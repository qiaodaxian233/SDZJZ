package com.sdzjz.debug;

import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.machine.CoreScheduler;
import com.sdzjz.registry.ModBlocks;
import com.sdzjz.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * m306 一键压测（作者点名：一条命令跑完评审③矩阵一档，报告落游戏目录）。
 *
 * /sdzjz bench start [核数] [每核节点] [秒] [cap] → 玩家脚下附近高空自动铺场
 * （每站=结构核心+贴邻存储核心+N 台刷石机节点，核间距 64 格分散区块=真实 BE tick 序），
 * 满载跑够秒数（期间自测每 tick 真实耗时=END_SERVER_TICK 间隔 nanoTime 差），到点生成报告
 * `<游戏目录>/sdzjz_bench_<时间戳>.txt`（服务器均值/P95/峰值 ms、调度器判据行、逐核明细），
 * 然后**自动清场**（节点先清空不散落再拆块，绝不下 5 万件物品雨）、恢复被临时改动的预算配置。
 * /sdzjz bench stop 随时中止（跳过剩余压测直接出报告+清场）。
 *
 * 状态机全静态、IDLE 时 tick 零开销；SERVER_STOPPED 复位。铺场坐标写服务器日志留痕——
 * 万一压测中途停服，orphan 方块按日志坐标手清（边界立档，见 DEVLOG m306）。
 */
public final class BenchRunner {
    private BenchRunner() {}

    private enum Phase { IDLE, SPAWN, RUN, CLEANUP }

    private static Phase phase = Phase.IDLE;
    private static ServerWorld world;
    private static BlockPos origin;
    private static int cores, nodesPer, seconds;
    private static long capParam, savedCap;
    private static boolean capTouched;
    private static UUID starter;

    private static final List<BlockPos> SITES = new ArrayList<>();
    private static int spawnIdx, cleanupIdx;
    private static long runTicksLeft;
    private static long prevNano;
    private static long[] tickNs = new long[0];
    private static int tickCount;
    private static Path reportPath;

