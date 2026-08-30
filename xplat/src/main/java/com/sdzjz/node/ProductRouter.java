package com.sdzjz.node;

import net.minecraft.world.item.ItemStack;

/**
 * m495（真移植·B2 前置）：**产物分发两代共用一份**——主线 {@code distribute0} 的
 * 两轮垫底与缓存投喂逐句搬，**兜底那三行走世代口**。
 *
 * <p><b>为什么之前搬不了</b>：这段是 B2（tick 执行面下沉）里两代形状差最大的一处，
 * m473/m475 两次记档都说「执行面取舍不同，不是照抄能解决的」。真去对了才发现——
 * <b>不同的只有最后三行</b>：主线余量走 {@code depositFor → depositOrBuffer → addOutput}
 * （输出缓存 + 断网喷射），1.20.1 走 {@code depositTail}（落产出仓，拒收就原样回吐给调用方
 * 亮黄灯持料）。**循环体本身两代逐字相同**。把那三行收进 {@link Tail}，整段就能共用了。
 *
 * <p><b>这就是「统一世代取舍」的正确形状</b>：不是逼两代行为一致（主线有输出缓存、
 * 本世代没有，这是真实差异，硬统一等于给一代加它不该有的东西），而是<b>把差异收窄到一个口</b>，
 * 让相同的部分共用、不同的部分各实现各的——与前面十一个世代口同一手法。
 */
public final class ProductRouter {

    private ProductRouter() { }

    /** 缓存每种上限（两代同值，主线 BUF_CAP）。 */
    public static final long BUF_CAP = 200000L;

    /** 分发所需的宿主面。 */
    public interface Host {
        int nodeCount();
        ItemStack nodeStack(int i);
        /** 该节点的出线目标（主线 m355 数组化，本世代按需现算）。 */
        int[] outTargets(int from);
        /** 目标节点的在途缓存表（直接改）。 */
        java.util.Map<String, Long> nodeBuf(int i);
        /** m270 类型上限：该缓存还能不能收这个新类型。 */
        boolean bufTypeOk(java.util.Map<String, Long> buf, String id);
        /** 目标是否肯收（走 {@link RouteBrain#accepts}）。 */
        boolean accepts(Object level, int target, String id);
        /** 缓存变更后的落盘标记。 */
        void markChanged();
    }

    /**
     * 兜底世代口：喂完下游缓存后**剩下的怎么办**。这是两代唯一的真差异。
     * @return 仍未处理掉的数量（本世代据此回灌+亮黄灯；主线恒 0，因为它有输出缓存兜住）
     */
    public interface Tail {
        long deposit(Object level, int fromIndex, String id, long amt);
    }

    /**
     * 两轮垫底分发（主线 distribute0 原文）：第一轮跳过垃圾桶族，第二轮只喂它们——
     * **垃圾桶永远是最后去处，与连线先后无关**（不然玩家想要的东西会先被吞掉）。
     *
     * @return 未能送出的余量（已交给 {@link Tail} 处理过一轮）
     */
    public static long distribute(Host host, Tail tail, Object level, int fromIndex,
                                  boolean hasOut, String id, long amt) {
        if (amt <= 0) return 0L;
        if (hasOut) {
            int[] targets = host.outTargets(fromIndex);
            if (targets != null) {
                for (int pass = 0; pass < 2; pass++) {
                    for (int t : targets) {
                        if (amt <= 0) return 0L;
                        if (t < 0 || t >= host.nodeCount()) continue;
                        if ((pass == 0) == isTrashLike(host.nodeStack(t))) continue; // m150/m378 垫底族
                        if (!host.accepts(level, t, id)) continue;
                        java.util.Map<String, Long> m = host.nodeBuf(t);
                        if (!host.bufTypeOk(m, id)) continue; // m270 类型上限：跳过满型目标，余量走兜底
                        long cur = m.getOrDefault(id, 0L);
                        long put = Math.min(Math.max(0L, BUF_CAP - cur), amt);
                        if (put > 0) { m.put(id, cur + put); amt -= put; host.markChanged(); }
                    }
                }
            }
        }
        if (amt <= 0) return 0L;
        return tail.deposit(level, fromIndex, id, amt);
    }

    /** m150 垃圾桶 + m378 虚空处理器：两者都是「最后去处」，第一轮一律跳过。 */
    public static boolean isTrashLike(ItemStack st) {
        return NodeTags.isTrash(st)
                || NodeTags.defOf(st) == com.sdzjz.machine.Machines.VOID_PROCESSOR; // m472 身份口（原文是 instanceof VoidProcessorItem）
    }
}
