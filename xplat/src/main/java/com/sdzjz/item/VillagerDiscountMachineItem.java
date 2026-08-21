package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/** m145 村民打折机（用户拍板：独立画布机自动治愈）。 */
public class VillagerDiscountMachineItem extends MachineItem {
    public VillagerDiscountMachineItem(Properties settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("自动治愈：吃存储网络里的金苹果").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("给共网络交易所里的已就业合同升折扣（1 苹果 = 1 级，满 5 级）").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("低折扣合同优先补短板；全部满级后待机").withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.literal("与交易所手动治愈同价——自动化不改经济账").withStyle(ChatFormatting.DARK_GRAY));
    }
}
