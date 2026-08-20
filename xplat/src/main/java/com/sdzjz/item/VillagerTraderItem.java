package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/** m146 村民无限交易机（机身内含抓捕的村民——合成配方装笼）。 */
public class VillagerTraderItem extends MachineItem {
    public VillagerTraderItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("放入画布后，点徽章/右键菜单选择交易条目").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("材料从存储网络自取，或连线喂料（如刷线机直供）").formatted(ChatFormatting.RED));
        tooltip.add(Component.literal("产出自动入库；附魔书走精确账本，附魔不丢").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("自动享受共网交易所同职业合同的最高折扣（没合同=原价）").formatted(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.literal("交易经验 3-6/次 进核心经验池").formatted(ChatFormatting.DARK_GRAY));
    }
}
