package com.sdzjz.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** m144 村民合同（用户点名"用法要在合同上写清楚"）：动态段显示签约职业与折扣，
 *  用法段四步写全。数据键与 TradeCenterBlockEntity 一致（CUSTOM_DATA: prof/disc），
 *  这里直读不调那边的静态方法——tooltip 是客户端热路径，不值当拖一个方块实体类进来。 */
public class VillagerContractItem extends Item {
    public VillagerContractItem(Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        CompoundTag n = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String prof = n.getString("prof");
        int disc = n.getInt("disc");
        if (!prof.isEmpty()) {
            tooltip.add(Component.literal("已就业：").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("entity.minecraft.villager." + ResourceLocation.parse(prof).getPath())
                            .withStyle(ChatFormatting.YELLOW)));
            tooltip.add(Component.literal("折扣 " + disc + "/5（交易输入 -" + disc * 10 + "%）")
                    .withStyle(disc >= 5 ? ChatFormatting.GREEN : ChatFormatting.DARK_GREEN));
        } else {
            tooltip.add(Component.literal("空白合同（未就业）").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("① 放入村民交易所的合同槽").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("② 就业：选职业，吃存储网络里 1 个对应工作方块").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("③ 交易：材料从网络自动扣，产出自动入库（附魔书直发背包）").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("④ 治愈：每次吃 1 金苹果，折扣 +1 级（每级 -10%，满 5 级）").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("来源：村民繁殖机（面包×3/张）或超大工作台合成").withStyle(ChatFormatting.DARK_GRAY));
    }
}
