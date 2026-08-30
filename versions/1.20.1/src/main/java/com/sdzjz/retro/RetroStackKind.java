package com.sdzjz.retro;

import com.sdzjz.storage.StackKey;
import net.minecraft.world.item.ItemStack;

/**
 * m478（真移植 B 阶段第一刀）：{@link StackKey.Kind} 的 1.20.1 世代实现——两句取自被删除的
 * {@code TagStackKey}（m443 原文照搬，方法体一字未改）。
 * tag 世代的身份口径=物品相同且 tag 逐字相等（原版 tagMatches 同口径，与 0.92 transfer API 的
 * ItemVariant 同源）；哈希取 tag 内容哈希（CompoundTag#hashCode 为内容哈希），无 tag 记 0——
 * 故"空 tag 在场"与"无 tag"是两个身份，与存取侧的 hasTag 分流同口径，"不混堆"闭合。
 */
final class RetroStackKind implements StackKey.Kind {

    @Override
    public boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b); // 原句照搬
    }

    @Override
    public int dataHash(ItemStack s) {
        return s.getTag() == null ? 0 : s.getTag().hashCode(); // 原句照搬
    }

    @Override
    public String dataOrder(ItemStack s) {
        return String.valueOf(s.getTag()); // m500：与 same/dataHash 同口径（本世代的附加数据就是 tag）；无 tag → "null" 恒定串，稳定即可
    }
}
