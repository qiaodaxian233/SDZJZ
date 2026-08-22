package com.sdzjz.node;

import com.sdzjz.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * m427 绞杀者拆分第三刀：节点升级折算族（自 StructureCoreBlockEntity 原样迁入，方法体一字未改）。
 * 三张表是全库「升级类型 type ⇄ 物品/NBT 键」的唯一出口：type 0=速度(spd)/1=数量(cnt)/2=并发(par)。
 * upgradeItem/upgradeKey 纯映射零状态；refundUpgrades 只碰玩家背包，不碰任何 BE 状态。
 * 本刀直接切调用点、不留垫片（m426 教训：垫片=双身风险；本族全部调用点仅在 SCBE 一文件，逐点可数）。
 * SCBE 里 tick 读 spd/cnt/par 等级的运行时消费点不属本族，仍留原位。新代码直用本类。
 */
public final class NodeUpgrades {
    private NodeUpgrades() {}

    public static Item upgradeItem(int type) {
        return switch (type) {
            case 0 -> ModItems.SPEED_UPGRADE;
            case 1 -> ModItems.COUNT_UPGRADE;
            case 2 -> ModItems.PARALLEL_UPGRADE;
            default -> null;
        };
    }

    public static String upgradeKey(int type) {
        return switch (type) {
            case 0 -> "spd";
            case 1 -> "cnt";
            case 2 -> "par";
            default -> "";
        };
    }

    /** m128：把节点 NBT 里的内嵌升级折成物品退还玩家（returnNodeClean 与融合聚敛共用，双写归一）。 */
    public static void refundUpgrades(Player player, CompoundTag n) {
        for (int type = 0; type < 3; type++) {
            int lv = n.getInt(upgradeKey(type));
            Item item = upgradeItem(type);
            while (lv-- > 0 && item != null) {
                ItemStack up = new ItemStack(item);
                if (!player.getInventory().add(up)) player.drop(up, false);
            }
        }
    }
}
