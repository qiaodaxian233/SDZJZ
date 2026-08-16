package com.sdzjz.debug;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * m177 性能尺子（审查报告 P0：先有基线再谈优化）。
 * 零协议零 NBT：全内存、只在服务器线程读写；核心每 tick 记一次耗时进 100 长度环形窗，
 * 路由/供料/链查/同步 逐点计数。/sdzjz profile 读，/sdzjz profile reset 清窗。
 * 成本：每核心每 tick 两次 nanoTime + 若干 long 自增，可忽略。
 */
public final class CoreProfiler {
    private CoreProfiler() {}

    public static final class Stats {
        public final String dim;
        public final BlockPos pos;
        final long[] win = new long[100];   // 最近 100 tick 耗时(ns)环形窗
        int idx, filled;
        public long routes, storageResolves, chainChecks;      // 窗口计数（reset 清）
        public long planCompiles;                               // m179 执行计划编译次数（稳态≈0）
        public long syncPackets, syncBytes;                     // 核心 NBT 同步
        public long endsPackets, endsEntries;                   // m89 端点/总线直发包
        public int nodes, edges;
        public boolean running;
        public long windowStartMs = System.currentTimeMillis(); // 计数窗起点
        public long lastSeenMs;                                  // 最近一次 tick（判活）

        Stats(String dim, BlockPos pos) { this.dim = dim; this.pos = pos; }

        public double avgMicros() {
            int n = filled; if (n == 0) return 0;
            long s = 0; for (int i = 0; i < n; i++) s += win[i];
            return s / 1000.0 / n;
        }
        public double maxMicros() {
            long m = 0; for (int i = 0; i < filled; i++) if (win[i] > m) m = win[i];
            return m / 1000.0;
        }
        public double perSec(long counter) {
            double sec = (System.currentTimeMillis() - windowStartMs) / 1000.0;
            return sec < 0.05 ? 0 : counter / sec;
        }
        void resetWindow() {
            routes = storageResolves = chainChecks = syncPackets = syncBytes = endsPackets = endsEntries = planCompiles = 0;
            windowStartMs = System.currentTimeMillis();
        }
    }

    private static final Map<String, Stats> MAP = new HashMap<>(); // 仅服务器线程访问

    public static Stats register(World world, BlockPos pos) {
        String key = world.getRegistryKey().getValue() + "@" + pos.asLong();
        return MAP.computeIfAbsent(key, k -> new Stats(world.getRegistryKey().getValue().toString(), pos.toImmutable()));
    }

    public static void record(Stats s, int nodes, int edges, boolean running, long nanos) {
        s.win[s.idx] = nanos;
        s.idx = (s.idx + 1) % s.win.length;
        if (s.filled < s.win.length) s.filled++;
        s.nodes = nodes; s.edges = edges; s.running = running;
        s.lastSeenMs = System.currentTimeMillis();
        phaseTick(nanos); // m321：阶段账分母（同一份 nanos，零新采样）
    }

    /** 最近 5 秒内 tick 过的核心（=已加载在跑；dim 传 null 取全维度）。 */
    public static List<Stats> active(String dim) {
        long now = System.currentTimeMillis();
        List<Stats> out = new ArrayList<>();
        for (Stats s : MAP.values())
            if (now - s.lastSeenMs < 5000 && (dim == null || s.dim.equals(dim))) out.add(s);
        out.sort((a, b) -> Double.compare(b.avgMicros(), a.avgMicros()));
        return out;
    }

    public static void resetAll() { for (Stats s : MAP.values()) s.resetWindow(); resetPhases(); }

    // ===== m321 阶段计时（m305 尾账在案的 profiler 细账缺口：优化前先量化谁在吃时间）=====
    // 四大阶段：每核每 tick 各 2 次 nanoTime（~8 次/核·tick）常开可忽略；
    // 六项细分：逐调用计时（chainWants 等热路径每 tick 数万调）——只在 PHASES=true 时累计
    // （/sdzjz profile phase on|off 手动开，bench 期间自动开），平时零额外开销。
    public static volatile boolean PHASES = false;
    public static final int PH_MAINT = 0, PH_TICKET = 1, PH_SUPPLY = 2, PH_PROD = 3, PH_N = 4;
    private static final String[] PH_NAME = {
            "维护/同步(端点扫描·快照·m89包·看门狗)", "区块票(强加载/续票/孤儿回收)",
            "逻辑供料(5t拍:仓视图扫描+链需求门控+精确支路)", "生产/转发/分发(全机器大循环)"};
    public static final int SUB_CHAIN = 0, SUB_ENDPOINT = 1, SUB_DISTRIBUTE = 2,
            SUB_DEPOSIT = 3, SUB_RESOLVE = 4, SUB_PLANNER = 5,
            // m354 机器类型桶（外部审计④轮③"让数据决定下一刀"）：97.1% 的生产粗桶按节点类型拆账，
            // 报告直接给出 µs/核·tick 前三大贡献；PHASES 闸内每节点一次 nanoTime，平时零成本。
            SUB_T_LOGIC = 6, SUB_T_CRAFT = 7, SUB_T_BREW = 8, SUB_T_ENCH = 9,
            SUB_T_TRADE = 10, SUB_T_DUP = 11, SUB_T_MACHINE = 12, SUB_T_MISC = 13, SUB_N = 14;
    private static final String[] SUB_NAME = {
            "chainWants 链需求判定", "scanStorageEndpoints 端点扫描", "distribute/Even 分发路由",
            "depositOrBuffer 入仓", "supplyFor/depositFor 存储解析", "CraftPlanner.plans 配方规划",
            "[类型账]逻辑节点(分/滤/垃圾/抽/开/感)", "[类型账]自动合成机", "[类型账]酿造塔", "[类型账]附魔工厂",
            "[类型账]交易节点", "[类型账]复制机", "[类型账]通用机器(含熔炉/作物族)", "[类型账]其他节点"};
    private static final long[] phNs = new long[PH_N];
    private static final long[] subNs = new long[SUB_N];
    private static final long[] subCalls = new long[SUB_N];
    private static long phTicks, phTotalNs;

