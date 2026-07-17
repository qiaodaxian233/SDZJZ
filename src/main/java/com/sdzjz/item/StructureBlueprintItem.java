package com.sdzjz.item;

import com.sdzjz.Sdzjz;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 建造蓝图：右键地面，在其上方一键摆出预制结构（来自 ObjToSchematic 导出的 .mcfunction）。
 * .mcfunction 每行形如 `setblock ~x ~y ~z minecraft:block`（相对坐标、无方块状态）。
 * Phase 1：解析缓存 + 一次性摆放 + 消耗蓝图。材料清单校验/分tick摆放为后续。
 */
public class StructureBlueprintItem extends Item {

    private static final String RES = "/structures/block_mesh.mcfunction";
    private static List<Placement> CACHE;
    private record Placement(int x, int y, int z, Block block) {}

    public StructureBlueprintItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;

        List<Placement> plan = load();
        if (plan.isEmpty()) return ActionResult.FAIL;

        BlockPos origin = ctx.getBlockPos().up();
        int flags = Block.NOTIFY_LISTENERS;
        for (Placement p : plan) {
            world.setBlockState(origin.add(p.x, p.y, p.z), p.block.getDefaultState(), flags);
        }

        PlayerEntity player = ctx.getPlayer();
        if (player != null && !player.isCreative()) ctx.getStack().decrement(1);
        Sdzjz.LOGGER.info("[生电终结者] 建造结构：放置 {} 个方块 @ {}", plan.size(), origin);
        return ActionResult.SUCCESS;
    }

    private static synchronized List<Placement> load() {
        if (CACHE != null) return CACHE;
        List<Placement> list = new ArrayList<>();
        try (InputStream is = StructureBlueprintItem.class.getResourceAsStream(RES)) {
            if (is == null) {
                Sdzjz.LOGGER.warn("[生电终结者] 找不到结构文件 {}", RES);
                CACHE = list;
                return list;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("setblock")) continue;
                String[] t = line.split("\\s+");
                if (t.length < 5) continue;
                int x = rel(t[1]), y = rel(t[2]), z = rel(t[3]);
                Block b = Registries.BLOCK.get(Identifier.of(t[4]));
                list.add(new Placement(x, y, z, b));
            }
        } catch (Exception e) {
            Sdzjz.LOGGER.error("[生电终结者] 解析结构失败", e);
        }
        CACHE = list;
        return list;
    }

    private static int rel(String s) {
        s = s.replace("~", "");
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }
}
