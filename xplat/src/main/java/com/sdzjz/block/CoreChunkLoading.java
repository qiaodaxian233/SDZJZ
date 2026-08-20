package com.sdzjz.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 结构核心强制加载（m133）：解决"人一走远区块卸载=机器全停、挂机白挂"的地基级问题。
 * 双轨设计：
 * - 核心自身区块 = 自定义**无期票**（m296）+ 自家 SavedData 声明表 —— 与 /forceload 的
 *   FORCED 票是两条互不相干的通道：管理员对同一区块随便 forceload/解除，核心随便钉住/释放，
 *   谁也碰不到谁的旗（外部审计 P1 点名的"核心先钉、管理员后叠 /forceload、核心释放误伤"从根上消失，
 *   m268 的 EXTERNAL 运行时表与所有权近似整个退役）。
 *   无期票不落盘 → 声明表落 SavedData（每维度一份，NbtLongArray），开服 ServerWorldEvents.LOAD
 *   逐声明重发票——**复刻原版 ForcedChunkState 的自举戏法**：重启后区块开局即加载，核心 tick 苏醒
 *   后 ≤20t 重登记引用计数，"有期票不落盘=没人续票=死锁"的老命门由声明表补上。
 *   radius=2：目标区块 31 级（实体照 tick，与 /forceload 完全同级——刷怪塔/漏斗矿车零回退），
 *   邻环 32/33 级同原版 forced 的传播形状。
 * - 存储端点区块 = 自定义**有期票**（expiry 自动过期）——核心每 100t 续票；核心停机/被拆/
 *   本体区块意外卸载，票 15 秒内自动过期、端点区块自然卸载，零清理代码零泄漏。
 *
 * FORCED 引用计数（运行时）：同区块多台核心共用一张票+一条声明，登记表转瞬即逝（SERVER_STOPPED
 * 清空，运行核心 ≤20t 重登记）；最后一台注销才撤票删声明。孤儿声明（核心没走 release 就消失，
 * 如区块被外部工具整块删除）与旧版孤儿 forced 同律：同区块任一核心 release 时"未登记即撤"兜底。
 *
 * 旧档迁移（m268 → m296）：核心 NBT 里的 chunkOwned=true 意味着旧版曾用原版 forced 通道钉过此区块。
 * force() 首次登记时若 priorOwned 且区块此刻仍 forced，则撤一次原版旗（换轨）。一次性注意：
 * 若管理员恰好也对该区块叠过 /forceload，此次换轨会连带撤掉——管理员重跑一次 /forceload 即恢复，
 * 此后两通道永久互不干涉（旧版这是永久性缺陷，现在只是迁移瞬间的一次性近似）。
 * reclaimOrphan 同理只在 owned 时清旧通道遗旗。force 的返回值恒 true（所有权恒在自家通道），
 * 保留布尔签名让核心 NBT 的 chunkOwned 继续承担"旧通道迁移标记"职责，调用方零改动。
 */
public final class CoreChunkLoading {
    private CoreChunkLoading() {}

    /** 维度id → (区块long → 登记的核心方块坐标集合)。 */
    private static final Map<String, Map<Long, Set<Long>>> FORCED = new HashMap<>();

    /** m296 核心无期票（method_14291 二参 create=不过期）；radius=2 → 目标 31 级与 /forceload 同级。 */
    private static final TicketType<ChunkPos> CORE =
            TicketType.create("sdzjz_core", Comparator.comparingLong(ChunkPos::toLong));

    /** 端点有期票：300t(15s) 过期，核心每 100t 续票。 */
    private static final TicketType<ChunkPos> ENDPOINT =
            TicketType.create("sdzjz_endpoint", Comparator.comparingLong(ChunkPos::toLong), 300);

    public static void clearAll() {
        FORCED.clear();
        STRIKES.clear();     // m347 核销击数随停服清（跨存档幽灵防线同款）
        FIRST_SEEN.clear();  // m347 宽限锚同清（下个存档重新计宽限）
    }

    private static String dimId(ServerLevel w) {
        return w.getRegistryKey().getValue().toString();
    }

    /** m296 声明表：本 MOD 钉住的区块集合，每维度一份 SavedData（NbtLongArray 落盘）。 */
    public static final class Claims extends SavedData {
        final Set<Long> chunks = new HashSet<>();

        public static final SavedData.Type<Claims> TYPE = new SavedData.Type<>(
                Claims::new,
                (nbt, lookup) -> { // 待编译验证：Type 三参记录(构造/反序列化/DataFixTypes可null)，1.21.1 通行写法
                    Claims c = new Claims();
                    for (long l : nbt.getLongArray("chunks")) c.chunks.add(l);
                    return c;
                },
                null);

