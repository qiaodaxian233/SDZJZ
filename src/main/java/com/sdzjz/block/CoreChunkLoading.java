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
 * 设计留痕：玩家手动 /forceload 的区块若恰与被拆核心同区块，会被顺手解除——极边角，登记不处理。
 */
public final class CoreChunkLoading {
    private CoreChunkLoading() {}

    /** 维度id → (区块long → 登记的核心方块坐标集合)。 */
    private static final Map<String, Map<Long, Set<Long>>> FORCED = new HashMap<>();

    /** 端点有期票：300t(15s) 过期，核心每 100t 续票。 */
    private static final ChunkTicketType<ChunkPos> ENDPOINT =
            ChunkTicketType.create("sdzjz_endpoint", Comparator.comparingLong(ChunkPos::toLong), 300);

    public static void clearAll() {
        FORCED.clear();
    }

    private static String dimId(ServerWorld w) {
        return w.getRegistryKey().getValue().toString();
    }

    /** 登记并钉住核心自身区块（重复登记幂等）。 */
    public static void force(ServerWorld w, BlockPos core) {
        ChunkPos cp = new ChunkPos(core);
        Set<Long> owners = FORCED.computeIfAbsent(dimId(w), k -> new HashMap<>())
                .computeIfAbsent(cp.toLong(), k -> new HashSet<>());
        boolean first = owners.isEmpty();
        owners.add(core.asLong());
        if (first) w.setChunkForced(cp.x, cp.z, true); // 已 forced 时重复置 true 无害
    }

    /** 注销；本区块无其他登记核心（或压根未登记=重启后孤儿）→ 解除 forced。 */
    public static void release(ServerWorld w, BlockPos core) {
        ChunkPos cp = new ChunkPos(core);
        Map<Long, Set<Long>> dim = FORCED.get(dimId(w));
        Set<Long> owners = dim == null ? null : dim.get(cp.toLong());
        if (owners != null) {
            owners.remove(core.asLong());
            if (!owners.isEmpty()) return; // 同区块还有别的运行核心，保持钉住
            dim.remove(cp.toLong());
        }
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
