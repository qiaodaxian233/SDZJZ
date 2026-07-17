package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.item.CaptureCageItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

/** 物品注册 + 创造物品组。 */
public class ModItems {
    // ---- 物品 ----
    public static final Item CORE_MODULE     = reg("core_module", new Item(new Item.Settings()));      // 共用中间件
    public static final Item WIRE_BRUSHER    = reg("wire_brusher", new Item(new Item.Settings()));     // 刷线机（放进结构核心）
    public static final Item SPEED_UPGRADE   = reg("speed_upgrade", new Item(new Item.Settings()));    // 速度升级
    public static final Item COUNT_UPGRADE   = reg("count_upgrade", new Item(new Item.Settings()));    // 数量升级
    public static final Item PARALLEL_UPGRADE= reg("parallel_upgrade", new Item(new Item.Settings())); // 并发升级
    public static final Item CAPTURE_CAGE    = reg("capture_cage", new CaptureCageItem(new Item.Settings().maxCount(1))); // 抓物笼子

    // ---- 创造物品组 ----
    public static final RegistryKey<ItemGroup> GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Sdzjz.id("main"));
    public static final ItemGroup GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(Items.CRAFTING_TABLE))
            .displayName(Text.translatable("itemGroup.sdzjz.main"))
            .build();

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, Sdzjz.id(name), item);
    }

    public static void init() {
        Registry.register(Registries.ITEM_GROUP, GROUP_KEY, GROUP);
        ItemGroupEvents.modifyEntriesEvent(GROUP_KEY).register(entries -> {
            entries.add(CORE_MODULE);
            entries.add(WIRE_BRUSHER);
            entries.add(SPEED_UPGRADE);
            entries.add(COUNT_UPGRADE);
            entries.add(PARALLEL_UPGRADE);
            entries.add(CAPTURE_CAGE);
            entries.add(ModBlocks.STRUCTURE_CORE);
            entries.add(ModBlocks.SUPER_BENCH);
            entries.add(ModBlocks.DATA_PANEL);
        });
    }
}
