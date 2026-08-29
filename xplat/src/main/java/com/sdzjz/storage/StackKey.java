package com.sdzjz.storage;

import net.minecraft.world.item.ItemStack;

/**
 * m404 精确条目哈希键：加载器中立的栈身份键——列表仍是唯一权威与落盘格式，本键只做旁挂索引
 * （查找 O(n) 逐条深比 → O(1) 哈希，见 StorageCoreBlockEntity 的 exactIdx 注）。
 * 模板只当只读用，绝不改（m353 只读铁律）。
 *
 * <p><b>m478（真移植 B 阶段第一刀）：本类自此两代共用一份</b>——1.20.1 世代的 {@code TagStackKey}
 * （39 行仿写件）整个删除。合一前两份的实测差异只有**两句**：相等判定（1.21 组件
 * {@code isSameItemSameComponents} vs 1.20.1 tag {@code isSameItemSameTags}）与哈希取材
 * （{@code getComponentsPatch().hashCode()} vs {@code getTag().hashCode()}），已收进 {@link Kind} 世代口。
 *
 * <p><b>等价性证明（精确账本红线，两代同款论证）</b>：equals 直调各世代的"同物品+同附加数据"判定
 * （与 transfer API 的 ItemVariant 相等语义同源）；hashCode 取 <em>物品身份 + 附加数据内容哈希</em>。
 * 同物品下附加数据相等 ⟺ 其哈希相等，故 equals 相等必然 hashCode 相等，哈希契约在两代都成立。
 * 1.20.1 口径下"空 tag 在场"与"无 tag"是两个身份（原版 tagMatches 即此口径），分流判据与本键同口径，
 * "不混堆"因此闭合。
 */
public final class StackKey {

    /** m478 世代口：栈身份的相等与哈希（两代唯一的真差异）。 */
    public interface Kind {
        /** 同物品 + 同附加数据（1.21=组件逐项相等；1.20.1=tag 逐字相等）。 */
        boolean same(ItemStack a, ItemStack b);
        /** 附加数据的内容哈希（与 same 同口径：same 为真必然哈希相等）。 */
        int dataHash(ItemStack s);
    }

    private static Kind kind;

    /** 加载器入口首段调（重复安装直接炸出来，ItemData m437 / NodeTags.Ident m472 / StackCodec m477 同律）。 */
    public static void installKind(Kind k) {
        if (kind != null) throw new IllegalStateException("StackKey 身份实现重复安装");
        kind = k;
    }

    private static Kind kind() {
        if (kind == null) throw new IllegalStateException("StackKey 身份实现未安装：加载器入口须先调 StackKey.installKind(...)（1.21=Sdzjz.onInitialize 首段，1.20.1=RetroBootstrap 同位）");
        return kind;
    }

    private final ItemStack tpl; // 只当模板读，绝不改（m353 只读铁律）
    private final int hash;

    private StackKey(ItemStack tpl) {
        this.tpl = tpl;
        this.hash = System.identityHashCode(tpl.getItem()) * 31 + kind().dataHash(tpl);
    }

    public static StackKey of(ItemStack stack) { return new StackKey(stack); }

    public ItemStack template() { return tpl; }

    @Override
    public boolean equals(Object o) {
        return o instanceof StackKey k && kind().same(tpl, k.tpl);
    }

    @Override
    public int hashCode() { return hash; }

    @Override
    public String toString() { return "StackKey[" + tpl + "]"; }
}
