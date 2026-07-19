package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** 全自动农场：放入画布后点节点徽章选作物（小麦/胡萝卜/土豆/甜菜/西瓜/南瓜/甘蔗/蘑菇/可可果），免费产出。 */
public class CropFarmItem extends MachineItem {

    public CropFarmItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("周期 2 秒（吃加速/数量/并列升级）").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("免费产出（对齐原版农场）").formatted(Formatting.DARK_GREEN));
        tooltip.add(Text.literal("可种: 小麦/胡萝卜/土豆/甜菜/西瓜/南瓜/甘蔗/蘑菇/可可果").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("放入画布后，点节点右上角徽章选择作物").formatted(Formatting.GRAY));
    }
}
