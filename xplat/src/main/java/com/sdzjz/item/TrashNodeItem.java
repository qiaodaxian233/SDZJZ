package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/** m150 垃圾桶节点（画布终点节点）：连啥吞啥，永久销毁。 */
public class TrashNodeItem extends MachineItem {
    public TrashNodeItem(Properties settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("画布终点节点：连进来的物品永久销毁").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("配合过滤器：想要的走过滤器，其余连这里").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("永远是最后去处——同一台机器并联时，先喂别人再喂它").withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.literal("只吞推送来的，不会主动从仓库抽货").withStyle(ChatFormatting.DARK_GRAY));
    }
}
