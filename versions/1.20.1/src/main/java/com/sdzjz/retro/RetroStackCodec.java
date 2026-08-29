package com.sdzjz.retro;

import com.sdzjz.node.CanvasGraphState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * m477（真移植 A 阶段第一刀）：{@link CanvasGraphState.StackCodec} 的 1.20.1 世代实现——
 * 两句取自被删除的 {@code CanvasGraphState120}（m454 原文照搬，方法体一字未改）：
 * 写=<code>save(new CompoundTag())</code>、读=<code>ItemStack.of(tag)</code>。
 *
 * <p>本世代无 {@code HolderLookup.Provider}（1.20.5+ 才有），故 lookup 句柄**整个忽略**——
 * 这正是「不透明代际句柄」约法的用处：共用层不需要知道有没有这东西。
 * {@code ItemStack.of} 对坏数据返回 EMPTY，与 1.21 侧 parse 落空同语义（调用方按空跳过不炸档）。
 */
final class RetroStackCodec implements CanvasGraphState.StackCodec {

    @Override
    public Tag save(ItemStack s, Object lookup) {
        return s.save(new CompoundTag()); // 1.20.1 对位 save(lookup)；句柄忽略
    }

    @Override
    public ItemStack load(CompoundTag tag, Object lookup) {
        return ItemStack.of(tag); // 1.20.1 对位 parse()；坏数据即 EMPTY
    }
}