        @Override
        public CompoundTag writeNbt(CompoundTag nbt, net.minecraft.core.HolderLookup.WrapperLookup lookup) {
            long[] arr = new long[chunks.size()];
            int i = 0;
            for (long l : chunks) arr[i++] = l;
            nbt.putLongArray("chunks", arr);
            return nbt;
        }
    }

    private static Claims claims(ServerLevel w) {
        return w.getPersistentStateManager().getOrCreate(Claims.TYPE, "sdzjz_chunk_claims");
    }

    /** m296 开服/维度载入：逐声明重发无期票（ForcedChunkState 同款自举）。世界边界外坏声明拒发并剔除。 */
    public static void restoreClaims(ServerLevel w) {
        Claims c = claims(w);
        java.util.Iterator<Long> it = c.chunks.iterator();
        while (it.hasNext()) {
            ChunkPos cp = new ChunkPos(it.next());
            if (Math.abs(cp.x) > 1_875_000 || Math.abs(cp.z) > 1_875_000) { it.remove(); c.markDirty(); continue; }
            w.getChunkManager().addTicket(CORE, cp, 2, cp);
        }
    }

    /** 登记并钉住核心自身区块（重复登记幂等）。返回恒 true（所有权恒在自家通道），签名兼容 m268 调用方。
     *  priorOwned=旧档迁移标记：曾用原版 forced 通道 → 首登记时撤一次旧旗换轨（见类头"旧档迁移"）。 */
    public static boolean force(ServerLevel w, BlockPos core, boolean priorOwned) {
        ChunkPos cp = new ChunkPos(core);
        if (Math.abs(cp.x) > 1_875_000 || Math.abs(cp.z) > 1_875_000) return true; // m142 毒票末端防线同款
        String dim = dimId(w);
        Set<Long> owners = FORCED.computeIfAbsent(dim, k -> new HashMap<>())
                .computeIfAbsent(cp.toLong(), k -> new HashSet<>());
        boolean first = owners.isEmpty();
        owners.add(core.asLong());
        if (first) {
            w.getChunkManager().addTicket(CORE, cp, 2, cp); // 重复 addTicket 幂等，first 只为少打点
            Claims c = claims(w);
            if (c.chunks.add(cp.toLong())) c.markDirty();
            if (priorOwned && w.getForcedChunks().contains(cp.toLong())) {
                w.setChunkForced(cp.x, cp.z, false); // 旧通道换轨（一次性；管理员叠旗需重跑 /forceload，见类头）
                com.sdzjz.Sdzjz.LOGGER.warn("[生电终结者] 强加载换轨：已撤下 {} 区块({}, {}) 的旧版 forced 旗"
                        + "（核心改用自有票据）。若管理员曾对该区块执行过 /forceload，请重新执行一次以恢复。",
                        dim, cp.x, cp.z);
            }
        }
        return true;
    }

    /** 注销；本区块无其他登记核心（或压根未登记=孤儿声明兜底）→ 撤票删声明。原版 forced 通道从此不碰。 */
    public static void release(ServerLevel w, BlockPos core, boolean owned) {
        ChunkPos cp = new ChunkPos(core);
        String dim = dimId(w);
        Map<Long, Set<Long>> dimMap = FORCED.get(dim);
        Set<Long> owners = dimMap == null ? null : dimMap.get(cp.toLong());
        if (owners != null) {
            owners.remove(core.asLong());
            if (!owners.isEmpty()) return; // 同区块还有别的运行核心，保持钉住
            dimMap.remove(cp.toLong());
        }
        w.getChunkManager().removeTicket(CORE, cp, 2, cp); // 无票时 remove 无害
        Claims c = claims(w);
        if (c.chunks.remove(cp.toLong())) c.markDirty();
    }

    // ===== m347 孤儿声明渐进核销（外部审计销账）=====
    // 病根：restoreClaims 开服照声明表逐块重发无期票，**不验核心还在不在**。核心若在区块未加载态下
    // 消失（存档回滚/世界编辑工具/落盘时序撕裂），声明成孤儿——区块被自家票永久钉死，release 的
    // "未登记即撤"兜底永远等不到调用（没有核心去调它）。
    // 修法：每维度每 200t 扫一遍声明表，与运行时 FORCED 引用计数对表——声明在而运行时零登记
    // = 记一击；**连续三击**（≥30s）才核销（撤票+删声明+出声）。运行时有登记即销击。
    // 时序安全账：重启后 chunkForceOn 瞬态=false，活核心的区块被恢复票钉着必然 tick，≤20t 边沿
    // 重登记进 FORCED（m296 既有戏法）；再叠开服 600t 宽限起扫 + 三击迟滞 ≥30s，任何慢热路径
    // 都追不上误杀。全程 O(声明数) 查表，声明数=运行核心区块数，量级可忽略（"渐进"即在此）。
    private static final Map<String, Map<Long, Integer>> STRIKES = new HashMap<>(); // dim → chunkLong → 连续缺席击数
    private static final Map<String, Long> FIRST_SEEN = new HashMap<>();            // dim → 首见世界时刻（宽限锚）
    private static final int STRIKE_OUT = 3;      // 三击核销
    private static final int SWEEP_PERIOD = 200;  // 每 10s 一扫
    private static final int GRACE_TICKS = 600;   // 开服 30s 宽限（≥20t 重登记窗的 30 倍）

