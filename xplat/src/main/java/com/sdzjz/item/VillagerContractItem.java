package com.sdzjz.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/** m144 村民合同（用户点名"用法要在合同上写清楚"）：动态段显示签约职业与折扣，
 *  用法段四步写全。数据键与 TradeCenterBlockEntity 一致（CUSTOM_DATA: prof/disc），
 *  这里直读不调那边的静态方法——tooltip 是客户端热路径，不值当拖一个方块实体类进来。 */
public class VillagerContractItem extends Item {
    public VillagerContractItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound n = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        String prof = n.getString("prof");
        int disc = n.getInt("disc");
        if (!prof.isEmpty()) {
            tooltip.add(Text.literal("已就业：").formatted(Formatting.GOLD)
                    .append(Text.translatable("entity.minecraft.villager." + Identifier.of(prof).getPath())
                            .formatted(Formatting.YELLOW)));
            tooltip.add(Text.literal("折扣 " + disc + "/5（交易输入 -" + disc * 10 + "%）")
                    .formatted(disc >= 5 ? Formatting.GREEN : Formatting.DARK_GREEN));
        } else {
            tooltip.add(Text.literal("空白合同（未就业）").formatted(Formatting.GRAY));
        }
        tooltip.add(Text.literal("① 放入村民交易所的合同槽").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("② 就业：选职业，吃存储网络里 1 个对应工作方块").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("③ 交易：材料从网络自动扣，产出自动入库（附魔书直发背包）").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("④ 治愈：每次吃 1 金苹果，折扣 +1 级（每级 -10%，满 5 级）").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("来源：村民繁殖机（面包×3/张）或超大工作台合成").formatted(Formatting.DARK_GRAY));
    }
}
