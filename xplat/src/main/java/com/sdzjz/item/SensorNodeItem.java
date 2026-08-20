package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("画布逻辑节点：按存储量开/关物流闸门").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("例：铁锭 < 10000 才放行 → 自动补货防爆仓").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("闸门关闭时上游机器整台暂停（不白产）").formatted(Formatting.DARK_GREEN));
        tooltip.add(Text.literal("默认监测主存储；连一条 存储→传感器 供料线=监测那个库").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("放入画布后右键节点配置").formatted(Formatting.GRAY));
    }
}
