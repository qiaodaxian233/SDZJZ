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

    public static void resetAll() { for (Stats s : MAP.values()) s.resetWindow(); }

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
