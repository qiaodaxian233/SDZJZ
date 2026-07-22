package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.item.CaptureCageItem;
import com.sdzjz.item.AutoCrafterItem;
import com.sdzjz.item.MachineItem;
import com.sdzjz.item.LinkerItem;
import com.sdzjz.item.TerminalItem;
import com.sdzjz.machine.Machines;
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
    // 通用件
    public static final Item CORE_MODULE      = reg("core_module", new Item(new Item.Settings()));
    public static final Item SPEED_UPGRADE    = reg("speed_upgrade", new Item(new Item.Settings()));
    public static final Item COUNT_UPGRADE    = reg("count_upgrade", new Item(new Item.Settings()));
    public static final Item PARALLEL_UPGRADE = reg("parallel_upgrade", new Item(new Item.Settings()));
    public static final Item STORAGE_UPGRADE   = reg("storage_upgrade", new Item(new Item.Settings()));
    public static final Item CAPTURE_CAGE     = reg("capture_cage", new CaptureCageItem(new Item.Settings().maxCount(1)));
    public static final Item LINKER = reg("linker", new LinkerItem(new Item.Settings().maxCount(1)));
    public static final Item TERMINAL = reg("terminal", new TerminalItem(new Item.Settings().maxCount(1)));
    public static final Item LOGO = reg("logo", new Item(new Item.Settings())); // m93 创造栏标签图标(不入栏)
    public static final Item AUTO_FEEDER = reg("auto_feeder", new com.sdzjz.item.AutoFeederItem(new Item.Settings().maxCount(1)));

    // 自动合成机（量产一切：画布上设目标，按原版配方吃料出货）
    public static final Item AUTO_CRAFTER = reg("auto_crafter", new AutoCrafterItem(new Item.Settings(), Machines.AUTO_CRAFTER));
    public static final Item BREWING_TOWER = reg("brewing_tower", new com.sdzjz.item.BrewingTowerItem(new Item.Settings(), Machines.BREWING_TOWER));
    public static final Item FILTER_NODE = reg("filter_node", new com.sdzjz.item.FilterNodeItem(new Item.Settings(), Machines.FILTER_NODE));
    public static final Item SENSOR_NODE = reg("sensor_node", new com.sdzjz.item.SensorNodeItem(new Item.Settings(), Machines.SENSOR_NODE));
    public static final Item SWITCH_NODE = reg("switch_node", new com.sdzjz.item.SwitchNodeItem(new Item.Settings(), Machines.SWITCH_NODE));
    public static final Item DISTRIBUTOR_NODE = reg("distributor_node", new com.sdzjz.item.DistributorNodeItem(new Item.Settings(), Machines.DISTRIBUTOR_NODE));
    public static final Item CHICKEN_FARM = reg("chicken_farm", new MachineItem(new Item.Settings(), Machines.CHICKEN_FARM));
    public static final Item SHEEP_FARM   = reg("sheep_farm",   new MachineItem(new Item.Settings(), Machines.SHEEP_FARM));
    public static final Item COW_FARM     = reg("cow_farm",     new MachineItem(new Item.Settings(), Machines.COW_FARM));
    public static final Item PIG_FARM      = reg("pig_farm",      new MachineItem(new Item.Settings(), Machines.PIG_FARM)); // m92
    public static final Item CROP_FARM    = reg("crop_farm",    new com.sdzjz.item.CropFarmItem(new Item.Settings(), Machines.CROP_FARM));
    public static final Item DEEP_MINING_PLATFORM = reg("deep_mining_platform", new MachineItem(new Item.Settings(), Machines.DEEP_MINING_PLATFORM)); // m102 引子:钻石x2+远古残骸x2
    public static final Item ARCHAEOLOGY_STATION = reg("archaeology_station", new MachineItem(new Item.Settings(), Machines.ARCHAEOLOGY_STATION)); // m109a 引子:回响碎片x2+海洋之心x2
    public static final Item END_EXPEDITION_PLATFORM = reg("end_expedition_platform", new MachineItem(new Item.Settings(), Machines.END_EXPEDITION_PLATFORM)); // m109b 引子:末地石x2+龙息x2
    public static final Item TRIAL_FARM = reg("trial_farm", new MachineItem(new Item.Settings(), Machines.TRIAL_FARM)); // m109c 引子:试炼钥匙x2+不祥之瓶x2

    // 机器（MachineItem 携带 MachineDef）
    public static final Item WIRE_BRUSHER   = reg("wire_brusher",   new MachineItem(new Item.Settings(), Machines.WIRE_BRUSHER));
    public static final Item COBBLE_MAKER   = reg("cobble_maker",   new MachineItem(new Item.Settings(), Machines.COBBLE_MAKER));
    public static final Item BONE_FARM      = reg("bone_farm",      new MachineItem(new Item.Settings(), Machines.BONE_FARM));
    public static final Item GUNPOWDER_FARM = reg("gunpowder_farm", new MachineItem(new Item.Settings(), Machines.GUNPOWDER_FARM));
    public static final Item FLESH_FARM     = reg("flesh_farm",     new MachineItem(new Item.Settings(), Machines.FLESH_FARM));
    public static final Item PEARL_FARM     = reg("pearl_farm",     new MachineItem(new Item.Settings(), Machines.PEARL_FARM));
    public static final Item SLIME_FARM     = reg("slime_farm",     new MachineItem(new Item.Settings(), Machines.SLIME_FARM));
    public static final Item IRON_FARM      = reg("iron_farm",      new MachineItem(new Item.Settings(), Machines.IRON_FARM));
    public static final Item TREE_FARM      = reg("tree_farm",      new MachineItem(new Item.Settings(), Machines.TREE_FARM));
    public static final Item SUGARCANE_FARM = reg("sugarcane_farm", new MachineItem(new Item.Settings(), Machines.SUGARCANE_FARM));
    public static final Item BAMBOO_FARM    = reg("bamboo_farm",    new MachineItem(new Item.Settings(), Machines.BAMBOO_FARM));
    public static final Item SAND_MAKER     = reg("sand_maker",     new MachineItem(new Item.Settings(), Machines.SAND_MAKER));
    public static final Item ICE_MAKER      = reg("ice_maker",      new MachineItem(new Item.Settings(), Machines.ICE_MAKER));
    public static final Item OBSIDIAN_MAKER = reg("obsidian_maker", new MachineItem(new Item.Settings(), Machines.OBSIDIAN_MAKER));
    public static final Item SWAMP_SPAWNER  = reg("swamp_spawner",  new MachineItem(new Item.Settings(), Machines.SWAMP_SPAWNER));
    public static final Item WITCH_TOWER    = reg("witch_tower",    new MachineItem(new Item.Settings(), Machines.WITCH_TOWER));
    public static final Item GUARDIAN_FARM  = reg("guardian_farm",  new MachineItem(new Item.Settings(), Machines.GUARDIAN_FARM));
    public static final Item MAGMA_FARM     = reg("magma_farm",     new MachineItem(new Item.Settings(), Machines.MAGMA_FARM));
    public static final Item SHULKER_FARM   = reg("shulker_farm",   new MachineItem(new Item.Settings(), Machines.SHULKER_FARM));
    public static final Item RAID_TOWER     = reg("raid_tower",     new MachineItem(new Item.Settings(), Machines.RAID_TOWER));
    public static final Item PIGMAN_TOWER   = reg("pigman_tower",   new MachineItem(new Item.Settings(), Machines.PIGMAN_TOWER));
    public static final Item PIGLIN_BARTER  = reg("piglin_barter",  new MachineItem(new Item.Settings(), Machines.PIGLIN_BARTER));
    public static final Item CACTUS_FARM = reg("cactus_farm", new MachineItem(new Item.Settings(), Machines.CACTUS_FARM));
    public static final Item NETHER_WART_FARM = reg("nether_wart_farm", new MachineItem(new Item.Settings(), Machines.NETHER_WART_FARM));
    public static final Item KELP_FARM = reg("kelp_farm", new MachineItem(new Item.Settings(), Machines.KELP_FARM));
    public static final Item BLAZE_FARM = reg("blaze_farm", new MachineItem(new Item.Settings(), Machines.BLAZE_FARM));
    public static final Item WITHER_SKELETON_FARM = reg("wither_skeleton_farm", new MachineItem(new Item.Settings(), Machines.WITHER_SKELETON_FARM));
    public static final Item HONEY_FARM = reg("honey_farm", new MachineItem(new Item.Settings(), Machines.HONEY_FARM));
    public static final Item IRON_SMELTER = reg("iron_smelter", new MachineItem(new Item.Settings(), Machines.IRON_SMELTER));
    public static final Item GOLD_SMELTER = reg("gold_smelter", new MachineItem(new Item.Settings(), Machines.GOLD_SMELTER));
    public static final Item CHARCOAL_KILN = reg("charcoal_kiln", new MachineItem(new Item.Settings(), Machines.CHARCOAL_KILN));
    public static final Item GLASS_KILN = reg("glass_kiln", new MachineItem(new Item.Settings(), Machines.GLASS_KILN));
    public static final Item RAIL_MACHINE = reg("rail_machine", new MachineItem(new Item.Settings(), Machines.RAIL_MACHINE));
    public static final Item CARPET_MACHINE = reg("carpet_machine", new MachineItem(new Item.Settings(), Machines.CARPET_MACHINE));
    public static final Item MOB_TOWER = reg("mob_tower", new MachineItem(new Item.Settings(), Machines.MOB_TOWER));
    public static final Item NETHER_TREE_FARM = reg("nether_tree_farm", new MachineItem(new Item.Settings(), Machines.NETHER_TREE_FARM));
    public static final Item CHORUS_FARM = reg("chorus_farm", new MachineItem(new Item.Settings(), Machines.CHORUS_FARM));
    public static final Item DROWNED_TOWER = reg("drowned_tower", new MachineItem(new Item.Settings(), Machines.DROWNED_TOWER));
    public static final Item SUPER_SMELTER = reg("super_smelter", new MachineItem(new Item.Settings(), Machines.SUPER_SMELTER));
    public static final Item GHAST_TOWER = reg("ghast_tower", new MachineItem(new Item.Settings(), Machines.GHAST_TOWER));
    public static final Item BREEZE_FARM = reg("breeze_farm", new MachineItem(new Item.Settings(), Machines.BREEZE_FARM));
    public static final Item BONEMEAL_MACHINE = reg("bonemeal_machine", new MachineItem(new Item.Settings(), Machines.BONEMEAL_MACHINE));
    public static final Item MOSS_FARM = reg("moss_farm", new MachineItem(new Item.Settings(), Machines.MOSS_FARM));
    public static final Item AMETHYST_FARM = reg("amethyst_farm", new MachineItem(new Item.Settings(), Machines.AMETHYST_FARM));
    public static final Item CLAY_MACHINE = reg("clay_machine", new MachineItem(new Item.Settings(), Machines.CLAY_MACHINE));
    public static final Item DRIPSTONE_FARM = reg("dripstone_farm", new MachineItem(new Item.Settings(), Machines.DRIPSTONE_FARM));
    public static final Item SNOW_MACHINE = reg("snow_machine", new MachineItem(new Item.Settings(), Machines.SNOW_MACHINE));
    public static final Item BASALT_MACHINE = reg("basalt_machine", new MachineItem(new Item.Settings(), Machines.BASALT_MACHINE));
    public static final Item FISHING_MACHINE = reg("fishing_machine", new MachineItem(new Item.Settings(), Machines.FISHING_MACHINE));
    public static final Item DISC_MACHINE = reg("disc_machine", new MachineItem(new Item.Settings(), Machines.DISC_MACHINE));
    public static final Item STONECUTTER_MACHINE = reg("stonecutter_machine", new MachineItem(new Item.Settings(), Machines.STONECUTTER_MACHINE));
    public static final Item VILLAGER_CONTRACT = reg("villager_contract", new Item(new Item.Settings()));
    public static final Item VILLAGER_BREEDER = reg("villager_breeder", new MachineItem(new Item.Settings(), Machines.VILLAGER_BREEDER));

    public static final RegistryKey<ItemGroup> GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Sdzjz.id("main"));
    public static final ItemGroup GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(LOGO)) // m93 用户点名：标签图标换 MOD 红色核心
            .displayName(Text.translatable("itemGroup.sdzjz.main"))
            .build();

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, Sdzjz.id(name), item);
    }

    public static void init() {
        Registry.register(Registries.ITEM_GROUP, GROUP_KEY, GROUP);
        ItemGroupEvents.modifyEntriesEvent(GROUP_KEY).register(entries -> {
            entries.add(CORE_MODULE);
            entries.add(AUTO_CRAFTER);
            entries.add(BREWING_TOWER);
            entries.add(FILTER_NODE);
            entries.add(SENSOR_NODE);
            entries.add(SWITCH_NODE);
            entries.add(DISTRIBUTOR_NODE);
            entries.add(CHICKEN_FARM);
            entries.add(SHEEP_FARM);
            entries.add(COW_FARM);
            entries.add(PIG_FARM);
            entries.add(CROP_FARM);
            entries.add(DEEP_MINING_PLATFORM);
            entries.add(ARCHAEOLOGY_STATION);
            entries.add(END_EXPEDITION_PLATFORM);
            entries.add(TRIAL_FARM);
            entries.add(SPEED_UPGRADE);
            entries.add(COUNT_UPGRADE);
            entries.add(PARALLEL_UPGRADE);
            entries.add(STORAGE_UPGRADE);
            entries.add(CAPTURE_CAGE);
            entries.add(LINKER);
            entries.add(TERMINAL);
            entries.add(AUTO_FEEDER);
            entries.add(WIRE_BRUSHER);
            entries.add(COBBLE_MAKER);
            entries.add(BONE_FARM);
            entries.add(GUNPOWDER_FARM);
            entries.add(FLESH_FARM);
            entries.add(PEARL_FARM);
            entries.add(SLIME_FARM);
            entries.add(IRON_FARM);
            entries.add(TREE_FARM);
            entries.add(SUGARCANE_FARM);
            entries.add(BAMBOO_FARM);
            entries.add(SAND_MAKER);
            entries.add(ICE_MAKER);
            entries.add(OBSIDIAN_MAKER);
            entries.add(SWAMP_SPAWNER);
            entries.add(WITCH_TOWER);
            entries.add(GUARDIAN_FARM);
            entries.add(MAGMA_FARM);
            entries.add(SHULKER_FARM);
            entries.add(RAID_TOWER);
            entries.add(PIGMAN_TOWER);
            entries.add(PIGLIN_BARTER);
            entries.add(CACTUS_FARM);
            entries.add(NETHER_WART_FARM);
            entries.add(KELP_FARM);
            entries.add(BLAZE_FARM);
            entries.add(WITHER_SKELETON_FARM);
            entries.add(HONEY_FARM);
            entries.add(IRON_SMELTER);
            entries.add(GOLD_SMELTER);
            entries.add(CHARCOAL_KILN);
            entries.add(GLASS_KILN);
            entries.add(RAIL_MACHINE);
            entries.add(CARPET_MACHINE);
            entries.add(MOB_TOWER);
            entries.add(NETHER_TREE_FARM);
            entries.add(CHORUS_FARM);
            entries.add(DROWNED_TOWER);
            entries.add(SUPER_SMELTER);
            entries.add(GHAST_TOWER);
            entries.add(BREEZE_FARM);
            entries.add(BONEMEAL_MACHINE);
            entries.add(MOSS_FARM);
            entries.add(AMETHYST_FARM);
            entries.add(CLAY_MACHINE);
            entries.add(DRIPSTONE_FARM);
            entries.add(SNOW_MACHINE);
            entries.add(BASALT_MACHINE);
            entries.add(FISHING_MACHINE);
            entries.add(DISC_MACHINE);
            entries.add(STONECUTTER_MACHINE);
            entries.add(VILLAGER_CONTRACT);
            entries.add(VILLAGER_BREEDER);
            entries.add(ModBlocks.STRUCTURE_CORE);
            entries.add(ModBlocks.SUPER_BENCH);
            entries.add(ModBlocks.DATA_PANEL);
            entries.add(ModBlocks.STORAGE_CORE);
            entries.add(ModBlocks.DATA_CABLE);
            entries.add(ModBlocks.WIRELESS_NODE);
            entries.add(ModBlocks.SATELLITE_NODE);
        });
    }
}
