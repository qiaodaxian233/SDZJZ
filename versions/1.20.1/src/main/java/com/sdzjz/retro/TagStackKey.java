package com.sdzjz.retro;

import net.minecraft.world.item.ItemStack;

/**
 * m443 精确条目哈希键——1.20.1 世代版，语义蓝本=xplat 的 {@code com.sdzjz.storage.StackKey}（m404）。
 * xplat 未挂本世代（StackKey 建在 1.20.5+ 组件 API 上），按 m436 方案"身份键从组件哈希退 tag 哈希"对位新写。
 *
 * <p><b>等价性证明（精确账本红线，与蓝本同款论证）</b>：equals 直调 1.20.1 的
 * {@link ItemStack#isSameItemSameTags}（与 0.92 transfer API 的 ItemVariant 相等语义同源——
 * 都是"同物品 + tag 逐字相等"）；hashCode 取 <em>物品身份 + tag 内容哈希</em>（CompoundTag#hashCode
 * 为内容哈希）。同物品下 tag 相等 ⟺ tag 哈希相等，故 equals 相等必然 hashCode 相等，哈希契约成立。
 * 注意 1.20.1 口径下"空 tag 在场"与"无 tag"是两个身份（原版 tagMatches 即此口径）——
 * 分流（hasTag）与本键同口径，"不混堆"因此闭合。
 */
final class TagStackKey {

    private final ItemStack tpl; // 只当模板读，绝不改（m353 只读铁律的 tag 版）
    private final int hash;

    private TagStackKey(ItemStack tpl) {
        this.tpl = tpl;
        this.hash = System.identityHashCode(tpl.getItem()) * 31
                + (tpl.getTag() == null ? 0 : tpl.getTag().hashCode());
    }

    static TagStackKey of(ItemStack stack) { return new TagStackKey(stack); }

    @Override
    public boolean equals(Object o) {
        return o instanceof TagStackKey k && ItemStack.isSameItemSameTags(tpl, k.tpl);
    }

    @Override
    public int hashCode() { return hash; }

    @Override
    public String toString() { return "TagStackKey[" + tpl + "]"; }
}
