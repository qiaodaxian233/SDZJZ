package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.item.CaptureCageItem;
import com.sdzjz.item.AutoCrafterItem;
import com.sdzjz.item.MachineItem;
import com.sdzjz.item.LinkerItem;
import com.sdzjz.item.TerminalItem;
import com.sdzjz.item.PortableVaultItem; // m311
import com.sdzjz.machine.Machines;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

/** 物品注册 + 创造物品组。 */
public class ModItems {
    // 通用件
    public static final Item CORE_MODULE      = reg("core_module", new Item(new Item.Properties()));
    public static final Item SPEED_UPGRADE    = reg("speed_upgrade", new Item(new Item.Properties()));
    public static final Item COUNT_UPGRADE    = reg("count_upgrade", new Item(new Item.Properties()));
    public static final Item PARALLEL_UPGRADE = reg("parallel_upgrade", new Item(new Item.Properties()));
    public static final Item STORAGE_UPGRADE   = reg("storage_upgrade", new Item(new Item.Properties()));
    public static final Item CAPTURE_CAGE     = reg("capture_cage", new CaptureCageItem(new Item.Properties().stacksTo(1)));
    public static final Item COMPRESSED_PACK       = reg("compressed_pack",       new com.sdzjz.item.LegacyCompressedPackItem(new Item.Properties(), 64));   // m241 方案A 一级 64:1
    public static final Item SUPER_COMPRESSED_PACK = reg("super_compressed_pack", new com.sdzjz.item.LegacyCompressedPackItem(new Item.Properties(), 4096)); // m241 方案A 二级 64²:1
    // m243 边框渲染件（压缩包动态图标的叠层素材）：只为客户端渲染注册模型/贴图，不进创造栏属设计
    public static final Item COMPRESSED_PACK_FRAME       = reg("compressed_pack_frame",       new Item(new Item.Properties()));
    public static final Item SUPER_COMPRESSED_PACK_FRAME = reg("super_compressed_pack_frame", new Item(new Item.Properties()));
    public static final Item LINKER = reg("linker", new LinkerItem(new Item.Properties().stacksTo(1)));
    public static final Item TERMINAL = reg("terminal", new TerminalItem(new Item.Properties().stacksTo(1)));
    public static final Item PORTABLE_VAULT = reg("portable_vault", new PortableVaultItem(new Item.Properties().stacksTo(1))); // m311 随身仓库（long账本+吸附）
    public static final Item LOGO = reg("logo", new Item(new Item.Properties())); // m93 创造栏标签图标(不入栏)
    public static final Item AUTO_FEEDER = reg("auto_feeder", new com.sdzjz.item.AutoFeederItem(new Item.Properties().stacksTo(1)));

