package com.sdzjz.retro;

import com.sdzjz.item.ItemData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * m451（C2-①）：ItemData 门面的 1.20.1 世代实现——m436 方案稿承诺的双实现第二半兑现
 * （"1.20.1 届时写一个 TagItemData 对位即可"），五口对位表照稿逐字落地。物品附加数据的
 * 版本差自此钉死在 ComponentItemData（1.21 组件三式）与本类（1.20.1 tag 三式）两个文件。
 *
 * <p><b>共享空表红线（m353 谱系）</b>：view 无数据返回全局共享空表 EMPTY——绝对只读，
 * 写它=全服中毒（Legacy NbtComponent.DEFAULT 同款语义，蓝本注释原句随迁）。
 * <b>write 语义零改动</b>：对位原生 setTag——空表在场与无 tag 是两个身份（m443 类注①同口径），
 * "write(空表)=清除"归一按 m437 记档另立项，本世代同样不动；显式清除走 clear=setTag(null)。
 */
final class TagItemData implements ItemData.Impl {

    /** 全局共享空表：只读铁律见类注。 */
    private static final CompoundTag EMPTY = new CompoundTag();

    @Override
    public CompoundTag copyOf(ItemStack s) {
        return s.hasTag() ? s.getTag().copy() : new CompoundTag();
    }

    @Override
    public CompoundTag view(ItemStack s) {
        return s.hasTag() ? s.getTag() : EMPTY;
    }

    @Override
    public void write(ItemStack s, CompoundTag n) {
        s.setTag(n);
    }

    @Override
    public boolean has(ItemStack s) {
        return s.hasTag();
    }

    @Override
    public net.minecraft.world.item.Item itemById(String id) { // m522：1.20.1 对位（对照表：parse→new ResourceLocation）
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(id));
    }

    @Override
    public void clearCustomName(ItemStack s) { s.resetHoverName(); } // m522：1.20.1 对位（1.21 remove(CUSTOM_NAME)）

    @Override
    public net.minecraft.resources.ResourceLocation id(String s) { return new net.minecraft.resources.ResourceLocation(s); } // m522

    @Override
    public void clear(ItemStack s) {
        s.setTag(null); // 对位 remove(CUSTOM_DATA)，m128"变裸"语义
    }
}
