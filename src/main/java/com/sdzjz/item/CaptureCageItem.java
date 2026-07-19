package com.sdzjz.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/**
 * 抓物笼子：右键一只活体生物 → 把它「装进」笼子（记录生物类型 id 到 CUSTOM_DATA 并改名），并移除该实体。
 * 装了生物的笼子：插进结构核心画布刷该生物掉落，或作为刷怪机器的合成材料。
 *
 * 【m76 修复的两个雷】
 * 1、创造模式：原版 PlayerEntity.interact 在创造下传给 useOnEntity 的是手上物品的"复制品"，
 *    直接改 stack 参数会被丢弃 → 捕获静默失败。修法：不改参数，改 user.getStackInHand(hand) 的真实栈。
 * 2、整叠笼子：以前把生物 id 写到整叠上（3 个笼子一次全变"已捕获"），自动填料又整叠搬进一格，
 *    多重集精确匹配 ×3≠×1 → 配方不出结果。修法：只产 1 个"已捕获"笼，叠里其余保持空笼。
 */
public class CaptureCageItem extends Item {
    private static final String KEY = "caged";

    public CaptureCageItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (isCaged(stack)) return ActionResult.PASS;          // 已装了生物
        if (entity instanceof PlayerEntity) return ActionResult.PASS;      // 绝不允许"抓走"玩家
        if (entity instanceof EnderDragonEntity) return ActionResult.PASS; // 龙战实体，抓走会坏档
        if (!entity.isAlive()) return ActionResult.PASS;
        if (user.getWorld().isClient) return ActionResult.SUCCESS;

        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        // 造一个"1 只"的已捕获笼（不动传入的 stack——创造模式下那是复制品）
        ItemStack caged = new ItemStack(this, 1);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(KEY, id.toString());
        caged.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        caged.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("抓物笼子 · ").append(entity.getType().getName()));

        // 操作手上的真实栈（getStackInHand 重新取真身，创造/生存都对）
        ItemStack real = user.getStackInHand(hand);
        if (real.getItem() instanceof CaptureCageItem && !isCaged(real)) {
            if (real.getCount() <= 1) {
                user.setStackInHand(hand, caged);
            } else {
                real.decrement(1); // 叠里只消耗 1 个空笼
                if (!user.getInventory().insertStack(caged)) user.dropItem(caged, false);
            }
        } else { // 兜底（理论到不了）：直接给玩家
            if (!user.getInventory().insertStack(caged)) user.dropItem(caged, false);
        }

        user.sendMessage(Text.literal("已捕获: ").append(entity.getType().getName())
                .formatted(net.minecraft.util.Formatting.GREEN), true); // actionbar 即时反馈

        entity.discard();
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, java.util.List<Text> tooltip,
                              net.minecraft.item.tooltip.TooltipType type) {
        String id = cagedType(stack);
        if (id != null) {
            Text name;
            try { name = Registries.ENTITY_TYPE.get(Identifier.of(id)).getName(); }
            catch (Exception ex) { name = Text.literal(id); }
            tooltip.add(Text.literal("已捕获: ").append(name).formatted(net.minecraft.util.Formatting.GREEN));
            tooltip.add(Text.literal("可插画布刷掉落，或作刷怪机器的合成材料").formatted(net.minecraft.util.Formatting.GRAY));
        } else {
            tooltip.add(Text.literal("空笼 · 右键活体生物捕获").formatted(net.minecraft.util.Formatting.GRAY));
            tooltip.add(Text.literal("刷怪机器需装着对应生物的笼子才能合成").formatted(net.minecraft.util.Formatting.AQUA));
        }
    }

    public static boolean isCaged(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        return c != null && c.copyNbt().contains(KEY);
    }

    /** 取笼子里装的生物类型 id（空笼子返回 null）。 */
    public static String cagedType(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null) return null;
        NbtCompound nbt = c.copyNbt();
        return nbt.contains(KEY) ? nbt.getString(KEY) : null;
    }
}