    // 自动合成机（量产一切：画布上设目标，按原版配方吃料出货）
    public static final Item AUTO_CRAFTER = reg("auto_crafter", new AutoCrafterItem(new Item.Properties(), Machines.AUTO_CRAFTER));
    public static final Item BREWING_TOWER = reg("brewing_tower", new com.sdzjz.item.BrewingTowerItem(new Item.Properties(), Machines.BREWING_TOWER));
    public static final Item ENCHANT_FACTORY = reg("enchant_factory", new com.sdzjz.item.EnchantFactoryItem(new Item.Properties(), Machines.ENCHANT_FACTORY));
    public static final Item WITHER_FARM = reg("wither_farm", new MachineItem(new Item.Properties(), Machines.WITHER_FARM));
    public static final Item WITHER_KILLER = reg("wither_killer", new MachineItem(new Item.Properties(), Machines.WITHER_KILLER)); // m174 龙池杀凋机:付费快档下界之星
    public static final Item G_MISC_MACHINE = reg("g_misc_machine", new MachineItem(new Item.Properties(), Machines.G_MISC_MACHINE));
    public static final Item SCULK_LINE = reg("sculk_line", new MachineItem(new Item.Properties(), Machines.SCULK_LINE));
    public static final Item VILLAGER_DISCOUNT_MACHINE = reg("villager_discount_machine", new com.sdzjz.item.VillagerDiscountMachineItem(new Item.Properties(), Machines.VILLAGER_DISCOUNT_MACHINE));
    public static final Item VILLAGER_TRADER = reg("villager_trader", new com.sdzjz.item.VillagerTraderItem(new Item.Properties(), Machines.VILLAGER_TRADER));
    public static final Item DUPLICATOR = reg("duplicator", new com.sdzjz.item.DuplicatorItem(new Item.Properties(), Machines.DUPLICATOR)); // m334 无限复制机
    public static final Item CHUNK_REMOVER = reg("chunk_remover", new com.sdzjz.item.ChunkRemoverItem(new Item.Properties(), Machines.CHUNK_REMOVER)); // m376 区块移除器
    public static final Item CHUNK_FILTER = reg("chunk_filter", new com.sdzjz.item.ChunkFilterItem(new Item.Properties(), Machines.CHUNK_FILTER)); // m377 区块过滤器
    public static final Item VOID_PROCESSOR = reg("void_processor", new com.sdzjz.item.VoidProcessorItem(new Item.Properties(), Machines.VOID_PROCESSOR)); // m378 虚空处理器
    public static final Item CHUNK_SCANNER = reg("chunk_scanner", new com.sdzjz.item.ChunkScannerItem(new Item.Properties(), Machines.CHUNK_SCANNER)); // m380 区块扫描器
    public static final Item CHUNK_VAULT = reg("chunk_vault", new com.sdzjz.item.ChunkVaultItem(new Item.Properties(), Machines.CHUNK_VAULT)); // m381 区块储存器
    public static final Item INFINITE_BEACON = reg("infinite_beacon", new com.sdzjz.item.InfiniteBeaconItem(new Item.Properties(), Machines.INFINITE_BEACON)); // m399 无限距离信标
    public static final Item CHUNK_DATA_CORE = reg("chunk_data_core", new com.sdzjz.item.ChunkDataCoreItem(new Item.Properties().stacksTo(1))); // m381 区块数据核心（不可合成，储存器产）
    public static final Item GRINDSTONE_RECYCLER = reg("grindstone_recycler", new MachineItem(new Item.Properties(), Machines.GRINDSTONE_RECYCLER));
    public static final Item FILTER_NODE = reg("filter_node", new com.sdzjz.item.FilterNodeItem(new Item.Properties(), Machines.FILTER_NODE));
    public static final Item TRASH_NODE = reg("trash_node", new com.sdzjz.item.TrashNodeItem(new Item.Properties(), Machines.TRASH_NODE));
    public static final Item EXTRACTOR_NODE = reg("extractor_node", new com.sdzjz.item.ExtractorNodeItem(new Item.Properties(), Machines.EXTRACTOR_NODE));
    public static final Item SENSOR_NODE = reg("sensor_node", new com.sdzjz.item.SensorNodeItem(new Item.Properties(), Machines.SENSOR_NODE));
    public static final Item SWITCH_NODE = reg("switch_node", new com.sdzjz.item.SwitchNodeItem(new Item.Properties(), Machines.SWITCH_NODE));
    public static final Item DISTRIBUTOR_NODE = reg("distributor_node", new com.sdzjz.item.DistributorNodeItem(new Item.Properties(), Machines.DISTRIBUTOR_NODE));
    public static final Item CHICKEN_FARM = reg("chicken_farm", new MachineItem(new Item.Properties(), Machines.CHICKEN_FARM));
    public static final Item SHEEP_FARM   = reg("sheep_farm",   new MachineItem(new Item.Properties(), Machines.SHEEP_FARM));
    public static final Item COW_FARM     = reg("cow_farm",     new MachineItem(new Item.Properties(), Machines.COW_FARM));
    public static final Item PIG_FARM      = reg("pig_farm",      new MachineItem(new Item.Properties(), Machines.PIG_FARM)); // m92
    public static final Item ANIMAL_FARM = reg("animal_farm", new MachineItem(new Item.Properties(), Machines.ANIMAL_FARM)); // m174 五畜综合+兔子线
    public static final Item CROP_FARM    = reg("crop_farm",    new com.sdzjz.item.CropFarmItem(new Item.Properties(), Machines.CROP_FARM));
    public static final Item MEGA_CROP_FARM = reg("mega_crop_farm", new com.sdzjz.item.CropFarmItem(new Item.Properties(), Machines.MEGA_CROP_FARM)); // m173 农业塔
    public static final Item DEEP_MINING_PLATFORM = reg("deep_mining_platform", new MachineItem(new Item.Properties(), Machines.DEEP_MINING_PLATFORM)); // m102 引子:钻石x2+远古残骸x2
    public static final Item TUNNEL_BORER = reg("tunnel_borer", new MachineItem(new Item.Properties(), Machines.TUNNEL_BORER)); // m174 盾构机吃TNT出地形
    public static final Item ARCHAEOLOGY_STATION = reg("archaeology_station", new MachineItem(new Item.Properties(), Machines.ARCHAEOLOGY_STATION)); // m109a 引子:回响碎片x2+海洋之心x2
    public static final Item END_EXPEDITION_PLATFORM = reg("end_expedition_platform", new MachineItem(new Item.Properties(), Machines.END_EXPEDITION_PLATFORM)); // m109b 引子:末地石x2+龙息x2
    public static final Item DRAGON_CANNON = reg("dragon_cannon", new MachineItem(new Item.Properties(), Machines.DRAGON_CANNON)); // m174 屠龙炮:龙息+500xp/轮
    public static final Item TRIAL_FARM = reg("trial_farm", new MachineItem(new Item.Properties(), Machines.TRIAL_FARM)); // m109c 引子:试炼钥匙x2+不祥之瓶x2
    public static final Item MEGA_TRIAL_FARM = reg("mega_trial_farm", new MachineItem(new Item.Properties(), Machines.MEGA_TRIAL_FARM)); // m172 试炼大厅工程款

