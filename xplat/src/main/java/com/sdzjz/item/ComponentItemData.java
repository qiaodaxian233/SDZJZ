package com.sdzjz.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** m437：{@link ItemData.Impl} 的 1.21 组件世代实现——全仓三式原样收拢，一行未改。
 *  纯 MC 类型无加载器耦合，故住 xplat（版本差=世代差不是加载器差）。 */
public final class ComponentItemData implements ItemData.Impl {

    @Override
    public CompoundTag copyOf(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    @Override
    public CompoundTag view(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
    }

    @Override
    public void write(ItemStack s, CompoundTag n) {
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
    }

    @Override
    public boolean has(ItemStack s) {
        return s.has(DataComponents.CUSTOM_DATA);
    }

    @Override
    public net.minecraft.world.item.Item itemById(String id) { // m522：SuperBenchScreenHandler/SuperBenchRecipes 原句
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id));
    }

    @Override
    public void clearCustomName(ItemStack s) { s.remove(DataComponents.CUSTOM_NAME); } // m522：原 SuperBenchScreenHandler 129 行原句

    @Override
    public net.minecraft.resources.ResourceLocation id(String s) { return net.minecraft.resources.ResourceLocation.parse(s); } // m522

    @Override
    public void clear(ItemStack s) {
        s.remove(DataComponents.CUSTOM_DATA);
    }
}
