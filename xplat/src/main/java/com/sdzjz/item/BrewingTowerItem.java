package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/** 酿造塔（m131b）：放入画布后点节点徽章选目标药水（普通/喷溅/滞留 × 延长/强化全谱）；
 *  按原版酿造链吃 材料+玻璃瓶+烈焰粉，一批出 3 瓶，产物带药水组件走精确存储入库。 */
public class BrewingTowerItem extends MachineItem {

    public BrewingTowerItem(Properties settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("周期 2 秒（吃加速/数量/并列升级）").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("按原版酿造链消耗 材料+玻璃瓶×3+烈焰粉（1粉=20步）").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("放入画布后，点节点右上角徽章选择目标药水").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("喷溅/滞留/延长/强化全支持；产物直接入库（精确存储）").withStyle(ChatFormatting.DARK_GREEN));
    }
}