    // 机器（MachineItem 携带 MachineDef）
    public static final Item WIRE_BRUSHER   = reg("wire_brusher",   new MachineItem(new Item.Properties(), Machines.WIRE_BRUSHER));
    public static final Item COBBLE_MAKER   = reg("cobble_maker",   new MachineItem(new Item.Properties(), Machines.COBBLE_MAKER));
    public static final Item MEGA_COBBLE_MAKER = reg("mega_cobble_maker", new MachineItem(new Item.Properties(), Machines.MEGA_COBBLE_MAKER)); // m168 百万刷石机
    public static final Item BONE_FARM      = reg("bone_farm",      new MachineItem(new Item.Properties(), Machines.BONE_FARM));
    public static final Item GUNPOWDER_FARM = reg("gunpowder_farm", new MachineItem(new Item.Properties(), Machines.GUNPOWDER_FARM));
    public static final Item FLESH_FARM     = reg("flesh_farm",     new MachineItem(new Item.Properties(), Machines.FLESH_FARM));
    public static final Item PEARL_FARM     = reg("pearl_farm",     new MachineItem(new Item.Properties(), Machines.PEARL_FARM));
    public static final Item SLIME_FARM     = reg("slime_farm",     new MachineItem(new Item.Properties(), Machines.SLIME_FARM));
    public static final Item MEGA_SLIME_FARM = reg("mega_slime_farm", new MachineItem(new Item.Properties(), Machines.MEGA_SLIME_FARM)); // m170
    public static final Item IRON_FARM      = reg("iron_farm",      new MachineItem(new Item.Properties(), Machines.IRON_FARM));
    public static final Item MEGA_IRON_FARM = reg("mega_iron_farm", new MachineItem(new Item.Properties(), Machines.MEGA_IRON_FARM)); // m169 40核刷铁机
    public static final Item IRON_FARM_160 = reg("iron_farm_160", new MachineItem(new Item.Properties(), Machines.IRON_FARM_160)); // m172 160核刷铁机
    public static final Item TREE_FARM      = reg("tree_farm",      new MachineItem(new Item.Properties(), Machines.TREE_FARM));
    public static final Item SUGARCANE_FARM = reg("sugarcane_farm", new MachineItem(new Item.Properties(), Machines.SUGARCANE_FARM));
    public static final Item BAMBOO_FARM    = reg("bamboo_farm",    new MachineItem(new Item.Properties(), Machines.BAMBOO_FARM));
    public static final Item SAND_MAKER     = reg("sand_maker",     new MachineItem(new Item.Properties(), Machines.SAND_MAKER));
    public static final Item ICE_MAKER      = reg("ice_maker",      new MachineItem(new Item.Properties(), Machines.ICE_MAKER));
    public static final Item OBSIDIAN_MAKER = reg("obsidian_maker", new MachineItem(new Item.Properties(), Machines.OBSIDIAN_MAKER));
    public static final Item SWAMP_SPAWNER  = reg("swamp_spawner",  new MachineItem(new Item.Properties(), Machines.SWAMP_SPAWNER));
    public static final Item WITCH_TOWER    = reg("witch_tower",    new MachineItem(new Item.Properties(), Machines.WITCH_TOWER));
    public static final Item MEGA_WITCH_TOWER = reg("mega_witch_tower", new MachineItem(new Item.Properties(), Machines.MEGA_WITCH_TOWER)); // m171
    public static final Item GUARDIAN_FARM  = reg("guardian_farm",  new MachineItem(new Item.Properties(), Machines.GUARDIAN_FARM));
    public static final Item MEGA_GUARDIAN_FARM = reg("mega_guardian_farm", new MachineItem(new Item.Properties(), Machines.MEGA_GUARDIAN_FARM)); // m171
    public static final Item MAGMA_FARM     = reg("magma_farm",     new MachineItem(new Item.Properties(), Machines.MAGMA_FARM));
    public static final Item SHULKER_FARM   = reg("shulker_farm",   new MachineItem(new Item.Properties(), Machines.SHULKER_FARM));
    public static final Item RAID_TOWER     = reg("raid_tower",     new MachineItem(new Item.Properties(), Machines.RAID_TOWER));
    public static final Item MEGA_RAID_TOWER = reg("mega_raid_tower", new MachineItem(new Item.Properties(), Machines.MEGA_RAID_TOWER)); // m172 百万劫掠塔
    public static final Item PIGMAN_TOWER   = reg("pigman_tower",   new MachineItem(new Item.Properties(), Machines.PIGMAN_TOWER));
    public static final Item MEGA_PIGMAN_TOWER = reg("mega_pigman_tower", new MachineItem(new Item.Properties(), Machines.MEGA_PIGMAN_TOWER)); // m172 80万猪人塔
    public static final Item HOGLIN_FARM = reg("hoglin_farm", new MachineItem(new Item.Properties(), Machines.HOGLIN_FARM)); // m174 疣猪兽农场
    public static final Item PIGLIN_BARTER  = reg("piglin_barter",  new MachineItem(new Item.Properties(), Machines.PIGLIN_BARTER));
    public static final Item MEGA_PIGLIN_BARTER = reg("mega_piglin_barter", new MachineItem(new Item.Properties(), Machines.MEGA_PIGLIN_BARTER)); // m170
    public static final Item CACTUS_FARM = reg("cactus_farm", new MachineItem(new Item.Properties(), Machines.CACTUS_FARM));
    public static final Item NETHER_WART_FARM = reg("nether_wart_farm", new MachineItem(new Item.Properties(), Machines.NETHER_WART_FARM));
    public static final Item KELP_FARM = reg("kelp_farm", new MachineItem(new Item.Properties(), Machines.KELP_FARM));
    public static final Item BLAZE_FARM = reg("blaze_farm", new MachineItem(new Item.Properties(), Machines.BLAZE_FARM));
    public static final Item WITHER_SKELETON_FARM = reg("wither_skeleton_farm", new MachineItem(new Item.Properties(), Machines.WITHER_SKELETON_FARM));
    public static final Item MEGA_WITHER_SKELETON_FARM = reg("mega_wither_skeleton_farm", new MachineItem(new Item.Properties(), Machines.MEGA_WITHER_SKELETON_FARM)); // m172 凋骷工程款
    public static final Item WITHER_ROSE_FARM = reg("wither_rose_farm", new MachineItem(new Item.Properties(), Machines.WITHER_ROSE_FARM)); // m174 凋灵玫瑰农场
    public static final Item HONEY_FARM = reg("honey_farm", new MachineItem(new Item.Properties(), Machines.HONEY_FARM));
    public static final Item MEGA_HONEY_FARM = reg("mega_honey_farm", new MachineItem(new Item.Properties(), Machines.MEGA_HONEY_FARM)); // m172 蜜脾农场
    public static final Item IRON_SMELTER = reg("iron_smelter", new MachineItem(new Item.Properties(), Machines.IRON_SMELTER));
    public static final Item GOLD_SMELTER = reg("gold_smelter", new MachineItem(new Item.Properties(), Machines.GOLD_SMELTER));
    public static final Item CHARCOAL_KILN = reg("charcoal_kiln", new MachineItem(new Item.Properties(), Machines.CHARCOAL_KILN));
    public static final Item GLASS_KILN = reg("glass_kiln", new MachineItem(new Item.Properties(), Machines.GLASS_KILN));
    public static final Item RAIL_MACHINE = reg("rail_machine", new MachineItem(new Item.Properties(), Machines.RAIL_MACHINE));
    public static final Item CARPET_MACHINE = reg("carpet_machine", new MachineItem(new Item.Properties(), Machines.CARPET_MACHINE));
    public static final Item MOB_TOWER = reg("mob_tower", new MachineItem(new Item.Properties(), Machines.MOB_TOWER));
    public static final Item MEGA_MOB_TOWER = reg("mega_mob_tower", new MachineItem(new Item.Properties(), Machines.MEGA_MOB_TOWER)); // m170
    public static final Item NETHER_TREE_FARM = reg("nether_tree_farm", new MachineItem(new Item.Properties(), Machines.NETHER_TREE_FARM));
    public static final Item CHORUS_FARM = reg("chorus_farm", new MachineItem(new Item.Properties(), Machines.CHORUS_FARM));
    public static final Item DROWNED_TOWER = reg("drowned_tower", new MachineItem(new Item.Properties(), Machines.DROWNED_TOWER));
    public static final Item MEGA_DROWNED_TOWER = reg("mega_drowned_tower", new MachineItem(new Item.Properties(), Machines.MEGA_DROWNED_TOWER)); // m172 僵尸增援溺尸塔
    public static final Item SUPER_SMELTER = reg("super_smelter", new MachineItem(new Item.Properties(), Machines.SUPER_SMELTER));
    public static final Item MEGA_SUPER_SMELTER = reg("mega_super_smelter", new MachineItem(new Item.Properties(), Machines.MEGA_SUPER_SMELTER)); // m173 1728熔炉阵
    public static final Item GHAST_TOWER = reg("ghast_tower", new MachineItem(new Item.Properties(), Machines.GHAST_TOWER));
    public static final Item BREEZE_FARM = reg("breeze_farm", new MachineItem(new Item.Properties(), Machines.BREEZE_FARM));
    public static final Item BONEMEAL_MACHINE = reg("bonemeal_machine", new MachineItem(new Item.Properties(), Machines.BONEMEAL_MACHINE));
    public static final Item MOSS_FARM = reg("moss_farm", new MachineItem(new Item.Properties(), Machines.MOSS_FARM));
    public static final Item AMETHYST_FARM = reg("amethyst_farm", new MachineItem(new Item.Properties(), Machines.AMETHYST_FARM));
    public static final Item MEGA_AMETHYST_FARM = reg("mega_amethyst_farm", new MachineItem(new Item.Properties(), Machines.MEGA_AMETHYST_FARM)); // m172 紫水晶工程款
    public static final Item CLAY_MACHINE = reg("clay_machine", new MachineItem(new Item.Properties(), Machines.CLAY_MACHINE));
    public static final Item DRIPSTONE_FARM = reg("dripstone_farm", new MachineItem(new Item.Properties(), Machines.DRIPSTONE_FARM));
    public static final Item SNOW_MACHINE = reg("snow_machine", new MachineItem(new Item.Properties(), Machines.SNOW_MACHINE));
    public static final Item BASALT_MACHINE = reg("basalt_machine", new MachineItem(new Item.Properties(), Machines.BASALT_MACHINE));
    public static final Item FISHING_MACHINE = reg("fishing_machine", new MachineItem(new Item.Properties(), Machines.FISHING_MACHINE));
    public static final Item MEGA_FISHING_MACHINE = reg("mega_fishing_machine", new MachineItem(new Item.Properties(), Machines.MEGA_FISHING_MACHINE)); // m172 鳕鱼鲑鱼农场
    public static final Item DISC_MACHINE = reg("disc_machine", new MachineItem(new Item.Properties(), Machines.DISC_MACHINE));
    public static final Item STONECUTTER_MACHINE = reg("stonecutter_machine", new MachineItem(new Item.Properties(), Machines.STONECUTTER_MACHINE));
    public static final Item VILLAGER_CONTRACT = reg("villager_contract", new com.sdzjz.item.VillagerContractItem(new Item.Properties()));
    public static final Item VILLAGER_BREEDER = reg("villager_breeder", new MachineItem(new Item.Properties(), Machines.VILLAGER_BREEDER));

