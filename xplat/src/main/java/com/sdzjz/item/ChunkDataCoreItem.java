package com.sdzjz.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * m381 区块数据核心——"一个区块保存成一颗物品"的招牌凭证（不可合成，只能区块储存器产出）。
 * 引用式（方案 m379 决策1）：物品只揣 {tid 模板UUID, ox/oz/dim 原区块, tt 总数, ty 类型数}，
 * 模板本体在服务端 ChunkTemplateStore——丢核心≠丢模板（清点命令归 m383），复制核心≠复制区块
 * （同 UUID 引用同一份模板，重建照样逐份收料，无经济漏洞）。
 * 组件物品：入仓自动走精确账本（getComponentChanges 非空），输出缓存 m131b 保组件不并异组件。
 * m382 复制器装填口：背包内光标拿本核心右键点复制器物品（m311 收纳同款 onClicked 工艺，立档待实现）。
 */
public class ChunkDataCoreItem extends Item {

    public ChunkDataCoreItem(Settings settings) {
        super(settings);
    }

    /** 储存器产出唯一建器。 */
    public static ItemStack make(Item self, String uuid, int ox, int oz, String dim, long total, int types) {
        ItemStack s = new ItemStack(self);
        CompoundTag n = new CompoundTag();
        n.putString("tid", uuid);
        n.putInt("ox", ox);
        n.putInt("oz", oz);
        n.putString("dim", dim);
        n.putLong("tt", total);
        n.putInt("ty", types);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
        return s;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        CompoundTag n = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.DEFAULT).copyNbt();
        if (n.contains("tid")) {
            tooltip.add(Component.literal("源区块 (" + n.getInt("ox") + ", " + n.getInt("oz") + ") · " + n.getString("dim")).formatted(ChatFormatting.AQUA));
            tooltip.add(Component.literal("可重建方块 " + n.getLong("tt") + " · 材料 " + n.getInt("ty") + " 种").formatted(ChatFormatting.GRAY));
            tooltip.add(Component.literal("模板 " + n.getString("tid").substring(0, 8) + "…（服务端模板库）").formatted(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("空核心（未关联模板——只能由区块储存器产出）").formatted(ChatFormatting.RED));
        }
        tooltip.add(Component.literal("交给区块复制器即可异地重建（收料照模板 BOM）").formatted(ChatFormatting.LIGHT_PURPLE));
    }
}
