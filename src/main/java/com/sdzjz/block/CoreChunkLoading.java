package com.sdzjz.block;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 结构核心强制加载（m133）：解决"人一走远区块卸载=机器全停、挂机白挂"的地基级问题。
 * 双轨设计：
 * - 核心自身区块 = 自定义**无期票**（m296）+ 自家 PersistentState 声明表 —— 与 /forceload 的
 *   FORCED 票是两条互不相干的通道：管理员对同一区块随便 forceload/解除，核心随便钉住/释放，
 *   谁也碰不到谁的旗（外部审计 P1 点名的"核心先钉、管理员后叠 /forceload、核心释放误伤"从根上消失，
 *   m268 的 EXTERNAL 运行时表与所有权近似整个退役）。
 *   无期票不落盘 → 声明表落 PersistentState（每维度一份，NbtLongArray），开服 ServerWorldEvents.LOAD
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
    private static final ChunkTicketType<ChunkPos> CORE =
            ChunkTicketType.create("sdzjz_core", Comparator.comparingLong(ChunkPos::toLong));

    /** 端点有期票：300t(15s) 过期，核心每 100t 续票。 */
    private static final ChunkTicketType<ChunkPos> ENDPOINT =
            ChunkTicketType.create("sdzjz_endpoint", Comparator.comparingLong(ChunkPos::toLong), 300);

    public static void clearAll() {
        FORCED.clear();
    }

    private static String dimId(ServerWorld w) {
        return w.getRegistryKey().getValue().toString();
    }

    /** m296 声明表：本 MOD 钉住的区块集合，每维度一份 PersistentState（NbtLongArray 落盘）。 */
    public static final class Claims extends PersistentState {
        final Set<Long> chunks = new HashSet<>();

        public static final PersistentState.Type<Claims> TYPE = new PersistentState.Type<>(
                Claims::new,
                (nbt, lookup) -> { // 待编译验证：Type 三参记录(构造/反序列化/DataFixTypes可null)，1.21.1 通行写法
                    Claims c = new Claims();
                    for (long l : nbt.getLongArray("chunks")) c.chunks.add(l);
                    return c;
                },
                null);

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
            long[] arr = new long[chunks.size()];
            int i = 0;
            for (long l : chunks) arr[i++] = l;
            nbt.putLongArray("chunks", arr);
            return nbt;
        }
    }

    private static Claims claims(ServerWorld w) {
        return w.getPersistentStateManager().getOrCreate(Claims.TYPE, "sdzjz_chunk_claims");
    }

    /** m296 开服/维度载入：逐声明重发无期票（ForcedChunkState 同款自举）。世界边界外坏声明拒发并剔除。 */
    public static void restoreClaims(ServerWorld w) {
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
    public static boolean force(ServerWorld w, BlockPos core, boolean priorOwned) {
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
            if (priorOwned && w.getForcedChunks().contains(cp.toLong()))
                w.setChunkForced(cp.x, cp.z, false); // 旧通道换轨（一次性；管理员叠旗需重跑 /forceload，见类头）
        }
        return true;
    }

    /** 注销；本区块无其他登记核心（或压根未登记=孤儿声明兜底）→ 撤票删声明。原版 forced 通道从此不碰。 */
    public static void release(ServerWorld w, BlockPos core, boolean owned) {
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

    /** 孤儿回收：现只承担**旧通道遗旗**清理（m268 时代 owned=true 但停机核心重启后发现区块仍 forced）。
     *  owned=false（含管理员 /forceload）绝不碰。新通道的孤儿声明由 release 的"未登记即撤"兜。 */
    public static void reclaimOrphan(ServerWorld w, BlockPos core, boolean owned) {
        if (!owned) return;
        ChunkPos cp = new ChunkPos(core);
        if (w.getForcedChunks().contains(cp.toLong())) w.setChunkForced(cp.x, cp.z, false);
    }

    /** 给端点区块续一张有期票（radius=1：本块可tick、邻块可访问）。
     *  m142 末端防线：世界边界外（区块 ±187.5万）的票直接拒发——上游任何坏数据（哨兵解码/
     *  存档损坏）走到这里也发不出毒票，radius 邻块回卷崩实体管理器的路从此焊死。 */
    public static void ticket(ServerWorld w, long chunkLong) {
        ChunkPos cp = new ChunkPos(chunkLong);
        if (Math.abs(cp.x) > 1_875_000 || Math.abs(cp.z) > 1_875_000) return;
        w.getChunkManager().addTicket(ENDPOINT, cp, 1, cp);
    }
}