    /** 每维度 tick 驱动（Sdzjz 注册）：宽限+节拍由此把门，真活在 sweepNow。开关关=零动作。 */
    public static void reconcileTick(ServerLevel w) {
        if (!com.sdzjz.config.SdzjzConfig.get().chunkClaimReconcile) return;
        String dim = dimId(w);
        long now = w.getTime();
        Long first = FIRST_SEEN.get(dim);
        if (first == null) { FIRST_SEEN.put(dim, now); return; }
        if (now - first < GRACE_TICKS || Math.floorMod(now, SWEEP_PERIOD) != 0) return;
        sweepNow(w);
    }

    /** 单趟对表核销（GameTest 直驱口，绕过宽限/节拍）。 */
    public static void sweepNow(ServerLevel w) {
        String dim = dimId(w);
        Claims c = claims(w);
        Map<Long, Integer> strikes = STRIKES.computeIfAbsent(dim, k -> new HashMap<>());
        Map<Long, Set<Long>> dimMap = FORCED.get(dim);
        java.util.Iterator<Long> it = c.chunks.iterator();
        while (it.hasNext()) {
            long cl = it.next();
            Set<Long> owners = dimMap == null ? null : dimMap.get(cl);
            if (owners != null && !owners.isEmpty()) { strikes.remove(cl); continue; } // 运行时有主=活声明
            int n = strikes.merge(cl, 1, Integer::sum);
            if (n < STRIKE_OUT) continue;
            ChunkPos cp = new ChunkPos(cl);
            w.getChunkManager().removeTicket(CORE, cp, 2, cp);
            it.remove();
            c.markDirty();
            strikes.remove(cl);
            com.sdzjz.Sdzjz.LOGGER.warn("[生电终结者] 孤儿强加载声明核销：{} 区块({}, {}) 连续 {} 轮无核心登记，"
                    + "已撤票删声明（核心消失于区块未加载态的遗留；误销自愈=核心仍在则下次开机重新登记）。",
                    dim, cp.x, cp.z, STRIKE_OUT);
        }
        strikes.keySet().retainAll(c.chunks); // 声明已由 release 正常撤走的陈击数顺手保洁
    }

    /** GameTest 观测口：本维度声明数。 */
    public static int claimCount(ServerLevel w) {
        return claims(w).chunks.size();
    }

    /** GameTest 注入口：只抹掉指定区块的**运行时**登记（声明保留）=精确模拟"核心消失于区块
     *  未加载态"的孤儿态，不像 clearAll 会殃及同服其他用例的运行时账。 */
    public static void debugForgetRuntime(ServerLevel w, long chunkLong) {
        Map<Long, Set<Long>> dimMap = FORCED.get(dimId(w));
        if (dimMap != null) dimMap.remove(chunkLong);
    }

    /** 孤儿回收：现只承担**旧通道遗旗**清理（m268 时代 owned=true 但停机核心重启后发现区块仍 forced）。
     *  owned=false（含管理员 /forceload）绝不碰。新通道的孤儿声明由 release 的"未登记即撤"兜。 */
    public static void reclaimOrphan(ServerLevel w, BlockPos core, boolean owned) {
        if (!owned) return;
        ChunkPos cp = new ChunkPos(core);
        if (w.getForcedChunks().contains(cp.toLong())) w.setChunkForced(cp.x, cp.z, false);
    }

    /** 给端点区块续一张有期票（radius=1：本块可tick、邻块可访问）。
     *  m142 末端防线：世界边界外（区块 ±187.5万）的票直接拒发——上游任何坏数据（哨兵解码/
     *  存档损坏）走到这里也发不出毒票，radius 邻块回卷崩实体管理器的路从此焊死。 */
    public static void ticket(ServerLevel w, long chunkLong) {
        ChunkPos cp = new ChunkPos(chunkLong);
        if (Math.abs(cp.x) > 1_875_000 || Math.abs(cp.z) > 1_875_000) return;
        w.getChunkManager().addTicket(ENDPOINT, cp, 1, cp);
    }
}
