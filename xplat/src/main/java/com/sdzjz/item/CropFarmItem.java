package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/** 全自动农场：放入画布后点节点徽章选作物（小麦/胡萝卜/土豆/甜菜/西瓜/南瓜/甘蔗/蘑菇/可可果），免费产出。 */
public class CropFarmItem extends MachineItem {

    public CropFarmItem(Properties settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("周期 2 秒（吃加速/数量/并列升级）").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("免费产出（对齐原版农场）").withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.literal("可种: 小麦/胡萝卜/土豆/甜菜/西瓜/南瓜/甘蔗/蘑菇/可可果").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("放入画布后，点节点右上角徽章选择作物").withStyle(ChatFormatting.GRAY));
    }
}
