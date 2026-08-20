package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/** 自动合成机：放入画布后点节点徽章选目标产物；按原版合成配方吃料出货，可量产任何有配方的物品。 */
public class AutoCrafterItem extends MachineItem {

    public AutoCrafterItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("周期 2 秒（吃加速/数量/并列升级）").formatted(ChatFormatting.GRAY));
        tooltip.add(Component.literal("按原版合成配方消耗材料（存储网络或连线喂料）").formatted(ChatFormatting.RED));
        tooltip.add(Component.literal("放入画布后，点节点右上角徽章选择目标产物").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("可量产任何有合成配方的物品").formatted(ChatFormatting.DARK_GREEN));
    }
}
