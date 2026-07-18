package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** 自动合成机：放入画布后点节点徽章选目标产物；按原版合成配方吃料出货，可量产任何有配方的物品。 */
public class AutoCrafterItem extends MachineItem {

    public AutoCrafterItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("周期 2 秒（吃加速/数量/并列升级）").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("按原版合成配方消耗材料（存储网络或连线喂料）").formatted(Formatting.RED));
        tooltip.add(Text.literal("放入画布后，点节点右上角徽章选择目标产物").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("可量产任何有合成配方的物品").formatted(Formatting.DARK_GREEN));
    }
}
