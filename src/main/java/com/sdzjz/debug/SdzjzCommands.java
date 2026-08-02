package com.sdzjz.debug;

import com.sdzjz.Sdzjz;
import com.sdzjz.block.StructureCoreBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * m177 调试命令（审查报告三之调试三件套，OP2 权限）：
 *   /sdzjz profile core    —— 本维度活跃核心逐个报：节点/边/tick 均值峰值/路由/供料/链查 速率
 *   /sdzjz profile network —— 全服同步账单：核心 NBT 包数与字节、端点直发包数与条目
 *   /sdzjz profile reset   —— 计数窗清零（耗时环形窗自然滚动不用清）
 *   /sdzjz dumpgraph       —— 就近核心整图转储进服务器日志（节点/连线/状态），聊天给摘要
 */
public final class SdzjzCommands {
    private SdzjzCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((disp, reg, env) -> disp.register(
                literal("sdzjz").requires(s -> s.hasPermissionLevel(2))
                        .then(literal("profile")
                                .then(literal("core").executes(c -> profileCore(c.getSource())))
                                .then(literal("network").executes(c -> profileNetwork(c.getSource())))
                                .then(literal("reset").executes(c -> {
                                    CoreProfiler.resetAll();
                                    c.getSource().sendFeedback(() -> Text.literal("§a[sdzjz] 剖析计数窗已清零"), false);
                                    return 1;
                                })))
                        .then(literal("dumpgraph").executes(c -> dumpGraph(c.getSource())))));
    }

    private static int profileCore(ServerCommandSource src) {
        String dim = src.getWorld().getRegistryKey().getValue().toString();
        List<CoreProfiler.Stats> list = CoreProfiler.active(dim);
        if (list.isEmpty()) {
            src.sendFeedback(() -> Text.literal("§7[sdzjz] 本维度最近 5 秒无活跃核心（核心需已加载）"), false);
            return 0;
        }
        src.sendFeedback(() -> Text.literal("§b[sdzjz] 活跃核心 ×" + list.size() + "（按均耗排序，窗口=最近100tick）"), false);
        for (CoreProfiler.Stats s : list) {
            String line = String.format("§f%s §7| 节点%d 边%d %s §7| tick §e均%.0fµs §6峰%.0fµs §7| 路由 %.1f/s 供料 %.1f/s 链查 %.1f/s",
                    s.pos.toShortString(), s.nodes, s.edges, s.running ? "§a运行" : "§8停机",
                    s.avgMicros(), s.maxMicros(),
                    s.perSec(s.routes), s.perSec(s.storageResolves), s.perSec(s.chainChecks));
            src.sendFeedback(() -> Text.literal(line), false);
        }
        return list.size();
    }

    private static int profileNetwork(ServerCommandSource src) {
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
        src.sendFeedback(() -> Text.literal(line), false);
        src.sendFeedback(() -> Text.literal("§7  （端点直发包按 payload 条目计，字节未计——增量同步改造后此处对表收益）"), false);
        return 1;
    }

    private static int dumpGraph(ServerCommandSource src) {
        BlockPos me = BlockPos.ofFloored(src.getPosition());
        String dim = src.getWorld().getRegistryKey().getValue().toString();
        CoreProfiler.Stats best = null; double bd = 64 * 64;
        for (CoreProfiler.Stats s : CoreProfiler.active(dim)) {
            double d = s.pos.getSquaredDistance(me);
            if (d < bd) { bd = d; best = s; }
        }
        if (best == null) {
            src.sendFeedback(() -> Text.literal("§7[sdzjz] 64 格内无活跃核心"), false);
            return 0;
        }
        if (!(src.getWorld().getBlockEntity(best.pos) instanceof StructureCoreBlockEntity be)) {
            src.sendFeedback(() -> Text.literal("§7[sdzjz] 核心方块实体未加载"), false);
            return 0;
        }
        String dump = be.debugDump();
        Sdzjz.LOGGER.info("[sdzjz dumpgraph] {} @ {}\n{}", dim, best.pos.toShortString(), dump);
        final CoreProfiler.Stats fb = best;
        src.sendFeedback(() -> Text.literal(String.format(
                "§a[sdzjz] 已转储核心 %s：节点 %d / 边 %d → 服务器日志 logs/latest.log", fb.pos.toShortString(), fb.nodes, fb.edges)), false);
        return 1;
    }
}
