package com.sdzjz.storage;

import net.minecraft.world.item.ItemStack;

/**
 * m478（真移植 B 阶段第一刀）：{@link StackKey.Kind} 的 1.21 世代实现——两句**原句照搬**
 * 自合一前的 StackKey（m180 家法：方法体一字不改，回归风险按构造为零）。
 * 组件世代的身份口径=物品相同且组件补丁逐项相等，与 transfer API 的 ItemVariant 同源。
 */
public final class LegacyStackKind implements StackKey.Kind {

    @Override
    public boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b); // 原句照搬
    }

    @Override
    public int dataHash(ItemStack s) {
        return s.getComponentsPatch().hashCode(); // 原句照搬
    }
}