    /** Sdzjz 初始化时挂一次。 */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BenchRunner::tick);
    }

    /** SERVER_STOPPED：复位状态机（配置只改内存不落盘，重启自动回读磁盘值）。 */
    public static void reset() {
        phase = Phase.IDLE; world = null; SITES.clear(); tickNs = new long[0]; tickCount = 0;
        capTouched = false; reportPath = null;
    }

    public static String start(ServerWorld w, BlockPos at, UUID by, int nCores, int nNodes, int secs, long cap) {
        if (phase != Phase.IDLE) return "§c已有压测在跑（/sdzjz bench stop 可中止）";
        int nodeCap = SdzjzConfig.get().maxNodesPerCore;
        cores = Math.max(1, Math.min(200, nCores));
        nodesPer = Math.max(1, nodeCap > 0 ? Math.min(nNodes, nodeCap) : nNodes);
        seconds = Math.max(5, Math.min(1200, secs));
        capParam = cap;
        world = w;
        // 高空铺场：脚下 +60（躲开多数地形；山体内会替换方块、清场留空洞——建议空旷处执行，边界立档）
        origin = at.up(60);
        starter = by;
        SITES.clear();
        for (int i = 0; i < cores; i++)
            SITES.add(origin.add((i % 10) * 64, 0, (i / 10) * 64));
        Sdzjz_log("[sdzjz bench] 铺场清单（中途停服按此手清）: origin=" + origin.toShortString()
                + " cores=" + cores + " 间距64 每站=核心+东侧存储 " + SITES.get(0).toShortString()
                + " .. " + SITES.get(SITES.size() - 1).toShortString());
        spawnIdx = 0; cleanupIdx = 0; tickCount = 0; prevNano = 0;
        tickNs = new long[Math.min(seconds * 20 + 40, 24_000)];
        phase = Phase.SPAWN;
        return "§a[sdzjz] 压测铺场中：核心×" + cores + " × 节点" + nodesPer + "（刷石机满载）· " + seconds
                + "s · cap=" + (capParam > 0 ? capParam : "不动配置") + " —— 跑完报告落游戏目录并自动清场";
    }

    public static String stopNow() {
        if (phase == Phase.IDLE) return "§7[sdzjz] 没有在跑的压测";
        if (phase == Phase.RUN) { runTicksLeft = 1; return "§e[sdzjz] 收到，下一拍出报告并清场"; }
        if (phase == Phase.SPAWN) { phase = Phase.CLEANUP; return "§e[sdzjz] 铺场中止，转清场"; }
        return "§7[sdzjz] 正在清场中";
    }

    private static void tick(MinecraftServer server) {
        switch (phase) {
            case IDLE -> { }
            case SPAWN -> {
                for (int b = 0; b < 2 && spawnIdx < SITES.size(); b++, spawnIdx++) spawnSite(SITES.get(spawnIdx));
                if (spawnIdx >= SITES.size()) {
                    CoreProfiler.resetAll();
                    CoreScheduler.resetStats();
                    if (capParam > 0) { savedCap = SdzjzConfig.get().maxRecipesPerNetworkTick;
                        SdzjzConfig.get().maxRecipesPerNetworkTick = capParam; capTouched = true; }
                    runTicksLeft = (long) seconds * 20;
                    prevNano = 0;
                    phase = Phase.RUN;
                    msg(server, "§a[sdzjz] 铺场完成，压测开跑 " + seconds + "s（/sdzjz bench stop 可提前收）");
                }
            }
            case RUN -> {
                long now = System.nanoTime();
                if (prevNano != 0 && tickCount < tickNs.length) tickNs[tickCount++] = now - prevNano;
                prevNano = now;
                if (--runTicksLeft <= 0) {
                    try { writeReport(server); msg(server, "§a[sdzjz] 压测完成，报告：§f" + reportPath); }
                    catch (Exception e) { msg(server, "§c[sdzjz] 报告写盘失败：" + e); Sdzjz_log("[sdzjz bench] 报告写盘失败: " + e); }
                    if (capTouched) { SdzjzConfig.get().maxRecipesPerNetworkTick = savedCap; capTouched = false; }
                    phase = Phase.CLEANUP;
                }
            }
            case CLEANUP -> {
                for (int b = 0; b < 4 && cleanupIdx < SITES.size(); b++, cleanupIdx++) cleanSite(SITES.get(cleanupIdx));
                if (cleanupIdx >= SITES.size()) {
                    msg(server, "§a[sdzjz] 压测清场完毕（配置已复原，方块零残留）");
                    phase = Phase.IDLE; world = null; SITES.clear();
                }
            }
        }
    }

    private static void spawnSite(BlockPos p) {
        world.setBlockState(p, ModBlocks.STRUCTURE_CORE.getDefaultState(), 3);
        world.setBlockState(p.east(), ModBlocks.STORAGE_CORE.getDefaultState(), 3);
        if (world.getBlockEntity(p) instanceof StructureCoreBlockEntity core) {
            ItemStack pack = new ItemStack(ModItems.COBBLE_MAKER, nodesPer);
            while (!pack.isEmpty() && core.insertMachine(null, pack)) { /* insertMachine 每次吃 1 台并 decrement */ }
            core.running = true; // 开机（m133：开机即自持区块票，人不在照跑——正好把强加载链路一起压）
        }
    }

    private static void cleanSite(BlockPos p) {
        if (world.getBlockEntity(p) instanceof StructureCoreBlockEntity core) core.benchClearNodes();
        world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);       // 节点已清空,dropAll 散落=零件
        world.setBlockState(p.east(), net.minecraft.block.Blocks.AIR.getDefaultState(), 3); // 存储账本是虚拟账,拆块零散落
    }

    private static void writeReport(MinecraftServer server) throws Exception {
        long[] ns = java.util.Arrays.copyOf(tickNs, tickCount);
        java.util.Arrays.sort(ns);
        double avg = 0; for (long v : ns) avg += v; avg = ns.length == 0 ? 0 : avg / ns.length / 1e6;
        double p95 = ns.length == 0 ? 0 : ns[(int) Math.min(ns.length - 1, Math.floor(ns.length * 0.95))] / 1e6;
        double mx  = ns.length == 0 ? 0 : ns[ns.length - 1] / 1e6;

        Set<BlockPos> mine = new HashSet<>(SITES);
        List<CoreScheduler.Row> rows = CoreScheduler.statRows();
        List<CoreScheduler.Row> bench = rows.stream().filter(r -> mine.contains(r.pos))
                .sorted(java.util.Comparator.comparingLong(r -> r.granted)).toList();
        long min = bench.isEmpty() ? 0 : bench.get(0).granted;
        long max = bench.isEmpty() ? 0 : bench.get(bench.size() - 1).granted;
        long med = bench.isEmpty() ? 0 : bench.get(bench.size() / 2).granted;
        long zero = bench.stream().filter(r -> r.granted == 0).count();
        long capUsed = capParam > 0 ? capParam : SdzjzConfig.get().maxRecipesPerNetworkTick;

        StringBuilder sb = new StringBuilder();
        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        sb.append("《生电终结者》一键压测报告 ").append(ts).append('\n');
        sb.append("参数: cores=").append(cores).append(" nodes/core=").append(nodesPer)
          .append(" machine=cobble_maker(10t无输入满载) duration=").append(seconds).append("s cap=")
          .append(capParam > 0 ? String.valueOf(capParam) : ("未改动(现值" + capUsed + ")")).append('\n');
        sb.append("维度: ").append(world.getRegistryKey().getValue()).append("  origin=").append(origin.toShortString())
          .append("  站距64格(跨区块=真实BE tick序)\n\n");
        sb.append(String.format("服务器整tick(自测END_TICK间隔, %d样本): 均值 %.2f ms | P95 %.2f ms | 峰值 %.2f ms%n",
                ns.length, avg, p95, mx));
        sb.append(String.format("调度器(仅本次压测核心): granted 最低=%d 中位=%d 最高=%d | 零吞吐核心=%d | 上拍消费=%d%n",
                min, med, max, zero, CoreScheduler.prevTickSpent()));
        String verdict = zero > 0 ? "不达标：存在零吞吐核心（防饥饿失效，请贴报告）"
                : min > 0 ? String.format("达标判据（评审③）：最高/最低=%.1f×（几倍内且无恒0=达标）", (double) max / min)
                : "样本不足";
        sb.append("判据: ").append(verdict).append("\n\n");
        sb.append("逐核明细(granted升序; tick耗时µs来自/sdzjz profile core同一环形窗):\n");
        String dim = world.getRegistryKey().getValue().toString();
        List<CoreProfiler.Stats> prof = CoreProfiler.active(dim);
        for (CoreScheduler.Row r : bench) {
            CoreProfiler.Stats s = null;
            for (CoreProfiler.Stats c : prof) if (c.pos.equals(r.pos)) { s = c; break; }
            sb.append(String.format("  %s granted=%d 记名=%d", r.pos.toShortString(), r.granted, r.zeroEvents));
            if (s != null) sb.append(String.format("  tick均%.0fµs 峰%.0fµs 编译%d", s.avgMicros(), s.maxMicros(), s.planCompiles));
            sb.append('\n');
        }
        if (rows.size() > bench.size())
            sb.append("(另有 ").append(rows.size() - bench.size()).append(" 个非压测核心也在消费预算，已从判据剔除)\n");

        reportPath = server.getRunDirectory().resolve("sdzjz_bench_" + ts + ".txt");
        Files.write(reportPath, sb.toString().getBytes(StandardCharsets.UTF_8));
        Sdzjz_log("[sdzjz bench] 报告已写盘: " + reportPath);
    }

    private static void msg(MinecraftServer server, String s) {
        if (starter != null) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(starter);
            if (p != null) p.sendMessage(Text.literal(s), false);
        }
        Sdzjz_log(s.replaceAll("§.", ""));
    }

    private static void Sdzjz_log(String s) { com.sdzjz.Sdzjz.LOGGER.info(s); }
}
