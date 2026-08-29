package com.sdzjz.node;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * m477（真移植 A 阶段第一刀）：{@link CanvasGraphState.StackCodec} 的 1.21 世代实现——
 * 原 {@code s.save(lookup)} 与 {@code ItemStack.parse(lookup, tag)} 两句**原句照搬**
 * （m180 家法：方法体一字不改，回归风险按构造为零；m468 风险②「新增走法+旧走法原位保留同值」
 * 的落点就是本类）。
 *
 * <p>lookup 是不透明代际句柄（本世代=HolderLookup.Provider，实为 {@code registryAccess()}），
 * 共用层只透传不触碰，转型在这里做一次——与 {@code platform.RecipeAccess} 的 level 句柄同一约法。
 * 空句柄防御：主线全部调用点都带 lookup，为 null 只可能是接线错误，此时按坏数据处理返回 EMPTY，
 * 不让一次接线失误炸掉整个存档读入。
 */
public final class LegacyStackCodec implements CanvasGraphState.StackCodec {

    @Override
    public Tag save(ItemStack s, Object lookup) {
        return s.save((HolderLookup.Provider) lookup); // 原句照搬
    }

    @Override
    public ItemStack load(CompoundTag tag, Object lookup) {
        if (!(lookup instanceof HolderLookup.Provider p)) return ItemStack.EMPTY;
        return ItemStack.parse(p, tag).orElse(ItemStack.EMPTY); // 原 parse().ifPresent 的同值形（空=调用方跳过）
    }
}
