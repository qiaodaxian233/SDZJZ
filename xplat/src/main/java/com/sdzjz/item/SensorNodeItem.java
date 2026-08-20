package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * 数量传感器节点（画布逻辑节点）：按存储量开/关物流闸门。
 * 例：铁锭 &lt; 10000 → 开（放行上游）；≥ 10000 → 关（上游暂停），自动补货防爆仓。
 * 上游机器的全部出线目标都关闸时，机器整台暂停（不白产、不塞存储）。
 */
public class SensorNodeItem extends MachineItem {

    public SensorNodeItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("画布逻辑节点：按存储量开/关物流闸门").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("例：铁锭 < 10000 才放行 → 自动补货防爆仓").formatted(ChatFormatting.GRAY));
        tooltip.add(Component.literal("闸门关闭时上游机器整台暂停（不白产）").formatted(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.literal("默认监测主存储；连一条 存储→传感器 供料线=监测那个库").formatted(ChatFormatting.GRAY));
        tooltip.add(Component.literal("放入画布后右键节点配置").formatted(ChatFormatting.GRAY));
    }
}
