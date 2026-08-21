package com.sdzjz.storage;

import net.minecraft.world.item.ItemStack;

/**
 * m404 精确条目哈希键（物品 + 组件）——加载器中立版，替掉此前借 Fabric 的 {@code ItemVariant} 当键的两处。
 *
 * <p><b>为什么不继续借 ItemVariant</b>：那两处（m295 精确账本索引、m130/m267 面板跨核心合并）
 * 用的根本不是"传输"能力，只是要一个"物品+组件"的哈希键——为一个纯数据结构的需求绑住加载器，
 * 换 NeoForge 时就得连业务逻辑一起改。现在只用原版类型，逻辑可原样上 xplat 层。
 *
 * <p><b>等价性证明（这是精确账本的红线，必须写清）</b>：
 * equals 直接调 {@link ItemStack#areItemsAndComponentsEqual}（与 ItemVariant 的相等语义、
 * 与账本此前的逐条深比语义三者同源）；hashCode 取 <em>物品身份 + 组件增量</em>——
 * 同一物品下"完整组件表相等 ⟺ 组件增量相等"（完整表=默认值⊕增量，默认值由物品唯一决定），
 * 故 equals 相等必然 hashCode 相等，哈希契约成立。
 */
public final class StackKey {

    private final ItemStack tpl; // 只当模板读，绝不改（m353 只读铁律）
    private final int hash;

    private StackKey(ItemStack tpl) {
        this.tpl = tpl;
        this.hash = System.identityHashCode(tpl.getItem()) * 31 + tpl.getComponentsPatch().hashCode();
    }

    public static StackKey of(ItemStack stack) { return new StackKey(stack); }

    /** 键背后的模板（只读；要改先 copy）。 */
    public ItemStack template() { return tpl; }

    @Override
    public boolean equals(Object o) {
        return o instanceof StackKey k && ItemStack.isSameItemSameComponents(tpl, k.tpl);
    }

    @Override
    public int hashCode() { return hash; }

    @Override
    public String toString() { return "StackKey[" + tpl + "]"; }
}
