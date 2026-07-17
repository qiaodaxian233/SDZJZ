package com.sdzjz.structure;

import com.sdzjz.Sdzjz;
import com.sdzjz.config.SdzjzConfig;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 结构解析(缓存) + 分 tick 摆放任务队列 + 材料清单统计。 */
public final class StructureBuilder {

    private static final String RES = "/structures/block_mesh.mcfunction";

    public record Placement(int x, int y, int z, Block block) {}

    private static List<Placement> PLAN;
    private static final List<Job> JOBS = new ArrayList<>();

    private static final class Job {
        final ServerWorld w;
        final BlockPos o;
        final List<Placement> plan;
        int i;
        Job(ServerWorld w, BlockPos o, List<Placement> plan) { this.w = w; this.o = o; this.plan = plan; }
    }

    /** 解析并缓存结构方块清单。 */
    public static synchronized List<Placement> plan() {
        if (PLAN != null) return PLAN;
        List<Placement> list = new ArrayList<>();
        try (InputStream is = StructureBuilder.class.getResourceAsStream(RES)) {
            if (is == null) {
                Sdzjz.LOGGER.warn("[生电终结者] 找不到结构文件 {}", RES);
                PLAN = list;
                return list;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("setblock")) continue;
                String[] t = line.split("\\s+");
                if (t.length < 5) continue;
                Block b = Registries.BLOCK.get(Identifier.of(t[4]));
                list.add(new Placement(rel(t[1]), rel(t[2]), rel(t[3]), b));
            }
        } catch (Exception e) {
            Sdzjz.LOGGER.error("[生电终结者] 解析结构失败", e);
        }
        PLAN = list;
        return list;
    }

    /** 材料清单：方块 → 数量（跳过空气/无对应物品）。 */
    public static Map<Item, Integer> tally() {
        Map<Item, Integer> m = new LinkedHashMap<>();
        for (Placement p : plan()) {
            if (p.block() == Blocks.AIR) continue;
            Item it = p.block().asItem();
            if (it == null || it == Items.AIR) continue;
            m.merge(it, 1, Integer::sum);
        }
        return m;
    }

    public static void enqueue(ServerWorld w, BlockPos origin, List<Placement> plan) {
        JOBS.add(new Job(w, origin, plan));
    }

    /** 每服务端 tick 推进各建造任务，按 config.structureBlocksPerTick 分批摆放。 */
    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        int per = Math.max(64, SdzjzConfig.get().structureBlocksPerTick);
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            int end = Math.min(j.i + per, j.plan.size());
            for (; j.i < end; j.i++) {
                Placement p = j.plan.get(j.i);
                j.w.setBlockState(j.o.add(p.x(), p.y(), p.z()), p.block().getDefaultState(), Block.NOTIFY_LISTENERS);
            }
            if (j.i >= j.plan.size()) it.remove();
        }
    }

    private static int rel(String s) {
        s = s.replace("~", "");
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }
}
