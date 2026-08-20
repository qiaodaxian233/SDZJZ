package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** m150 垃圾桶节点（画布终点节点）：连啥吞啥，永久销毁。 */
public class TrashNodeItem extends MachineItem {
    public TrashNodeItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("画布终点节点：连进来的物品永久销毁").formatted(Formatting.RED));
        tooltip.add(Text.literal("配合过滤器：想要的走过滤器，其余连这里").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("永远是最后去处——同一台机器并联时，先喂别人再喂它").formatted(Formatting.DARK_GREEN));
        tooltip.add(Text.literal("只吞推送来的，不会主动从仓库抽货").formatted(Formatting.DARK_GRAY));
    }
}
