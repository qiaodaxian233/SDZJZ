package com.sdzjz.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
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
 * 装了生物的笼子将来插进结构核心，驱动对应「刷X机」产出该生物掉落。
 * Phase 1：先实现捕获本身；笼子↔机器绑定的产出逻辑下一步接。
 */
public class CaptureCageItem extends Item {
    private static final String KEY = "caged";

    public CaptureCageItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user.getWorld().isClient) return ActionResult.SUCCESS;
        if (isCaged(stack)) return ActionResult.PASS; // 已装了生物

        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        NbtCompound nbt = new NbtCompound();
        nbt.putString(KEY, id.toString());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("抓物笼子 · ").append(entity.getType().getName()));

        entity.discard();
        return ActionResult.SUCCESS;
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