    public static final ResourceKey<CreativeModeTab> GROUP_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Sdzjz.id("main"));
    public static final CreativeModeTab GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(LOGO)) // m93 用户点名：标签图标换 MOD 红色核心
            .title(Component.translatable("itemGroup.sdzjz.main"))
            .build();

    private static Item reg(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Sdzjz.id(name), item);
    }

    public static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, GROUP_KEY, GROUP);
        ItemGroupEvents.modifyEntriesEvent(GROUP_KEY).register(entries -> {
            entries.accept(CORE_MODULE);
            entries.accept(AUTO_CRAFTER);
            entries.accept(BREWING_TOWER);
            entries.accept(ENCHANT_FACTORY);
            entries.accept(WITHER_FARM);
            entries.accept(WITHER_KILLER);
            entries.accept(G_MISC_MACHINE);
            entries.accept(SCULK_LINE);
            entries.accept(VILLAGER_DISCOUNT_MACHINE);
            entries.accept(VILLAGER_TRADER);
            entries.accept(DUPLICATOR); // m334
            entries.accept(CHUNK_REMOVER); // m376
            entries.accept(CHUNK_FILTER); // m377
            entries.accept(VOID_PROCESSOR); // m378
            entries.accept(CHUNK_SCANNER); // m380
            entries.accept(CHUNK_VAULT); // m381
            entries.accept(INFINITE_BEACON); // m399
            entries.accept(CHUNK_DATA_CORE); // m381
            entries.accept(GRINDSTONE_RECYCLER);
            entries.accept(FILTER_NODE);
            entries.accept(TRASH_NODE);
            entries.accept(EXTRACTOR_NODE);
            entries.accept(SENSOR_NODE);
            entries.accept(SWITCH_NODE);
            entries.accept(DISTRIBUTOR_NODE);
            entries.accept(CHICKEN_FARM);
            entries.accept(SHEEP_FARM);
            entries.accept(COW_FARM);
            entries.accept(PIG_FARM);
            entries.accept(ANIMAL_FARM);
            entries.accept(CROP_FARM);
            entries.accept(MEGA_CROP_FARM);
            entries.accept(DEEP_MINING_PLATFORM);
            entries.accept(TUNNEL_BORER);
            entries.accept(ARCHAEOLOGY_STATION);
            entries.accept(END_EXPEDITION_PLATFORM);
            entries.accept(DRAGON_CANNON);
            entries.accept(TRIAL_FARM);
            entries.accept(MEGA_TRIAL_FARM);
            entries.accept(SPEED_UPGRADE);
            entries.accept(COUNT_UPGRADE);
            entries.accept(PARALLEL_UPGRADE);
            entries.accept(STORAGE_UPGRADE);
            entries.accept(CAPTURE_CAGE);
            entries.accept(COMPRESSED_PACK);       // m241
            entries.accept(SUPER_COMPRESSED_PACK); // m241
            entries.accept(LINKER);
            entries.accept(TERMINAL);
            entries.accept(PORTABLE_VAULT); // m311
            entries.accept(AUTO_FEEDER);
            entries.accept(WIRE_BRUSHER);
            entries.accept(COBBLE_MAKER);
            entries.accept(MEGA_COBBLE_MAKER);
            entries.accept(BONE_FARM);
            entries.accept(GUNPOWDER_FARM);
            entries.accept(FLESH_FARM);
            entries.accept(PEARL_FARM);
            entries.accept(SLIME_FARM);
            entries.accept(MEGA_SLIME_FARM);
            entries.accept(IRON_FARM);
            entries.accept(MEGA_IRON_FARM);
            entries.accept(IRON_FARM_160);
            entries.accept(TREE_FARM);
            entries.accept(SUGARCANE_FARM);
            entries.accept(BAMBOO_FARM);
            entries.accept(SAND_MAKER);
            entries.accept(ICE_MAKER);
            entries.accept(OBSIDIAN_MAKER);
            entries.accept(SWAMP_SPAWNER);
            entries.accept(WITCH_TOWER);
            entries.accept(MEGA_WITCH_TOWER);
            entries.accept(GUARDIAN_FARM);
            entries.accept(MEGA_GUARDIAN_FARM);
            entries.accept(MAGMA_FARM);
            entries.accept(SHULKER_FARM);
            entries.accept(RAID_TOWER);
            entries.accept(MEGA_RAID_TOWER);
            entries.accept(PIGMAN_TOWER);
            entries.accept(MEGA_PIGMAN_TOWER);
            entries.accept(HOGLIN_FARM);
            entries.accept(PIGLIN_BARTER);
            entries.accept(MEGA_PIGLIN_BARTER);
            entries.accept(CACTUS_FARM);
            entries.accept(NETHER_WART_FARM);
            entries.accept(KELP_FARM);
            entries.accept(BLAZE_FARM);
            entries.accept(WITHER_SKELETON_FARM);
            entries.accept(MEGA_WITHER_SKELETON_FARM);
            entries.accept(WITHER_ROSE_FARM);
            entries.accept(HONEY_FARM);
            entries.accept(MEGA_HONEY_FARM);
            entries.accept(IRON_SMELTER);
            entries.accept(GOLD_SMELTER);
            entries.accept(CHARCOAL_KILN);
            entries.accept(GLASS_KILN);
            entries.accept(RAIL_MACHINE);
            entries.accept(CARPET_MACHINE);
            entries.accept(MOB_TOWER);
            entries.accept(MEGA_MOB_TOWER);
            entries.accept(NETHER_TREE_FARM);
            entries.accept(CHORUS_FARM);
            entries.accept(DROWNED_TOWER);
            entries.accept(MEGA_DROWNED_TOWER);
            entries.accept(SUPER_SMELTER);
            entries.accept(MEGA_SUPER_SMELTER);
            entries.accept(GHAST_TOWER);
            entries.accept(BREEZE_FARM);
            entries.accept(BONEMEAL_MACHINE);
            entries.accept(MOSS_FARM);
            entries.accept(AMETHYST_FARM);
            entries.accept(MEGA_AMETHYST_FARM);
            entries.accept(CLAY_MACHINE);
            entries.accept(DRIPSTONE_FARM);
            entries.accept(SNOW_MACHINE);
            entries.accept(BASALT_MACHINE);
            entries.accept(FISHING_MACHINE);
            entries.accept(MEGA_FISHING_MACHINE);
            entries.accept(DISC_MACHINE);
            entries.accept(STONECUTTER_MACHINE);
            entries.accept(VILLAGER_CONTRACT);
            entries.accept(VILLAGER_BREEDER);
            entries.accept(ModBlocks.STRUCTURE_CORE);
            entries.accept(ModBlocks.SUPER_BENCH);
            entries.accept(ModBlocks.DATA_PANEL);
            entries.accept(ModBlocks.STORAGE_CORE);
            entries.accept(ModBlocks.DATA_CABLE);
            entries.accept(ModBlocks.WIRELESS_NODE);
            entries.accept(ModBlocks.SATELLITE_NODE);
        });
    }
}
