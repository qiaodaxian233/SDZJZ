package com.sdzjz.debug;

import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.machine.CoreScheduler;
import com.sdzjz.registry.ModBlocks;
import com.sdzjz.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

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

    private enum Phase { IDLE, SPAWN, WIRE, RUN, CLEANUP }
    /** m359 工况（作者拍板 A：证明 m349/m350/chainWants 在真实合成网络里兑现，不是只把冷路径优化得漂亮）。
     *  IDLE=零节点纯核心框架基线；COBBLE=回归基准；CRAFT_FED=仓→过滤→合成机→回仓 真产线。 */
    enum Workload { IDLE, COBBLE, CRAFT_FED, CRAFT_CHAIN, MIXED }
    private static Workload workload = Workload.COBBLE;
    private static Workload[] siteWl = new Workload[0]; // m360 逐站工况（MIXED=按站序 25%×4 轮布）
    private static Workload wlOf(int idx) { // MIXED 轮布序：idle→cobble→fed→chain
        if (workload != Workload.MIXED) return workload;
        return switch (idx % 4) { case 0 -> Workload.IDLE; case 1 -> Workload.COBBLE;
            case 2 -> Workload.CRAFT_FED; default -> Workload.CRAFT_CHAIN; };
    }
    /** m360 深链单元：仓→F→F→C1(原木→木板)→F→F→C2(木板→木棍)→F→F→C3(木棍→梯子)→仓，9 节点/链。
     *  双过滤前置=chainWants 真递归（深度 2-3）；三级真配方=中间产物全靠节点边流转不回仓。 */
    private static final int CHAIN_UNIT = 9;
    private static int wireIdx, wireDelay; // m359 装配相位：toggleStorageEdge 有 known 闸（端点须先被扫入），铺完等首扫再连线
    private static int activeCrafters;      // m359 per-crafter 出账分母

    private static Phase phase = Phase.IDLE;
    private static ServerLevel world;
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
    private static com.sdzjz.debug.GcAccount gcStart; // m351 测窗起点 GC/分配快照
    // m355 三档矩阵连跑（外部审计④轮③：真测 100/300/500，让数据决定下一刀）——
    // 档序队列+每档关键行捕获+档间冷却（清场后 GC/MSPT 回稳再开下一档，避免上一档余温污染）。
    private static java.util.ArrayDeque<int[]> matrixQueue;
    private static java.util.List<String> matrixRows;
    private static int matrixCooldown;
    private static ServerLevel matrixWorld; private static BlockPos matrixAt; private static UUID matrixBy;
    private static int matrixSecs; private static long matrixCap;
    private static Workload matrixWl = Workload.MIXED; // m360 矩阵默认混布（作者拍板 A：25%×4）
    // m308 黄灯占空比直测（m115 看门狗 45/40ms 滞回；占空比>0 时倍数判据=噪声）
    private static long pauseSamples, pauseHits; private static int pausePeak;

    /** Sdzjz 初始化时挂一次。 */
    public static void init() {
        com.sdzjz.loader.Hooks.onServerTickEnd(BenchRunner::tick); // m405 平台口
    }

    /** SERVER_STOPPED：复位状态机（配置只改内存不落盘，重启自动回读磁盘值）。 */
    public static void reset() {
        phase = Phase.IDLE; world = null; SITES.clear(); tickNs = new long[0]; tickCount = 0;
        capTouched = false; reportPath = null; gcStart = null;
        matrixQueue = null; matrixRows = null; matrixCooldown = 0; matrixWorld = null; // m355
        workload = Workload.COBBLE; wireIdx = 0; wireDelay = 0; activeCrafters = 0; // m359
    }

    public static String start(ServerLevel w, BlockPos at, UUID by, int nCores, int nNodes, int secs, long cap) {
        return start(w, at, by, nCores, nNodes, secs, cap, Workload.COBBLE);
    }

    /** m359 工况版入口。 */
    public static String start(ServerLevel w, BlockPos at, UUID by, int nCores, int nNodes, int secs, long cap, Workload wl) {
        if (phase != Phase.IDLE) return "§c已有压测在跑（/sdzjz bench stop 可中止）";
        workload = wl;
        int nodeCap = SdzjzConfig.get().maxNodesPerCore;
        cores = Math.max(1, Math.min(500, nCores)); // m355 矩阵最高档 500（审计④轮③点名），10×50 站阵仍 64 格距
        nodesPer = Math.max(1, nodeCap > 0 ? Math.min(nNodes, nodeCap) : nNodes);
        seconds = Math.max(5, Math.min(1200, secs));
        capParam = cap;
        world = w;
        // 高空铺场：脚下 +60（躲开多数地形；山体内会替换方块、清场留空洞——建议空旷处执行，边界立档）
        origin = at.above(60);
        starter = by;
        SITES.clear();
        for (int i = 0; i < cores; i++)
            SITES.add(origin.offset((i % 10) * 64, 0, (i / 10) * 64));
        Sdzjz_log("[sdzjz bench] 铺场清单（中途停服按此手清）: origin=" + origin.toShortString()
                + " cores=" + cores + " 间距64 每站=核心+东侧存储 " + SITES.get(0).toShortString()
                + " .. " + SITES.get(SITES.size() - 1).toShortString());
        spawnIdx = 0; cleanupIdx = 0; tickCount = 0; prevNano = 0;
        wireIdx = 0; wireDelay = 5; // m359 铺完等 5t：各站核心首扫哨兵入端点表后才连线（known 闸）
        siteWl = new Workload[cores]; // m360 逐站工况定型
        activeCrafters = 0;
        for (int i = 0; i < cores; i++) {
            siteWl[i] = wlOf(i);
            activeCrafters += switch (siteWl[i]) {
                case CRAFT_FED -> nodesPer / 2;
                case CRAFT_CHAIN -> (nodesPer / CHAIN_UNIT) * 3;
                default -> 0; };
        }
        pauseSamples = 0; pauseHits = 0; pausePeak = 0;
        tickNs = new long[Math.min(seconds * 20 + 40, 24_000)];
        busyNs = new long[tickNs.length];
        phase = Phase.SPAWN;
        return "§a[sdzjz] 压测铺场中：核心×" + cores + " × 节点" + nodesPer + "（工况=" + workload + "）· " + seconds
                + "s · cap=" + (capParam > 0 ? capParam : "不动配置") + " —— 跑完报告落游戏目录并自动清场";
    }

    /** m355 三档矩阵：100/300/500 核 ×64 节点自动串跑，档间清场+冷却，收官落汇总对比文件。 */
    public static String startMatrix(ServerLevel w, BlockPos at, UUID by, int secs, long cap) {
        return startMatrix(w, at, by, secs, cap, Workload.MIXED);
    }

    /** m360 工况版矩阵。 */
    public static String startMatrix(ServerLevel w, BlockPos at, UUID by, int secs, long cap, Workload wl) {
        if (phase != Phase.IDLE) return "§c已有压测在跑（/sdzjz bench stop 可中止）";
        matrixWl = wl;
        matrixQueue = new java.util.ArrayDeque<>();
        matrixQueue.add(new int[]{100, 64});
        matrixQueue.add(new int[]{300, 64});
        matrixQueue.add(new int[]{500, 64});
        matrixRows = new java.util.ArrayList<>();
        matrixWorld = w; matrixAt = at; matrixBy = by; matrixSecs = secs; matrixCap = cap; matrixCooldown = 0;
        int[] first = matrixQueue.poll();
        String r = start(w, at, by, first[0], first[1], secs, cap, matrixWl);
        return r.startsWith("§c") ? r : r + " §b[矩阵 1/3：后续 300/500 档自动接跑，stop=全停]";
    }

    public static String stopNow() {
        if (matrixQueue != null) { matrixQueue = null; matrixRows = null; matrixCooldown = 0; } // m355 停=含后续档
        if (phase == Phase.IDLE) return "§7[sdzjz] 没有在跑的压测";
        if (phase == Phase.RUN) { runTicksLeft = 1; return "§e[sdzjz] 收到，下一拍出报告并清场"; }
        if (phase == Phase.SPAWN || phase == Phase.WIRE) { phase = Phase.CLEANUP; return "§e[sdzjz] 铺场/装配中止，转清场"; } // m359 WIRE 补漏：否则 stop 落空
        return "§7[sdzjz] 正在清场中";
    }

    private static void tick(MinecraftServer server) {
        switch (phase) {
            case IDLE -> { // m355 矩阵接力：档间冷却回稳后自动开下一档
                if (matrixQueue != null && matrixWorld != null) {
                    if (matrixCooldown > 0) { matrixCooldown--; }
                    else if (!matrixQueue.isEmpty()) {
                        int[] t = matrixQueue.poll();
                        int tier = 3 - matrixQueue.size();
                        msg(server, "§b[sdzjz] 矩阵第 " + tier + "/3 档开跑：核心×" + t[0] + " 工况=" + matrixWl);
                        start(matrixWorld, matrixAt, matrixBy, t[0], t[1], matrixSecs, matrixCap, matrixWl);
                    }
                }
            }
            case SPAWN -> {
                for (int b = 0; b < 2 && spawnIdx < SITES.size(); b++, spawnIdx++) spawnSite(spawnIdx); // m360 带站号定工况
                if (spawnIdx >= SITES.size()) {
                    CoreProfiler.resetAll();
                    CoreProfiler.PHASES = true; // m321：压测期自动开细分计时（收尾复原）
                    CoreScheduler.resetStats();
                    if (capParam > 0) { savedCap = SdzjzConfig.get().maxRecipesPerNetworkTick;
                        SdzjzConfig.get().maxRecipesPerNetworkTick = capParam; capTouched = true; }
                    if (activeCrafters > 0) { phase = Phase.WIRE; break; } // m359/m360 有合成机的工况先装配再起测
                    runTicksLeft = (long) seconds * 20;
                    prevNano = 0;
                    gcStart = com.sdzjz.debug.GcAccount.snap(); // m351 起账（END_SERVER_TICK=服务器线程）
                    phase = Phase.RUN;
                    msg(server, "§a[sdzjz] 铺场完成，压测开跑 " + seconds + "s（/sdzjz bench stop 可提前收）");
                }
            }
            case WIRE -> { // m359 装配：等首扫入端点表后，4 站/tick 连线（供料边/节点边/出库边/翻黑名单）
                if (wireDelay > 0) { wireDelay--; break; }
                for (int b = 0; b < 4 && wireIdx < SITES.size(); b++, wireIdx++) wireSite(wireIdx); // m360 带站号定工况
                if (wireIdx >= SITES.size()) {
                    runTicksLeft = (long) seconds * 20;
                    prevNano = 0;
                    gcStart = com.sdzjz.debug.GcAccount.snap();
                    phase = Phase.RUN;
                    msg(server, "§a[sdzjz] 装配完毕（" + activeCrafters + " 台合成机上线），测量开始");
                }
            }
            case RUN -> {
                long now = System.nanoTime();
                if (prevNano != 0 && tickCount < tickNs.length) {
                    // m307 忙时真值：原版把本拍耗时写在 tickTimes[ticks%100]，END_SERVER_TICK 时已写毕
                    busyNs[tickCount] = server.getTickTimesNanos()[server.getTickCount() % 100];
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
                    CoreProfiler.PHASES = false; // m321 复原（手动 profile phase on 的长开场景 bench 后重开即可）
                    phase = Phase.CLEANUP;
                }
            }
            case CLEANUP -> {
                for (int b = 0; b < 4 && cleanupIdx < SITES.size(); b++, cleanupIdx++) cleanSite(SITES.get(cleanupIdx));
                if (cleanupIdx >= SITES.size()) {
                    msg(server, "§a[sdzjz] 压测清场完毕（配置已复原，方块零残留）");
                    phase = Phase.IDLE; world = null; SITES.clear();
                    if (matrixQueue != null) { // m355 矩阵接力/收官
                        if (!matrixQueue.isEmpty()) { matrixCooldown = 200; msg(server, "§b[sdzjz] 矩阵档间冷却 10s（GC/MSPT 回稳）…"); }
                        else { matrixQueue = null; matrixRows = null; msg(server, "§a[sdzjz] 三档矩阵全部完成"); }
                    }
                }
            }
        }
    }

    private static void spawnSite(int idx) {
        BlockPos p = SITES.get(idx);
        world.setBlockAndUpdate(p, ModBlocks.STRUCTURE_CORE.defaultBlockState(), 3);
        world.setBlockAndUpdate(p.east(), ModBlocks.STORAGE_CORE.defaultBlockState(), 3);
        if (world.getBlockEntity(p) instanceof StructureCoreBlockEntity core) {
            switch (siteWl[idx]) { // m359/m360 逐站工况铺场
                case IDLE -> { /* 零节点：纯核心框架开销基线 */ }
                case COBBLE -> {
                    ItemStack pack = new ItemStack(ModItems.COBBLE_MAKER, nodesPer);
                    while (!pack.isEmpty() && core.insertMachine(null, pack)) { /* insertMachine 每次吃 1 台并 decrement */ }
                }
                case MIXED -> throw new IllegalStateException("siteWl 已定型不该出现 MIXED"); // 防呆
                case CRAFT_CHAIN -> { // m360 深链：仓灌原木，三级配方靠节点边流转，末级回仓
                    if (world.getBlockEntity(p.east()) instanceof com.sdzjz.block.StorageCoreBlockEntity scC) {
                        ItemStack logs = new ItemStack(net.minecraft.world.item.Items.SPRUCE_LOG);
                        logs.setCount(10_000_000);
                        scC.deposit(logs);
                    }
                    ItemStack cf = new ItemStack(ModItems.FILTER_NODE, 1);
                    ItemStack cc = new ItemStack(ModItems.AUTO_CRAFTER, 1);
                    String[] tgts = {"minecraft:spruce_planks", "minecraft:stick", "minecraft:ladder"};
                    for (int u = 0; u < nodesPer / CHAIN_UNIT; u++) {
                        int base = u * CHAIN_UNIT;
                        boolean full = true;
                        for (int stg = 0; stg < 3 && full; stg++) { // 每级=F,F,C
                            cf.setCount(1); full &= core.insertMachine(null, cf);
                            cf.setCount(1); full &= core.insertMachine(null, cf);
                            cc.setCount(1); full &= core.insertMachine(null, cc);
                        }
                        if (!full) break;
                        for (int stg = 0; stg < 3; stg++) {
                            core.toggleFilterEntry(base + stg * 3, "");
                            core.toggleFilterEntry(base + stg * 3 + 1, "");
                            core.setNodeTarget(base + stg * 3 + 2, tgts[stg]);
                        }
                    }
                }
                case CRAFT_FED -> { // 仓→过滤→合成机→回仓：预灌 1000 万木板（cap500×300s 最坏偏斜也吃不穿，绝无假空转）
                    if (world.getBlockEntity(p.east()) instanceof com.sdzjz.block.StorageCoreBlockEntity scF) {
                        ItemStack planks = new ItemStack(net.minecraft.world.item.Items.SPRUCE_PLANKS);
                        planks.setCount(10_000_000); // deposit=账本 merge 一笔入账，非逐栈
                        scF.deposit(planks);
                    }
                    ItemStack pf = new ItemStack(ModItems.FILTER_NODE, 1);
                    ItemStack pc = new ItemStack(ModItems.AUTO_CRAFTER, 1);
                    for (int k = 0; k < nodesPer / 2; k++) { // 交替插：偶=过滤 奇=合成机（wireSite 按此配对）
                        pf.setCount(1); if (!core.insertMachine(null, pf)) break;
                        pc.setCount(1); if (!core.insertMachine(null, pc)) break;
                        core.toggleFilterEntry(2 * k, "");                            // 翻黑名单：空黑名单=全放行
                        core.setNodeTarget(2 * k + 1, "minecraft:crafting_table");    // 真配方：4 木板→1 工作台
                    }
                }
            }
            core.running = true;
            // m307 首票自举（首轮实测 20 核只跑 6 的真凶）：核心自持票在**它自己 tick 里**注册，
            // 而模拟距离外的新区块根本不给 BE tick 机会=鸡生蛋死锁。代发一张同通道票（force 按
            // 核心坐标记账幂等，核心 ≤20t 自注册接管；拆块 release 走原路，另有清场兜底双保险）。
            com.sdzjz.block.CoreChunkLoading.force(world, p, false);
        }
    }

    /** m359 装配（CRAFT_FED）：供料边(仓→过滤)+节点边(过滤→合成机)+出库边(合成机→仓)。
     *  known 闸已由 WIRE 相位的 5t 延迟满足（首扫哨兵把东侧存储核心扫入端点表）。 */
    private static void wireSite(int idx) {
        BlockPos p = SITES.get(idx);
        if (!(world.getBlockEntity(p) instanceof StructureCoreBlockEntity core)) return;
        long sp = p.east().asLong();
        String dim = world.dimension().location().toString();
        switch (siteWl[idx]) {
            case CRAFT_FED -> {
                for (int k = 0; k < nodesPer / 2; k++) {
                    int f = 2 * k, c = 2 * k + 1;
                    core.toggleStorageEdge(null, f, sp, 1, dim); // 仓→过滤 供料
                    core.toggleConnection(null, f, c);           // 过滤→合成机
                    core.toggleStorageEdge(null, c, sp, 0, dim); // 合成机→仓 出库
                }
            }
            case CRAFT_CHAIN -> { // m360 深链：F→F→C 三级串接，级间 C→下级F 节点边，末级 C 出库
                for (int u = 0; u < nodesPer / CHAIN_UNIT; u++) {
                    int b = u * CHAIN_UNIT;
                    core.toggleStorageEdge(null, b, sp, 1, dim); // 仓→F0 供料（chainWants 经 F0→F1→C 深度递归）
                    for (int stg = 0; stg < 3; stg++) {
                        int f0 = b + stg * 3, f1 = f0 + 1, c = f0 + 2;
                        core.toggleConnection(null, f0, f1);
                        core.toggleConnection(null, f1, c);
                        if (stg < 2) core.toggleConnection(null, c, f0 + 3); // 级间：C→下级F0
                        else core.toggleStorageEdge(null, c, sp, 0, dim);    // 末级：梯子回仓
                    }
                }
            }
            default -> { }
        }
    }

    private static void cleanSite(BlockPos p) {
        if (world.getBlockEntity(p) instanceof StructureCoreBlockEntity core) core.benchClearNodes();
        world.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);       // 节点已清空,dropAll 散落=零件
        world.setBlockAndUpdate(p.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); // 存储账本是虚拟账,拆块零散落
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

        Set<Long> mine = new HashSet<>(); for (BlockPos sp0 : SITES) mine.add(sp0.asLong()); // m366 Row.pos 已 long 化
        List<CoreScheduler.Row> rows = CoreScheduler.statRows();
        List<CoreScheduler.Row> bench = rows.stream().filter(r -> mine.contains(r.pos))
                .sorted(java.util.Comparator.comparingLong(r -> r.granted)).toList();
        // m307 防哑账（首轮实测被 14/20 沉默核心骗出"达标"）：铺场清单逐一对上账,缺席点名
        Set<Long> seen = new HashSet<>(); for (CoreScheduler.Row r : bench) seen.add(r.pos); // m366b Row.pos 已 long
        List<BlockPos> silent = new ArrayList<>();
        for (int k = 0; k < SITES.size(); k++) { // m360 idle 站不生产不申请=豁免防哑账
            if (k < siteWl.length && siteWl[k] == Workload.IDLE) continue;
            if (!seen.contains(SITES.get(k).asLong())) silent.add(SITES.get(k));
        }
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
          .append(" machine=").append(switch (workload) {
              case IDLE -> "idle(零节点纯框架基线)";
              case COBBLE -> "cobble_maker(10t无输入满载)";
              case CRAFT_FED -> "craft_fed(仓→过滤→合成机→回仓, 4木板→工作台, 预灌1000万)";
              case CRAFT_CHAIN -> "craft_chain(9节点深链×" + (nodesPer / CHAIN_UNIT) + ": 原木→木板→木棍→梯子, 双过滤前置)";
              case MIXED -> "mixed(25%×4轮布: idle/cobble/craft_fed/craft_chain)"; })
          .append(" duration=").append(seconds).append("s cap=")
          .append(capParam > 0 ? String.valueOf(capParam) : ("未改动(现值" + capUsed + ")")).append('\n');
        sb.append("维度: ").append(world.dimension().location()).append("  origin=").append(origin.toShortString())
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
        if (gcStart != null) { // m351 GC/分配账（m349/m350 热路径刀的对表尺）
            com.sdzjz.debug.GcAccount ge = com.sdzjz.debug.GcAccount.snap();
            double winSec = Math.max(1e-9, (ge.nanoTime - gcStart.nanoTime) / 1e9);
            long gcC = Math.max(0, ge.gcCount - gcStart.gcCount);
            long gcT = Math.max(0, ge.gcMs - gcStart.gcMs);
            sb.append(String.format("GC/分配(测窗 %.1fs): GC %d 次 | 停顿累计 %d ms (占窗 %.2f%%)",
                    winSec, gcC, gcT, gcT / (winSec * 10)));
            long ab = -1; // m355 矩阵行捕获用（-1=分配账不可用）
            if (ge.allocOk && gcStart.allocOk) {
                ab = Math.max(0, ge.allocBytes - gcStart.allocBytes);
                sb.append(String.format(" | 服务器线程分配 %.1f MB | %.1f MB/s | %.1f KB/tick",
                        ab / 1048576.0, ab / 1048576.0 / winSec, tickCount > 0 ? ab / 1024.0 / tickCount : 0));
            } else sb.append(" | 分配账不可用(非HotSpot/被禁)");
            sb.append('\n').append("  口径: GC=全JVM合计(集成服含渲染线程诱发), 分配=仅服务器线程; 对比请同环境同参数跑\n");
            if (activeCrafters > 0 && tickCount > 0) { // m359 合成机口径（作者拍板 A 三指标：ns/台·tick 双账+分配/台·tick）
                long ctks = (long) activeCrafters * tickCount;
                sb.append(String.format(
                        "合成机口径(×%d台): 类型账 %.0f ns/台·tick | exec %.0f ns/次·%d次 | chainWants 总 %.1f ms | plans 总 %.1f ms | 分配摊台 %.2f KB/台·tick%n",
                        activeCrafters,
                        com.sdzjz.debug.CoreProfiler.subNsOf(com.sdzjz.debug.CoreProfiler.SUB_T_CRAFT) * 1.0 / ctks,
                        com.sdzjz.debug.CoreProfiler.subNsOf(com.sdzjz.debug.CoreProfiler.SUB_P_EXEC) * 1.0
                                / Math.max(1, com.sdzjz.debug.CoreProfiler.subCallsOf(com.sdzjz.debug.CoreProfiler.SUB_P_EXEC)),
                        com.sdzjz.debug.CoreProfiler.subCallsOf(com.sdzjz.debug.CoreProfiler.SUB_P_EXEC),
                        com.sdzjz.debug.CoreProfiler.subNsOf(com.sdzjz.debug.CoreProfiler.SUB_CHAIN) / 1e6,
                        com.sdzjz.debug.CoreProfiler.subNsOf(com.sdzjz.debug.CoreProfiler.SUB_PLANNER) / 1e6,
                        ab >= 0 ? ab / 1024.0 / ctks : -1));
                sb.append("  口径: 分配摊台=全窗分配/(台×tick)含非合成开销, 是上界不是净值; ns/台·tick 才是纯合成账\n");
            }
            if (matrixRows != null) { // m355 每档关键行（纵向对比 µs/核·tick 线性度与类型前三换位）
                matrixRows.add(String.format(
                        "%3d核×%d: MSPT均%6.2f P95 %6.2f | 核tick均 %6.1fµs | GC %d次 %dms(%.2f%%窗) | 分配 %s | 前三: %s",
                        cores, nodesPer, busy[0], busy[1], com.sdzjz.debug.CoreProfiler.avgCoreTickUs(),
                        gcC, gcT, gcT / (winSec * 10),
                        ab >= 0 ? String.format("%.0fMB/s %.0fKB/tick", ab / 1048576.0 / winSec,
                                tickCount > 0 ? ab / 1024.0 / tickCount : 0) : "不可用",
                        com.sdzjz.debug.CoreProfiler.typeTop3()));
            }
        }
        String verdict;
        if (workload == Workload.IDLE) { // m359 零节点基线：无申请无哑账，调度判据不适用
            sb.append("判据: idle 基线工况——零节点无生产申请，调度判据不适用，本档只看 核tick均/GC/分配\n");
            silent.clear();
        }
        double ratio = min > 0 ? (double) max / min : Double.NaN;
        if (workload == Workload.IDLE) verdict = "（见上）";
        else if (!silent.isEmpty()) verdict = "不达标：" + silent.size() + " 个铺场核心从未上账（未tick/未申请）——数据不可用请贴报告";
        else if (zero > 0)     verdict = "不达标：存在零吞吐核心（防饥饿失效，请贴报告）";
        else if (workload == Workload.MIXED) verdict = "混合工况达标：非idle站全上账且零吞吐=0（跨型倍数不适用，各型成本看[类型账]与合成机口径行）";
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
        sb.append('\n').append("Top Hotspots（m321 阶段账，细分计时压测期自动开）:\n");
        for (String ln : CoreProfiler.phaseReport()) sb.append("  ").append(ln).append('\n');
        sb.append('\n').append("逐核明细(granted升序; tick耗时µs来自/sdzjz profile core同一环形窗):\n");
        String dim = world.dimension().location().toString();
        List<CoreProfiler.Stats> prof = CoreProfiler.active(dim);
        for (CoreScheduler.Row r : bench) {
            CoreProfiler.Stats s = null;
            for (CoreProfiler.Stats c : prof) if (c.pos == r.pos) { s = c; break; } // m366 双侧同 long
            sb.append(String.format("  %s granted=%d 记名=%d", BlockPos.of(r.pos).toShortString(), r.granted, r.zeroEvents));
            if (s != null) sb.append(String.format("  tick均%.0fµs 峰%.0fµs 编译%d", s.avgMicros(), s.maxMicros(), s.planCompiles));
            sb.append('\n');
        }

        reportPath = server.getServerDirectory().resolve("sdzjz_bench_" + ts + ".txt");
        Files.write(reportPath, sb.toString().getBytes(StandardCharsets.UTF_8));
        Sdzjz_log("[sdzjz bench] 报告已写盘: " + reportPath);
        if (matrixRows != null && matrixQueue != null && matrixQueue.isEmpty()) { // m355 末档=落汇总
            StringBuilder ms = new StringBuilder("《生电终结者》三档矩阵汇总 ").append(ts).append('\n');
            ms.append("每档 ").append(seconds).append("s cap=").append(capParam > 0 ? String.valueOf(capParam) : "未改动")
              .append(" 工况=").append(workload)
              .append("；看点：核tick均µs 随规模是否近似持平（超线性=争抢/缓存失效），类型前三是否换位（换位=下一刀换靶）\n\n");
            for (String r : matrixRows) ms.append(r).append('\n');
            java.nio.file.Path mp = server.getServerDirectory().resolve("sdzjz_bench_matrix_" + ts + ".txt");
            Files.write(mp, ms.toString().getBytes(StandardCharsets.UTF_8));
            msg(server, "§a[sdzjz] 三档矩阵汇总已写盘: " + mp);
        }
    }

    private static void msg(MinecraftServer server, String s) {
        if (starter != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(starter);
            if (p != null) p.displayClientMessage(Component.literal(s), false);
        }
        Sdzjz_log(s.replaceAll("§.", ""));
    }

    private static void Sdzjz_log(String s) { com.sdzjz.Sdzjz.LOGGER.info(s); }
}
