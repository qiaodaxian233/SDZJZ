package com.sdzjz.block;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 结构核心强制加载（m133）：解决"人一走远区块卸载=机器全停、挂机白挂"的地基级问题。
 * 双轨设计：
 * - 自身区块 = 原版 FORCED 票（/forceload 同款，ForcedChunkState 持久化）——服务器重启后
 *   该区块开局即加载，核心 tick 得以自举恢复（这是有期票做不到的：有期票不落盘，
 *   重启后没人续票、区块永不加载、核心永不苏醒，死锁）。
 * - 存储端点区块 = 自定义**有期票**（expiry 自动过期）——核心每 100t 续票；核心停机/被拆/
 *   本体区块意外卸载，票 15 秒内自动过期、端点区块自然卸载，零清理代码零泄漏。
 *   radius=1：目标区块 32 级（方块实体可 tick，存储核心自身 ticker 照常跑）、邻块 33 级（可访问）。
 *
 * FORCED 引用计数：同区块多台核心共用一个 forced 标记，登记表转瞬即逝（SERVER_STOPPED 清空，
 * 运行中的核心 ≤20t 维护里重新登记）。release 对未登记区块直接解除——重启后孤儿 forced 区块
 * （比如停机状态落盘前没来得及解除）由此兜底；同区块另一台运行核心会在下个维护拍重新登记加回，
 * 该时刻区块必然已加载（正在执行 release 的就是本区块里的 BE），无卸载窗口。
 *
 * m268 强加载所有权（外部审计）：force 前先查 getForcedChunks——**若该区块在本 MOD 动手前
 * 就已 forced（管理员 /forceload 或别的 MOD），登记为「外部所有」，本 MOD 永不解除它**。
 * force 返回「本 MOD 是否拥有该区块 forced 所有权」的布尔，由核心持久化进**自己的 NBT**
 * （chunkOwned，零新 API、随核心存档落盘）；release/reclaimOrphan 收核心传回的这个布尔，
 * **只解除本 MOD 名下的区块**。重启后运行时 EXTERNAL 表虽空，核心的 chunkOwned 仍在，
 * 故「管理员先 /forceload、核心后进同区块」以及重启后的孤儿回收都不会误伤管理员的强加载。
 * 管理员在核心已钉住区块上再叠加 /forceload 的罕见次生情况，因核心 chunkOwned=true 解除时会
 * 一并撤，属可接受近似（管理员再执行一次 /forceload 即恢复）。
 */
public final class CoreChunkLoading {
    private CoreChunkLoading() {}

    /** 维度id → (区块long → 登记的核心方块坐标集合)。 */
    private static final Map<String, Map<Long, Set<Long>>> FORCED = new HashMap<>();

    /** m268 维度id → 本 MOD 动手前就已 forced 的区块集合（外部所有，永不由本 MOD 解除）。 */
    private static final Map<String, Set<Long>> EXTERNAL = new HashMap<>();

    /** 端点有期票：300t(15s) 过期，核心每 100t 续票。 */
    private static final ChunkTicketType<ChunkPos> ENDPOINT =
            ChunkTicketType.create("sdzjz_endpoint", Comparator.comparingLong(ChunkPos::toLong), 300);

    public static void clearAll() {
        FORCED.clear();
        EXTERNAL.clear();
    }

    private static String dimId(ServerWorld w) {
        return w.getRegistryKey().getValue().toString();
    }

    /** m268 该区块此刻是否已被（任何来源）forced。 */
    private static boolean isForcedNow(ServerWorld w, ChunkPos cp) {
        return w.getForcedChunks().contains(cp.toLong());
    }

    /** 登记并钉住核心自身区块（重复登记幂等）。
     *  m268 priorOwned=核心持久化的既有所有权（重启后运行时表虽空但它仍在）——若本核心此前就
     *  拥有该区块所有权，即便此刻 getForcedChunks 仍显示 forced（正是它自己重启前钉的），也保持
     *  拥有、不误判为外部。返回本 MOD 是否拥有该区块 forced 所有权，供核心持久化。 */
    public static boolean force(ServerWorld w, BlockPos core, boolean priorOwned) {
        ChunkPos cp = new ChunkPos(core);
        String dim = dimId(w);
        Set<Long> owners = FORCED.computeIfAbsent(dim, k -> new HashMap<>())
                .computeIfAbsent(cp.toLong(), k -> new HashSet<>());
        boolean first = owners.isEmpty();
        // 首次登记且区块已 forced 且本核心此前不拥有=外部所有（管理员 /forceload 或别的 MOD）
        if (first && isForcedNow(w, cp) && !priorOwned)
            EXTERNAL.computeIfAbsent(dim, k -> new HashSet<>()).add(cp.toLong());
        owners.add(core.asLong());
        boolean externallyOwned = EXTERNAL.getOrDefault(dim, Set.of()).contains(cp.toLong());
        if (first) w.setChunkForced(cp.x, cp.z, true); // 已 forced 时重复置 true 无害
        return !externallyOwned;
    }

    /** 注销；本区块无其他登记核心（或压根未登记=重启后孤儿）→ 解除 forced。
     *  m268 owned=调用方持久化的所有权（本 MOD 名下才允许解除）；externalRuntime 兜运行时的 EXTERNAL 记录。 */
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
        Set<Long> ext = EXTERNAL.get(dim);
        boolean externalRuntime = ext != null && ext.remove(cp.toLong());
        if (externalRuntime || !owned) return; // 外部所有（运行时或持久标记）→ 只清登记不动 forced
        w.setChunkForced(cp.x, cp.z, false);
    }

    /** 孤儿回收（重启后登记表空但 forced 仍在）：owned=核心持久化的所有权，仅本 MOD 名下才解除。 */
    public static void reclaimOrphan(ServerWorld w, BlockPos core, boolean owned) {
        if (!owned) return; // 不是本 MOD 名下（管理员 /forceload）=绝不碰
        ChunkPos cp = new ChunkPos(core);
        w.setChunkForced(cp.x, cp.z, false);
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
