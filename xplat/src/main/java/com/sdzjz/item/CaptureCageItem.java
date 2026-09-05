package com.sdzjz.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;

/**
 * 抓物笼子：右键一只活体生物 → 把它「装进」笼子（记录生物类型 id 到 CUSTOM_DATA 并改名），并移除该实体。
 * 装了生物的笼子：插进结构核心画布刷该生物掉落，或作为刷怪机器的合成材料。
 *
 * 【m76 修复的两个雷】
 * 1、创造模式：原版 Player.interact 在创造下传给 useOnEntity 的是手上物品的"复制品"，
 *    直接改 stack 参数会被丢弃 → 捕获静默失败。修法：不改参数，改 user.getStackInHand(hand) 的真实栈。
 * 2、整叠笼子：以前把生物 id 写到整叠上（3 个笼子一次全变"已捕获"），自动填料又整叠搬进一格，
 *    多重集精确匹配 ×3≠×1 → 配方不出结果。修法：只产 1 个"已捕获"笼，叠里其余保持空笼。
 */
public abstract class CaptureCageItem extends Item {
    private static final String KEY = "caged";

    public CaptureCageItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        // 兜底路径：无右键交互的生物（僵尸/骷髅等）仍会走到这里；
        // 有交互的生物（村民/马/宠物）由 Sdzjz 里注册的 UseEntityCallback 抢先处理（m94）。
        return tryCapture(user, hand, entity);
    }

    /**
     * 捕获核心（m94 抽出为静态，供 UseEntityCallback 与 useOnEntity 共用）。
     * 背景：原版 Player.interact 先调 entity.interact()——村民会先弹交易界面并"吃掉"这次交互，
     * useOnEntity 永远轮不到执行；马/驯服猫狗同理。事件挂在交易之前，返回 SUCCESS 即取消原版处理。
     */
    public static InteractionResult tryCapture(Player user, InteractionHand hand, LivingEntity entity) {
        ItemStack held = user.getItemInHand(hand);
        if (!(held.getItem() instanceof CaptureCageItem cageItem)) return InteractionResult.PASS;
        if (isCaged(held)) return InteractionResult.PASS;           // 已装了生物
        if (user.isSpectator()) return InteractionResult.PASS;      // 事件挂在旁观检查之前，须自查（Fabric 文档明示）
        if (entity instanceof Player) return InteractionResult.PASS;      // 绝不允许"抓走"玩家
        if (entity instanceof EnderDragon) return InteractionResult.PASS; // 龙战实体，抓走会坏档
        if (!entity.isAlive()) return InteractionResult.PASS;
        if (user.level().isClientSide) return InteractionResult.SUCCESS;

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        // 造一个"1 只"的已捕获笼（不动传入的 stack——创造模式下那是复制品）
        ItemStack caged = new ItemStack(cageItem, 1);
        CompoundTag nbt = new CompoundTag();
        nbt.putString(KEY, id.toString());
        com.sdzjz.item.ItemData.write(caged, nbt);
        com.sdzjz.item.ItemData.setCustomName(caged, // m530：原 caged.set(DataComponents.CUSTOM_NAME, …)，走 ItemData 世代口
                Component.literal("抓物笼子 · ").append(entity.getType().getDescription()));

        // 操作手上的真实栈（getStackInHand 重新取真身，创造/生存都对）
        ItemStack real = user.getItemInHand(hand);
        if (real.getItem() instanceof CaptureCageItem && !isCaged(real)) {
            if (real.getCount() <= 1) {
                user.setItemInHand(hand, caged);
            } else {
                real.shrink(1); // 叠里只消耗 1 个空笼
                if (!user.getInventory().add(caged)) user.drop(caged, false);
            }
        } else { // 兜底（理论到不了）：直接给玩家
            if (!user.getInventory().add(caged)) user.drop(caged, false);
        }

        user.displayClientMessage(Component.literal("已捕获: ").append(entity.getType().getDescription())
                .withStyle(net.minecraft.ChatFormatting.GREEN), true); // actionbar 即时反馈

        entity.discard();
        return InteractionResult.SUCCESS;
    }

    /** m530（N2b）：原 {@code appendHoverText} 体原文——签名两代不同（1.21 TooltipContext / 1.20.1 Level），覆写留世代壳
     *  （主线 {@code LegacyCaptureCageItem} / 1.20.1 {@code CaptureCage120}）一句转调；本类因此 abstract（N2a 压缩包同律）。 */
    public void hoverLines(ItemStack stack, java.util.List<Component> tooltip) {
        String id = cagedType(stack);
        if (id != null) {
            Component name;
            try { name = BuiltInRegistries.ENTITY_TYPE.get(com.sdzjz.item.ItemData.id(id)).getDescription(); } // m530：原 ResourceLocation.parse(id)，走 m522 口
            catch (Exception ex) { name = Component.literal(id); }
            tooltip.add(Component.literal("已捕获: ").append(name).withStyle(net.minecraft.ChatFormatting.GREEN));
            tooltip.add(Component.literal("可插画布刷掉落，或作刷怪机器的合成材料").withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("空笼 · 右键活体生物捕获").withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(Component.literal("刷怪机器需装着对应生物的笼子才能合成").withStyle(net.minecraft.ChatFormatting.AQUA));
        }
    }

    public static boolean isCaged(ItemStack stack) {
        return com.sdzjz.item.ItemData.view(stack).contains(KEY);
    }

    /** 取笼子里装的生物类型 id（空笼子返回 null）。 */
    public static String cagedType(ItemStack stack) {
        CompoundTag nbt = com.sdzjz.item.ItemData.view(stack); // 只读，缺数据=空表走 null 路
        return nbt.contains(KEY) ? nbt.getString(KEY) : null;
    }
}
