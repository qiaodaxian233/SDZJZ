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
    private static long[] tickNs = new long[0];   // 墙钟间隔（健康脉搏≈50ms,含跑空等待）
    private static long[] busyNs = new long[0];   // m307 忙时=原版 tickTimes 当拍真值（负载口径）
    private static int tickCount;
    private static Path reportPath;
    // m308 黄灯占空比直测（m115 看门狗 45/40ms 滞回；占空比>0 时倍数判据=噪声）
    private static long pauseSamples, pauseHits; private static int pausePeak;

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
        pauseSamples = 0; pauseHits = 0; pausePeak = 0;
        tickNs = new long[Math.min(seconds * 20 + 40, 24_000)];
        busyNs = new long[tickNs.length];
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
                if (prevNano != 0 && tickCount < tickNs.length) {
                    // m307 忙时真值：原版把本拍耗时写在 tickTimes[ticks%100]，END_SERVER_TICK 时已写毕
                    busyNs[tickCount] = server.getTickTimes()[server.getTicks() % 100];
                    tickNs[tickCount++] = now - prevNano;
                }
                prevNano = now;
                if (tickCount % 20 == 0) { // m308 黄灯采样（100 站 getBlockEntity 每秒一轮，开销可忽略）
                    int paused = 0;
                    for (BlockPos s : SITES)
                        if (world.getBlockEntity(s) instanceof StructureCoreBlockEntity c && c.lagPausedNow()) paused++;
                    pauseSamples += SITES.size(); pauseHits += paused; pausePeak = Math.max(pausePeak, paused);
                }
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
            core.running = true;
            // m307 首票自举（首轮实测 20 核只跑 6 的真凶）：核心自持票在**它自己 tick 里**注册，
            // 而模拟距离外的新区块根本不给 BE tick 机会=鸡生蛋死锁。代发一张同通道票（force 按
            // 核心坐标记账幂等，核心 ≤20t 自注册接管；拆块 release 走原路，另有清场兜底双保险）。
            com.sdzjz.block.CoreChunkLoading.force(world, p, false);
        }
    }

    private static void cleanSite(BlockPos p) {
        if (world.getBlockEntity(p) instanceof StructureCoreBlockEntity core) core.benchClearNodes();
        world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);       // 节点已清空,dropAll 散落=零件
        world.setBlockState(p.east(), net.minecraft.block.Blocks.AIR.getDefaultState(), 3); // 存储账本是虚拟账,拆块零散落
        com.sdzjz.block.CoreChunkLoading.release(world, p, false); // m307 兜底：核心若始终没tick,
        // chunkForceActive=false 拆块走不到释放——补一发注销自举票（已释放时=孤儿声明清理,幂等无害）
    }

    private static double[] avgP95Max(long[] arr, int n) {
        long[] a = java.util.Arrays.copyOf(arr, n);
        java.util.Arrays.sort(a);
        double avg = 0; for (long v : a) avg += v;
        return new double[]{ n == 0 ? 0 : avg / n / 1e6,
                n == 0 ? 0 : a[(int) Math.min(n - 1, Math.floor(n * 0.95))] / 1e6,
                n == 0 ? 0 : a[n - 1] / 1e6 };
    }

    private static void writeReport(MinecraftServer server) throws Exception {
        double[] busy = avgP95Max(busyNs, tickCount); // m307 负载口径（原版 tickTimes 真值）
        double[] wall = avgP95Max(tickNs, tickCount); // 健康脉搏（≈50ms=服务器有余力在睡）

        Set<BlockPos> mine = new HashSet<>(SITES);
        List<CoreScheduler.Row> rows = CoreScheduler.statRows();
        List<CoreScheduler.Row> bench = rows.stream().filter(r -> mine.contains(r.pos))
                .sorted(java.util.Comparator.comparingLong(r -> r.granted)).toList();
        // m307 防哑账（首轮实测被 14/20 沉默核心骗出"达标"）：铺场清单逐一对上账,缺席点名
        Set<BlockPos> seen = new HashSet<>(); for (CoreScheduler.Row r : bench) seen.add(r.pos);
        List<BlockPos> silent = new ArrayList<>(); for (BlockPos s : SITES) if (!seen.contains(s)) silent.add(s);
        long otherGranted = rows.stream().filter(r -> !mine.contains(r.pos)).mapToLong(r -> r.granted).sum();
        long otherCores = rows.size() - bench.size();
        long benchGranted = bench.stream().mapToLong(r -> r.granted).sum();

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
          .append("  站距64格(跨区块=真实BE tick序)  上账核心=").append(bench.size()).append('/').append(cores).append("\n\n");
        sb.append(String.format("服务器忙时MSPT(原版tickTimes, %d样本): 均值 %.2f ms | P95 %.2f ms | 峰值 %.2f ms%n",
                tickCount, busy[0], busy[1], busy[2]));
        sb.append(String.format("墙钟tick间隔(健康脉搏,≈50ms=有余力在睡): 均值 %.2f ms | P95 %.2f ms | 峰值 %.2f ms%n",
                wall[0], wall[1], wall[2]));
        sb.append(String.format("调度器(仅压测核心): granted 最低=%d 中位=%d 最高=%d 合计=%d | 零吞吐=%d | 上拍消费=%d%n",
                min, med, max, benchGranted, zero, CoreScheduler.prevTickSpent()));
        if (otherCores > 0)
            sb.append(String.format("同服非压测核心 ×%d 同期消费 granted=%d（tick序竞争的真实对照,判据已剔除）%n", otherCores, otherGranted));
        double dutyPct = pauseSamples == 0 ? 0 : 100.0 * pauseHits / pauseSamples;
        sb.append(String.format("过载看门狗(m115,阈45/40ms滞回): 黄灯占空比 均 %.1f%% | 峰值同时暂停 %d/%d 核%n",
                dutyPct, pausePeak, SITES.size()));
        String verdict;
        double ratio = min > 0 ? (double) max / min : Double.NaN;
        if (!silent.isEmpty()) verdict = "不达标：" + silent.size() + " 个铺场核心从未上账（未tick/未申请）——数据不可用请贴报告";
        else if (zero > 0)     verdict = "不达标：存在零吞吐核心（防饥饿失效，请贴报告）";
        else if (dutyPct > 0)  verdict = String.format("倍数判据无效：过载看门狗介入(占空比%.1f%%)——%.1f×是黄灯占空比噪声非调度器序偏置；" +
                                       "防饥饿(零吞吐=0)仍有效。请降档使忙时P95<35ms重测倍数", dutyPct, ratio);
        else if (min > 0 && ratio <= 10.0) verdict = String.format("达标（评审③）：最高/最低=%.1f× ≤10×且无恒0", ratio);
        else if (min > 0)      verdict = String.format("偏斜超阈：最高/最低=%.1f× 超'几倍'口径——anti-starvation只保底不保比例，" +
                                       "要比例公平升方案②轮转相位（HANDOVER远期候选）", ratio);
        else                   verdict = "样本不足";
        sb.append("判据: ").append(verdict).append("\n");
        if (!silent.isEmpty()) {
            sb.append("未上账核心: ");
            for (int k = 0; k < Math.min(6, silent.size()); k++) sb.append(silent.get(k).toShortString()).append("; ");
            if (silent.size() > 6) sb.append("…共").append(silent.size()).append("个");
            sb.append('\n');
        }
        sb.append('\n').append("逐核明细(granted升序; tick耗时µs来自/sdzjz profile core同一环形窗):\n");
        String dim = world.getRegistryKey().getValue().toString();
        List<CoreProfiler.Stats> prof = CoreProfiler.active(dim);
        for (CoreScheduler.Row r : bench) {
            CoreProfiler.Stats s = null;
            for (CoreProfiler.Stats c : prof) if (c.pos.equals(r.pos)) { s = c; break; }
            sb.append(String.format("  %s granted=%d 记名=%d", r.pos.toShortString(), r.granted, r.zeroEvents));
            if (s != null) sb.append(String.format("  tick均%.0fµs 峰%.0fµs 编译%d", s.avgMicros(), s.maxMicros(), s.planCompiles));
            sb.append('\n');
        }

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
