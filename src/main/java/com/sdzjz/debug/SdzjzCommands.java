package com.sdzjz.debug;

import com.sdzjz.Sdzjz;
import com.sdzjz.block.StructureCoreBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.util.List;

import static net.minecraft.commands.Commands.literal;

/**
 * m177 调试命令（审查报告三之调试三件套，OP2 权限）：
 *   /sdzjz profile core    —— 本维度活跃核心逐个报：节点/边/tick 均值峰值/路由/供料/链查 速率
 *   /sdzjz profile network —— 全服同步账单：核心 NBT 包数与字节、端点直发包数与条目\n *   /sdzjz profile sched   —— m304 调度器账单：cap/上拍消费/名单数 + 各核 granted 分布（压测判据直出）
 *   /sdzjz profile remover —— m391 移除器七段账（SCAN/FILTER/LOOT/MUTATE/SEAL/ROUTE/FX，phase on 期间累计）
 *   /sdzjz profile reset   —— 计数窗清零（耗时环形窗自然滚动不用清）
 *   /sdzjz dumpgraph       —— 就近核心整图转储进服务器日志（节点/连线/状态），聊天给摘要\n *   /sdzjz bench start [核数] [每核节点] [秒] [cap] / stop —— m306 一键压测：自动铺场→满载→\n *       报告落 <游戏目录>/sdzjz_bench_时间戳.txt →自动清场复原配置（默认 20×64×60s×cap100）
 */
