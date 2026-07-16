package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

/**
 * 物品注册。
 * Phase 0：只建并注册创造物品组「生电终结者」，作为面板物品 / 加速模块 / 控制器方块物品的容器。
 * Phase 1 起：在此注册第一个面板物品与控制器方块物品，并 .register 进本组。
 */
public class ModItems {
    public static final RegistryKey<ItemGroup> GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Sdzjz.id("main"));

    public static final ItemGroup GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(Items.CRAFTING_TABLE))
            .displayName(Text.translatable("itemGroup.sdzjz.main"))
            .build();

    public static void init() {
        Registry.register(Registries.ITEM_GROUP, GROUP_KEY, GROUP);
    }
}
