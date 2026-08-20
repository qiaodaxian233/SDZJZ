package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** m145 村民打折机（用户拍板：独立画布机自动治愈）。 */
public class VillagerDiscountMachineItem extends MachineItem {
    public VillagerDiscountMachineItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("自动治愈：吃存储网络里的金苹果").formatted(Formatting.RED));
        tooltip.add(Text.literal("给共网络交易所里的已就业合同升折扣（1 苹果 = 1 级，满 5 级）").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("低折扣合同优先补短板；全部满级后待机").formatted(Formatting.DARK_GREEN));
        tooltip.add(Text.literal("与交易所手动治愈同价——自动化不改经济账").formatted(Formatting.DARK_GRAY));
    }
}
