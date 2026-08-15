package com.sdzjz.debug;

/**
 * m351 GC/分配账（外部审计③轮"大规模 GC 压测"收官件）：测窗两端各取一张快照，差值即窗内账。
 *
 * 口径三条（报告里同文注明，防误读）：
 * - GC 次数/停顿=全 JVM 合计（ManagementFactory 全收集器求和）——单机集成服连客户端渲染线程的
 *   分配触发的 GC 也算在内，服务器专用实例才是纯服务端账；
 * - 分配字节=仅当前线程（快照都在服务器线程上取，故=服务器线程账）——走
 *   com.sun.management.ThreadMXBean（HotSpot 默认导出可用），非 HotSpot 优雅降级只留 GC 账；
 * - 两张快照必须同线程取（threadId 相同才可比），BenchRunner 两端都在 END_SERVER_TICK 里=天然同线程。
 */
public final class GcAccount {
    public final long gcCount;     // 全 JVM GC 次数累计
    public final long gcMs;        // 全 JVM GC 停顿毫秒累计
    public final long allocBytes;  // 当前线程分配字节累计（allocOk=false 时无意义）
    public final long nanoTime;    // 快照时刻（算测窗时长用）
    public final boolean allocOk;  // 分配账是否可用（HotSpot 支持且已启用）

    private GcAccount(long gcCount, long gcMs, long allocBytes, boolean allocOk) {
        this.gcCount = gcCount;
        this.gcMs = gcMs;
        this.allocBytes = allocBytes;
        this.allocOk = allocOk;
        this.nanoTime = System.nanoTime();
    }

    /** 取一张快照（在要记账的线程上调）。收集器计数 -1=不可用按 0 记，账只会少不会虚。 */
    public static GcAccount snap() {
        long c = 0, m = 0;
        for (var b : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            long cc = b.getCollectionCount();
            if (cc > 0) c += cc;
            long mm = b.getCollectionTime();
            if (mm > 0) m += mm;
        }
        long a = -1;
        boolean ok = false;
        var tm = java.lang.management.ManagementFactory.getThreadMXBean();
        if (tm instanceof com.sun.management.ThreadMXBean stm && stm.isThreadAllocatedMemorySupported()) {
            try {
                if (!stm.isThreadAllocatedMemoryEnabled()) stm.setThreadAllocatedMemoryEnabled(true);
                a = stm.getThreadAllocatedBytes(Thread.currentThread().threadId());
                ok = a >= 0;
            } catch (UnsupportedOperationException | SecurityException e) {
                ok = false; // 精简 JVM/安全管理器拒绝：降级只留 GC 账
            }
        }
        return new GcAccount(c, m, a, ok);
    }
}