public final class SdzjzCommands {
    private SdzjzCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((disp, reg, env) -> disp.register(
                literal("sdzjz").requires(s -> s.hasPermission(2))
                        .then(literal("profile")
                                .then(literal("core").executes(c -> profileCore(c.getSource())))
                                .then(literal("network").executes(c -> profileNetwork(c.getSource())))
                                .then(literal("sched").executes(c -> profileSched(c.getSource()))) // m304 调度器账单
                                .then(literal("phase") // m321 阶段账单：谁在吃时间
                                        .executes(c -> {
                                            for (String ln : CoreProfiler.phaseReport())
                                                c.getSource().sendSuccess(() -> Component.literal("§7" + ln), false);
                                            return 1;
                                        })
                                        .then(literal("on").executes(c -> {
                                            CoreProfiler.PHASES = true;
                                            c.getSource().sendSuccess(() -> Component.literal("§a[sdzjz] 细分计时已开（热路径逐调用计时，测完记得 off）"), false);
                                            return 1;
                                        }))
                                        .then(literal("off").executes(c -> {
                                            CoreProfiler.PHASES = false;
                                            c.getSource().sendSuccess(() -> Component.literal("§a[sdzjz] 细分计时已关"), false);
                                            return 1;
                                        })))
                                .then(literal("remover").executes(c -> { // m391 移除器七段账（性能小阶段仪表）
                                    for (String ln : CoreProfiler.removerReport())
                                        c.getSource().sendSuccess(() -> Component.literal("§7" + ln), false);
                                    return 1;
                                }))
                                .then(literal("reset").executes(c -> {
                                    CoreProfiler.resetAll();
                                    com.sdzjz.machine.CoreScheduler.resetStats(); // m304 只清计数不动名单
                                    c.getSource().sendSuccess(() -> Component.literal("§a[sdzjz] 剖析计数窗已清零"), false);
                                    return 1;
                                })))
                        .then(literal("dumpgraph").executes(c -> dumpGraph(c.getSource())))
                        .then(literal("bench") // m306 一键压测
                                .then(literal("start")
                                        .executes(c -> benchStart(c.getSource(), 20, 64, 60, 100))
                                        .then(net.minecraft.commands.Commands.argument("核数", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 500)) // m355 与矩阵最高档同步
                                        .then(net.minecraft.commands.Commands.argument("每核节点", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4096))
                                        .then(net.minecraft.commands.Commands.argument("秒", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 1200))
                                        .then(net.minecraft.commands.Commands.argument("cap", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1_000_000))
                                                .executes(c -> benchStart(c.getSource(),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "核数"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "每核节点"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "秒"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "cap")))
                                                .then(net.minecraft.commands.Commands.argument("工况", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .executes(c -> benchStartW(c.getSource(),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "核数"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "每核节点"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "秒"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "cap"),
                                                                com.mojang.brigadier.arguments.StringArgumentType.getString(c, "工况"))))))))
                                )
                                .then(literal("matrix") // m355 三档矩阵：100/300/500×64 自动串跑；m360 默认混布 25%×4
                                        .executes(c -> benchMatrix(c.getSource(), 60, 100))
                                        .then(net.minecraft.commands.Commands.argument("工况", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .executes(c -> benchMatrixW(c.getSource(), 60, 100,
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(c, "工况")))
                                                .then(net.minecraft.commands.Commands.argument("秒", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 1200))
                                                .then(net.minecraft.commands.Commands.argument("cap", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1_000_000))
                                                        .executes(c -> benchMatrixW(c.getSource(),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "秒"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "cap"),
                                                                com.mojang.brigadier.arguments.StringArgumentType.getString(c, "工况"))))))
                                        .then(net.minecraft.commands.Commands.argument("秒", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 1200))
                                        .then(net.minecraft.commands.Commands.argument("cap", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1_000_000))
                                                .executes(c -> benchMatrix(c.getSource(),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "秒"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "cap"))))))
                                .then(literal("stop").executes(c -> {
                                    final String r = BenchRunner.stopNow();
                                    c.getSource().sendSuccess(() -> Component.literal(r), false);
                                    return 1;
                                })))));
    }

    /** m360 工况版矩阵入口。 */
    private static int benchMatrixW(CommandSourceStack src, int secs, int cap, String wl) {
        BenchRunner.Workload w;
        try { w = BenchRunner.Workload.valueOf(wl.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            src.sendSuccess(() -> Component.literal("§c工况须为 idle|cobble|craft_fed|craft_chain|mixed"), false);
            return 0;
        }
        java.util.UUID by = src.getPlayer() != null ? src.getPlayer().getUUID() : null;
        final String r = BenchRunner.startMatrix(src.getLevel(), BlockPos.containing(src.getPosition()), by, secs, cap, w);
        src.sendSuccess(() -> Component.literal(r), false);
        return 1;
    }

    /** m359 工况版入口：idle|cobble|craft_fed（作者拍板 A：真实合成网络验证）。 */
    private static int benchStartW(CommandSourceStack src, int cores, int nodes, int secs, int cap, String wl) {
        BenchRunner.Workload w;
        try { w = BenchRunner.Workload.valueOf(wl.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            src.sendSuccess(() -> Component.literal("§c工况须为 idle|cobble|craft_fed|craft_chain|mixed"), false);
            return 0;
        }
        java.util.UUID by = src.getPlayer() != null ? src.getPlayer().getUUID() : null;
        final String r = BenchRunner.start(src.getLevel(), BlockPos.containing(src.getPosition()), by, cores, nodes, secs, cap, w);
        src.sendSuccess(() -> Component.literal(r), false);
        return 1;
    }

    /** m355 三档矩阵入口：100/300/500×64 自动串跑，默认每档 60s cap=100。 */
    private static int benchMatrix(CommandSourceStack src, int secs, int cap) {
        java.util.UUID by = src.getPlayer() != null ? src.getPlayer().getUUID() : null;
        final String r = BenchRunner.startMatrix(src.getLevel(), BlockPos.containing(src.getPosition()), by, secs, cap);
        src.sendSuccess(() -> Component.literal(r), false);
        return 1;
    }

    /** m306 一键压测入口：四参齐给才算自定义，否则默认 20核×64节点×60s×cap100（评审矩阵最小档）。 */
    private static int benchStart(CommandSourceStack src, int cores, int nodes, int secs, int cap) {
        java.util.UUID by = src.getPlayer() != null ? src.getPlayer().getUUID() : null;
        final String r = BenchRunner.start(src.getLevel(), BlockPos.containing(src.getPosition()), by, cores, nodes, secs, cap);
        src.sendSuccess(() -> Component.literal(r), false);
        return 1;
    }

    private static int profileCore(CommandSourceStack src) {
        String dim = src.getLevel().dimension().location().toString();
        List<CoreProfiler.Stats> list = CoreProfiler.active(dim);
        if (list.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7[sdzjz] 本维度最近 5 秒无活跃核心（核心需已加载）"), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal("§b[sdzjz] 活跃核心 ×" + list.size() + "（按均耗排序，窗口=最近100tick）"), false);
        for (CoreProfiler.Stats s : list) {
            String line = String.format("§f%s §7| 节点%d 边%d %s §7| tick §e均%.0fµs §6峰%.0fµs §7| 路由 %.1f/s 供料 %.1f/s 链查 %.1f/s §7编译%d",
                    BlockPos.of(s.pos).toShortString(), s.nodes, s.edges, s.running ? "§a运行" : "§8停机",
                    s.avgMicros(), s.maxMicros(),
                    s.perSec(s.routes), s.perSec(s.storageResolves), s.perSec(s.chainChecks), s.planCompiles);
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return list.size();
    }

    /** m304 调度器账单（评审③复评压测判据直出：无长期零吞吐 + 最低最高差距）。
     *  granted 为**累计**口径——想看某段负载的分布，先 /sdzjz profile reset 再压。 */
    private static int profileSched(CommandSourceStack src) {
        long cap = com.sdzjz.config.SdzjzConfig.get().maxRecipesPerNetworkTick;
        if (cap <= 0) {
            src.sendSuccess(() -> Component.literal("§7[sdzjz] 全服预算闸关（maxRecipesPerNetworkTick≤0=无限），调度器整体旁路无账可读"), false);
            return 0;
        }
        java.util.List<com.sdzjz.machine.CoreScheduler.Row> rows = com.sdzjz.machine.CoreScheduler.statRows();
        if (rows.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7[sdzjz] 自上次清零以来无核心申请过预算（核心需在跑且有节点结算）"), false);
            return 0;
        }
        rows.sort(java.util.Comparator.comparingLong(r -> r.granted));
        long min = rows.get(0).granted, max = rows.get(rows.size() - 1).granted;
        long median = rows.get(rows.size() / 2).granted;
        long zeroCores = rows.stream().filter(r -> r.granted == 0).count();
        final String head = String.format(
                "§b[sdzjz] 调度器账单 §7cap=%d/tick 上拍消费=%d 待保底=%d 本拍新记名=%d 核数=%d",
                cap, com.sdzjz.machine.CoreScheduler.prevTickSpent(),
                com.sdzjz.machine.CoreScheduler.starvedPending(), com.sdzjz.machine.CoreScheduler.starvedNew(), rows.size());
        src.sendSuccess(() -> Component.literal(head), false);
        final String verdict = zeroCores > 0
                ? "§c零吞吐核心 ×" + zeroCores + "（长期为 0 = 防饥饿失效，请贴报告）"
                : (min > 0 ? String.format("§a最高/最低 = %.1f×（评审判据：几倍内且无恒 0 = 达标）", (double) max / min) : "§7样本尚少");
        final String stat = String.format("§f granted 最低=%d 中位=%d 最高=%d  %s", min, median, max, verdict);
        src.sendSuccess(() -> Component.literal(stat), false);
        int show = Math.min(3, rows.size());
        for (int i = 0; i < show; i++) {
            com.sdzjz.machine.CoreScheduler.Row r = rows.get(i);
            final String line = String.format("§7  低│%s %s granted=%d 记名=%d", r.dim, BlockPos.of(r.pos).toShortString(), r.granted, r.zeroEvents);
            src.sendSuccess(() -> Component.literal(line), false);
        }
        for (int i = Math.max(show, rows.size() - 3); i < rows.size(); i++) {
            com.sdzjz.machine.CoreScheduler.Row r = rows.get(i);
            final String line = String.format("§7  高│%s %s granted=%d 记名=%d", r.dim, BlockPos.of(r.pos).toShortString(), r.granted, r.zeroEvents);
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return rows.size();
    }

    private static int profileNetwork(CommandSourceStack src) {
        List<CoreProfiler.Stats> list = CoreProfiler.active(null);
        long pk = 0, by = 0, ep = 0, ee = 0; double sec = 0;
        for (CoreProfiler.Stats s : list) {
            pk += s.syncPackets; by += s.syncBytes; ep += s.endsPackets; ee += s.endsEntries;
            sec = Math.max(sec, (System.currentTimeMillis() - s.windowStartMs) / 1000.0);
        }
        final String line = String.format(
                "§b[sdzjz] 同步账单§7（窗口 %.0fs，活跃核心 %d）§f 核心NBT %d 包 / %.1f KB §7|§f 端点直发 %d 包 / 均 %.0f 条 §7|§f 合计 %.2f KB/s",
                sec, list.size(), pk, by / 1024.0, ep, ep == 0 ? 0 : (double) ee / ep,
                sec < 0.05 ? 0 : by / 1024.0 / sec);
        src.sendSuccess(() -> Component.literal(line), false);
        src.sendSuccess(() -> Component.literal("§7  （端点直发包按 payload 条目计，字节未计——增量同步改造后此处对表收益）"), false);
        return 1;
    }

    private static int dumpGraph(CommandSourceStack src) {
        BlockPos me = BlockPos.containing(src.getPosition());
        String dim = src.getLevel().dimension().location().toString();
        CoreProfiler.Stats best = null; double bd = 64 * 64;
        for (CoreProfiler.Stats s : CoreProfiler.active(dim)) {
            double d = BlockPos.of(s.pos).distSqr(me);
            if (d < bd) { bd = d; best = s; }
        }
        if (best == null) {
            src.sendSuccess(() -> Component.literal("§7[sdzjz] 64 格内无活跃核心"), false);
            return 0;
        }
        if (!(src.getLevel().getBlockEntity(BlockPos.of(best.pos)) instanceof StructureCoreBlockEntity be)) {
            src.sendSuccess(() -> Component.literal("§7[sdzjz] 核心方块实体未加载"), false);
            return 0;
        }
        String dump = be.debugDump();
        Sdzjz.LOGGER.info("[sdzjz dumpgraph] {} @ {}\n{}", dim, BlockPos.of(best.pos).toShortString(), dump);
        final CoreProfiler.Stats fb = best;
        src.sendSuccess(() -> Component.literal(String.format(
                "§a[sdzjz] 已转储核心 %s：节点 %d / 边 %d → 服务器日志 logs/latest.log", BlockPos.of(fb.pos).toShortString(), fb.nodes, fb.edges)), false);
        return 1;
    }
}