    public static void phase(int i, long ns) { phNs[i] += ns; }

    /** m355 矩阵汇总口：窗口内均值 µs/核·tick（0=无样本）。 */
    public static double avgCoreTickUs() { return phTicks > 0 ? phTotalNs / 1e3 / phTicks : 0; }

    /** m355 矩阵汇总口：类型账前三（µs/核·tick 排序，"名 x.xµs" 连接；全零=空串）。 */
    public static String typeTop3() {
        long ct = Math.max(1, phTicks);
        int[] order = {SUB_T_LOGIC, SUB_T_CRAFT, SUB_T_BREW, SUB_T_ENCH, SUB_T_TRADE, SUB_T_DUP, SUB_T_MACHINE, SUB_T_MISC};
        java.util.Arrays.sort(order); // 稳定基序
        Integer[] idx = new Integer[order.length];
        for (int k = 0; k < order.length; k++) idx[k] = order[k];
        java.util.Arrays.sort(idx, (a, b) -> Long.compare(subNs[b], subNs[a]));
        StringBuilder o = new StringBuilder();
        for (int k = 0; k < 3; k++) {
            if (subNs[idx[k]] <= 0) break;
            if (o.length() > 0) o.append("  ");
            o.append(SUB_NAME[idx[k]].replace("[类型账]", "")).append(' ')
             .append(String.format("%.1fµs", subNs[idx[k]] / 1e3 / ct));
        }
        return o.toString();
    }
    public static void sub(int i, long ns) { subNs[i] += ns; subCalls[i]++; }
    static void phaseTick(long totalNs) { phTicks++; phTotalNs += totalNs; }

    public static void resetPhases() {
        java.util.Arrays.fill(phNs, 0);
        java.util.Arrays.fill(subNs, 0);
        java.util.Arrays.fill(subCalls, 0);
        phTicks = 0; phTotalNs = 0;
    }

    /** 阶段账单行（聊天与压测报告同源）。占比分母=全部核心 tick 总耗时。 */
    public static List<String> phaseReport() {
        List<String> out = new ArrayList<>();
        if (phTicks == 0) { out.add("阶段账为空（还没有核心 tick 过，或刚 reset）"); return out; }
        out.add(String.format("阶段计时：窗口 %d 核·tick，合计 %.1f ms，均 %.1f µs/核·tick",
                phTicks, phTotalNs / 1e6, phTotalNs / 1000.0 / phTicks));
        long sum = 0;
        for (int i = 0; i < PH_N; i++) {
            sum += phNs[i];
            out.add(String.format("  %-26s %8.1f µs/核·tick  %5.1f%%",
                    PH_NAME[i], phNs[i] / 1000.0 / phTicks, 100.0 * phNs[i] / Math.max(1, phTotalNs)));
        }
        out.add(String.format("  %-26s %8.1f µs/核·tick  %5.1f%%",
                "其他(计划编译/账尾/未列拍)", Math.max(0, phTotalNs - sum) / 1000.0 / phTicks,
                100.0 * Math.max(0, phTotalNs - sum) / Math.max(1, phTotalNs)));
        boolean any = false;
        for (long c : subCalls) if (c > 0) { any = true; break; }
        out.add("细分（PHASES=" + (PHASES ? "开" : "关") + "，关时不累计）：");
        if (!any) out.add("  （空——/sdzjz profile phase on 或跑 bench 后再看）");
        for (int i = 0; i < SUB_N; i++) {
            if (subCalls[i] == 0) continue;
            out.add(String.format("  %-32s 总 %7.1f ms · %,d 次 · 均 %,d ns · 占总 %4.1f%%",
                    SUB_NAME[i], subNs[i] / 1e6, subCalls[i], subNs[i] / Math.max(1, subCalls[i]),
                    100.0 * subNs[i] / Math.max(1, phTotalNs)));
        }
        return out;
    }

    /** NBT 编码字节数（计数流上真编码一遍，只在 syncToClient 时调用，量级同同步本身）。 */
    public static long nbtSize(NbtCompound nbt) {
        final long[] n = {0};
        try (DataOutputStream d = new DataOutputStream(new OutputStream() {
            @Override public void write(int b) { n[0]++; }
            @Override public void write(byte[] b, int off, int len) { n[0] += len; }
        })) {
            NbtIo.write(nbt, d);
        } catch (Exception ignored) {}
        return n[0];
    }
}
