package com.sdzjz.item;

import com.sdzjz.Sdzjz;
import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.structure.StructureBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 建造蓝图：右键地面，在其上方摆出预制结构（分 tick 摆放，防卡顿）。
 * config.structureConsumeMaterials=true 时先按材料清单扣背包（够料才建）。
 */
public class StructureBlueprintItem extends Item {

    public StructureBlueprintItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world instanceof ServerWorld sw)) return ActionResult.FAIL;

        List<StructureBuilder.Placement> plan = StructureBuilder.plan();
        if (plan.isEmpty()) return ActionResult.FAIL;

        PlayerEntity player = ctx.getPlayer();
        boolean creative = player != null && player.isCreative();
        SdzjzConfig cfg = SdzjzConfig.get();

        if (cfg.structureConsumeMaterials && !creative) {
            String missing = tryConsume(player, StructureBuilder.tally());
            if (missing != null) {
                msg(player, "材料不足：还缺 " + missing);
                return ActionResult.FAIL;
            }
        }

        BlockPos origin = ctx.getBlockPos().up();
        StructureBuilder.enqueue(sw, origin, plan);
        if (!creative) ctx.getStack().decrement(1);
        msg(player, "开始建造：共 " + plan.size() + " 块，分批摆放中…");
        Sdzjz.LOGGER.info("[生电终结者] 建造入队：{} 块 @ {}", plan.size(), origin);
        return ActionResult.SUCCESS;
    }

    /** 检查并从背包扣除材料；材料不足返回缺口描述，充足则扣除并返回 null。 */
    private static String tryConsume(PlayerEntity player, Map<Item, Integer> need) {
        if (player == null) return "玩家不存在";
        PlayerInventory inv = player.getInventory();
        Map<Item, Integer> have = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) have.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        for (Map.Entry<Item, Integer> e : need.entrySet()) {
            int lack = e.getValue() - have.getOrDefault(e.getKey(), 0);
            if (lack > 0) return e.getKey().getName().getString() + "×" + lack;
        }
        for (Map.Entry<Item, Integer> e : need.entrySet()) {
            int rem = e.getValue();
            for (int i = 0; i < inv.size() && rem > 0; i++) {
                ItemStack s = inv.getStack(i);
                if (s.getItem() == e.getKey()) {
                    int take = Math.min(rem, s.getCount());
                    s.decrement(take);
                    rem -= take;
                }
            }
        }
        return null;
    }

    private static void msg(PlayerEntity player, String s) {
        if (player != null) player.sendMessage(Text.literal(s), true);
    }
}
