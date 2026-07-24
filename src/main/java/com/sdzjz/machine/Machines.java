package com.sdzjz.machine;

import java.util.List;

/** 内置机器表。单产用 def()，多掉落用 defMulti()。全为农场类（免费出）。 */
public final class Machines {
    private Machines() {}

    // ---- 自动合成机（目标动态，画布上设置；此 def 仅作占位元数据）----
    public static final MachineDef AUTO_CRAFTER = new MachineDef("auto_crafter", List.of(), 40, false, List.of());
    /** 酿造塔（m131b）：目标药水在画布节点徽章选择，配方由 BrewPlanner 从原版酿造注册表解析。 */
    public static final MachineDef BREWING_TOWER = new MachineDef("brewing_tower", List.of(), 40, false, List.of());
    /** 附魔工厂（m132）：目标附魔+等级在画布节点徽章选择，成本由 EnchantPlanner 解析（经验从核心经验池扣）。 */
    public static final MachineDef ENCHANT_FACTORY = new MachineDef("enchant_factory", List.of(), 40, false, List.of());

    // ===== m135 G组杂项（原版生存精准采集也拿不到的三件）=====
    public static final MachineDef COBWEB_MACHINE = defMulti("cobweb_machine", 40,
            drop("minecraft:cobweb", 1, 2));
    public static final MachineDef SPORE_BLOSSOM_FARM = defMulti("spore_blossom_farm", 40,
            drop("minecraft:spore_blossom", 1, 1));
    public static final MachineDef BUDDING_AMETHYST_FARM = defMulti("budding_amethyst_farm", 40,
            drop("minecraft:budding_amethyst", 1, 1, 0.15f),
            drop("minecraft:small_amethyst_bud", 1, 1, 0.25f),
            drop("minecraft:medium_amethyst_bud", 1, 1, 0.25f),
            drop("minecraft:large_amethyst_bud", 1, 1, 0.25f),
            drop("minecraft:amethyst_cluster", 1, 1, 0.3f));

    // ===== m137 凋灵机+四副机（概念图五分区；均免费产出走通用分支）=====
    /** 凋灵机：主产下界之星（0.04 与残骸0.05/陶片0.04同档——boss战利品压最稀档；引子含星本体=先亲手打一次凋灵）。 */
    public static final MachineDef WITHER_FARM = defMulti("wither_farm", 40,
            drop("minecraft:nether_star", 1, 1, 0.04f));
    /** 青蛙灯机：三色青蛙灯（蛙吞小岩浆怪，色随蛙变——机器直接三色齐出，装饰件从宽）。 */
    public static final MachineDef FROGLIGHT_FARM = defMulti("froglight_farm", 40,
            drop("minecraft:ochre_froglight", 1, 2, 0.5f),
            drop("minecraft:verdant_froglight", 1, 2, 0.5f),
            drop("minecraft:pearlescent_froglight", 1, 2, 0.5f));
    /** 山羊角机：8 变体 instrument 组件随机（掉落表滚 id，组件在核心 tick 特判挂上走精确账本）。 */
    public static final MachineDef GOAT_HORN_FARM = defMulti("goat_horn_farm", 40,
            drop("minecraft:goat_horn", 1, 1, 0.25f));
    /** 犰狳鳞机：狼铠原料要量，从宽。 */
    public static final MachineDef ARMADILLO_FARM = defMulti("armadillo_farm", 40,
            drop("minecraft:armadillo_scute", 1, 2, 0.8f));
    /** 嗅探兽花园：双花种子（火把花籽/瓶子草荚——嗅探兽只能刨出种子，成株自己种）。 */
    public static final MachineDef SNIFFER_GARDEN = defMulti("sniffer_garden", 40,
            drop("minecraft:torchflower_seeds", 1, 1, 0.5f),
            drop("minecraft:pitcher_pod", 1, 1, 0.5f));

    // ===== m138 幽匿线三塔（吃核心经验池——原版幽匿=经验具象化；tick 有专属经验闸分支）=====
    /** 幽匿催化机：散块便宜量大（2经验/轮），催化体本体压稀档（原版=监守者掉一个）。 */
    public static final MachineDef SCULK_CATALYST_FARM = defMulti("sculk_catalyst_farm", 40,
            drop("minecraft:sculk", 4, 8),
            drop("minecraft:sculk_vein", 2, 4),
            drop("minecraft:sculk_catalyst", 1, 1, 0.08f));
    /** 幽匿传感机：9经验/轮对齐原版蔓延电荷量级。 */
    public static final MachineDef SCULK_SENSOR_FARM = defMulti("sculk_sensor_farm", 40,
            drop("minecraft:sculk_sensor", 1, 1, 0.6f));
    /** 幽匿尖啸机：9经验/轮；尖啸体原版不可合成、仅精准采集。 */
    public static final MachineDef SCULK_SHRIEKER_FARM = defMulti("sculk_shrieker_farm", 40,
            drop("minecraft:sculk_shrieker", 1, 1, 0.5f));
    /** 画布逻辑节点：本体不产不耗，逻辑在结构核心 tick 里（过滤分流/闸门）。 */
    public static final MachineDef FILTER_NODE = new MachineDef("filter_node", List.of(), 5, false, List.of());
    public static final MachineDef SENSOR_NODE = new MachineDef("sensor_node", List.of(), 5, false, List.of());
    public static final MachineDef SWITCH_NODE = new MachineDef("switch_node", List.of(), 5, false, List.of());
    public static final MachineDef DISTRIBUTOR_NODE = new MachineDef("distributor_node", List.of(), 5, false, List.of());
    public static final MachineDef CHICKEN_FARM = defMulti("chicken_farm", 30,
            drop("minecraft:chicken", 1, 2), drop("minecraft:feather", 0, 2, 0.6f), drop("minecraft:egg", 0, 1, 0.5f));
    public static final MachineDef SHEEP_FARM = defMulti("sheep_farm", 30,
            drop("minecraft:white_wool", 1, 2), drop("minecraft:mutton", 1, 2));
    public static final MachineDef COW_FARM = defMulti("cow_farm", 30,
            drop("minecraft:beef", 1, 3), drop("minecraft:leather", 0, 2, 0.6f));
    public static final MachineDef PIG_FARM = defMulti("pig_farm", 30,
            drop("minecraft:porkchop", 1, 2)); // m92 用户点名补缺
    /** m102 深层采掘平台（量产覆盖审计A/B组补缺）：加权多掉落——钻石慢、远古残骸更慢；
     * 残骸接万能熔炉即烧成下界合金碎片，合金锭链路就此打通。引子配方：钻石×2+远古残骸×2。 */
    public static final MachineDef DEEP_MINING_PLATFORM = defMulti("deep_mining_platform", 40,
            drop("minecraft:deepslate", 1, 3),
            drop("minecraft:tuff", 0, 2, 0.6f),
            drop("minecraft:calcite", 0, 2, 0.5f),
            drop("minecraft:red_sand", 0, 2, 0.4f),
            drop("minecraft:raw_copper", 1, 3, 0.6f),
            drop("minecraft:raw_iron", 0, 2, 0.5f),
            drop("minecraft:raw_gold", 0, 1, 0.35f),
            drop("minecraft:diamond", 1, 1, 0.15f),
            drop("minecraft:ancient_debris", 1, 1, 0.05f));
    /** m109a 考古工作站（量产覆盖提案2）：20 种考古陶片各 0.04 随机出 + 回响碎片 + 唱片残片5 +
     * 三张稀有唱片；海洋之心/附魔金苹果极低概率。引子配方：回响碎片×2+海洋之心×2（远古城+藏宝图亲手跑）。
     * 1.21 新增的 Flow/Guster/Scrape 三陶片出处是试炼密室罐子，按出处归试炼农场（m109c），不在此表。 */
    public static final MachineDef ARCHAEOLOGY_STATION = defMulti("archaeology_station", 40,
            drop("minecraft:angler_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:archer_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:arms_up_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:blade_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:brewer_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:burn_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:danger_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:explorer_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:friend_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:heart_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:heartbreak_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:howl_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:miner_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:mourner_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:plenty_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:prize_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:sheaf_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:shelter_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:skull_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:snort_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:echo_shard", 1, 2, 0.10f),
            drop("minecraft:disc_fragment_5", 1, 1, 0.06f),
            drop("minecraft:music_disc_pigstep", 1, 1, 0.02f),
            drop("minecraft:music_disc_otherside", 1, 1, 0.02f),
            drop("minecraft:music_disc_relic", 1, 1, 0.02f),
            drop("minecraft:heart_of_the_sea", 1, 1, 0.01f),
            drop("minecraft:enchanted_golden_apple", 1, 1, 0.005f));
    /** m109b 末地远征平台（量产覆盖提案3）：末地石主产（原版再生形同虚设）、龙息（免反复屠龙）、
     * 鞘翅 0.004 极低。引子配方：末地石×2+龙息×2（先亲手打一次龙拿龙息，仪式感照 m102）。 */
    public static final MachineDef END_EXPEDITION_PLATFORM = defMulti("end_expedition_platform", 40,
            drop("minecraft:end_stone", 1, 3),
            drop("minecraft:dragon_breath", 1, 1, 0.12f),
            drop("minecraft:elytra", 1, 1, 0.004f));
    /** m109c 试炼农场（量产覆盖提案4，重锤核心与试炼钥匙并入）：试炼/不祥钥匙、不祥之瓶（袭击队长掉）、
     * 1.21 三种试炼密室罐子陶片（Flow/Guster/Scrape 按出处归此表）、重锤核心 0.008 极低
     * （原版不祥宝库每玩家一次的物品，必须压到极稀）。引子配方：试炼钥匙×2+不祥之瓶×2。 */
    public static final MachineDef TRIAL_FARM = defMulti("trial_farm", 40,
            drop("minecraft:trial_key", 1, 1, 0.25f),
            drop("minecraft:ominous_trial_key", 1, 1, 0.06f),
            drop("minecraft:ominous_bottle", 1, 1, 0.15f),
            drop("minecraft:flow_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:guster_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:scrape_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:heavy_core", 1, 1, 0.008f));
    /** 全自动农场：产出按所选作物（CropFarms 表），此处仅占位定义。 */
    public static final MachineDef CROP_FARM = new MachineDef("crop_farm", List.of(), 40, false, List.of());

    // ---- 单产农场 ----
    public static final MachineDef WIRE_BRUSHER   = def("wire_brusher",   "minecraft:string",       1, 20);
    public static final MachineDef COBBLE_MAKER   = def("cobble_maker",   "minecraft:cobblestone",  1, 10);
    public static final MachineDef BONE_FARM      = def("bone_farm",      "minecraft:bone",         1, 20);
    public static final MachineDef GUNPOWDER_FARM = def("gunpowder_farm", "minecraft:gunpowder",    1, 25);
    public static final MachineDef FLESH_FARM     = def("flesh_farm",     "minecraft:rotten_flesh", 1, 20);
    public static final MachineDef PEARL_FARM     = def("pearl_farm",     "minecraft:ender_pearl",  1, 30);
    public static final MachineDef SLIME_FARM     = def("slime_farm",     "minecraft:slime_ball",   1, 25);
    public static final MachineDef IRON_FARM      = def("iron_farm",      "minecraft:iron_ingot",   1, 40);
    public static final MachineDef TREE_FARM = defMulti("tree_farm", 30,
            drop("minecraft:oak_log", 1, 2), drop("minecraft:stick", 0, 2),
            drop("minecraft:apple", 0, 1, 0.15f), drop("minecraft:oak_sapling", 0, 1, 0.4f));
    public static final MachineDef SUGARCANE_FARM = def("sugarcane_farm", "minecraft:sugar_cane",   1, 20);
    public static final MachineDef BAMBOO_FARM    = def("bamboo_farm",    "minecraft:bamboo",       1, 15);
    public static final MachineDef SAND_MAKER     = def("sand_maker",     "minecraft:sand",         1, 15);
    public static final MachineDef ICE_MAKER      = def("ice_maker",      "minecraft:ice",          1, 20);
    public static final MachineDef OBSIDIAN_MAKER = def("obsidian_maker", "minecraft:obsidian",     1, 40);

    // ---- 多掉落农场 ----
    public static final MachineDef SWAMP_SPAWNER = defMulti("swamp_spawner", 20,
            drop("minecraft:string", 0, 2), drop("minecraft:gunpowder", 0, 2), drop("minecraft:bone", 0, 2),
            drop("minecraft:arrow", 0, 2), drop("minecraft:rotten_flesh", 0, 2), drop("minecraft:spider_eye", 0, 1),
            drop("minecraft:slime_ball", 0, 1));

    public static final MachineDef WITCH_TOWER = defMulti("witch_tower", 25,
            drop("minecraft:redstone", 0, 2), drop("minecraft:glowstone_dust", 0, 2), drop("minecraft:sugar", 0, 2),
            drop("minecraft:glass_bottle", 0, 1), drop("minecraft:gunpowder", 0, 1), drop("minecraft:stick", 0, 2),
            drop("minecraft:spider_eye", 0, 1));

    public static final MachineDef GUARDIAN_FARM = defMulti("guardian_farm", 25,
            drop("minecraft:prismarine_shard", 0, 2), drop("minecraft:prismarine_crystals", 0, 1),
            drop("minecraft:cod", 0, 1));

    public static final MachineDef MAGMA_FARM    = def("magma_farm",   "minecraft:magma_cream",   1, 25);
    public static final MachineDef SHULKER_FARM  = def("shulker_farm", "minecraft:shulker_shell", 1, 60);
    public static final MachineDef RAID_TOWER = defMulti("raid_tower", 30,
            drop("minecraft:emerald", 0, 3), drop("minecraft:arrow", 0, 2), drop("minecraft:totem_of_undying", 1, 1, 0.1f));
    public static final MachineDef PIGMAN_TOWER = defMulti("pigman_tower", 25,
            drop("minecraft:gold_nugget", 1, 3), drop("minecraft:rotten_flesh", 0, 1),
            drop("minecraft:gold_ingot", 0, 1, 0.05f), drop("minecraft:golden_sword", 0, 1, 0.05f));

    // ---- 消耗类（从连接的数据面板取料）----
    public static final MachineDef PIGLIN_BARTER = defConsume("piglin_barter", 30,
            java.util.List.of(in("minecraft:gold_ingot", 1)),
            drop("minecraft:ender_pearl", 1, 1, 0.15f), drop("minecraft:string", 1, 3, 0.2f),
            drop("minecraft:quartz", 2, 4, 0.2f), drop("minecraft:glowstone_dust", 2, 4, 0.2f),
            drop("minecraft:obsidian", 1, 1, 0.1f), drop("minecraft:soul_sand", 2, 4, 0.2f),
            drop("minecraft:magma_cream", 1, 1, 0.1f), drop("minecraft:leather", 1, 1, 0.15f));

    // ---- 追加机器(m22) ----
    public static final MachineDef CACTUS_FARM = def("cactus_farm", "minecraft:cactus", 2, 20);
    public static final MachineDef NETHER_WART_FARM = def("nether_wart_farm", "minecraft:nether_wart", 1, 25);
    public static final MachineDef KELP_FARM = def("kelp_farm", "minecraft:kelp", 2, 20);
    public static final MachineDef BLAZE_FARM = def("blaze_farm", "minecraft:blaze_rod", 1, 30);
    public static final MachineDef WITHER_SKELETON_FARM = defMulti("wither_skeleton_farm", 30, drop("minecraft:bone", 1, 2), drop("minecraft:coal", 0, 1, 0.5f), drop("minecraft:wither_skeleton_skull", 0, 1, 0.025f));
    public static final MachineDef HONEY_FARM = defMulti("honey_farm", 40, drop("minecraft:honeycomb", 0, 1, 0.7f), drop("minecraft:honey_bottle", 0, 1, 0.5f));
    public static final MachineDef IRON_SMELTER = defConsume("iron_smelter", 20, List.of(in("minecraft:raw_iron", 1)), drop("minecraft:iron_ingot", 1, 1));
    public static final MachineDef GOLD_SMELTER = defConsume("gold_smelter", 20, List.of(in("minecraft:raw_gold", 1)), drop("minecraft:gold_ingot", 1, 1));
    public static final MachineDef CHARCOAL_KILN = defConsume("charcoal_kiln", 20, List.of(in("minecraft:oak_log", 1)), drop("minecraft:charcoal", 1, 1));
    public static final MachineDef GLASS_KILN = defConsume("glass_kiln", 15, List.of(in("minecraft:sand", 1)), drop("minecraft:glass", 1, 1));

    // ---- 追加机器(m31) ----
    public static final MachineDef RAIL_MACHINE = def("rail_machine", "minecraft:rail", 2, 20);
    public static final MachineDef CARPET_MACHINE = def("carpet_machine", "minecraft:white_carpet", 2, 20);
    public static final MachineDef MOB_TOWER = defMulti("mob_tower", 25, drop("minecraft:bone", 0, 2), drop("minecraft:gunpowder", 0, 1), drop("minecraft:rotten_flesh", 0, 2), drop("minecraft:string", 0, 2), drop("minecraft:arrow", 0, 1));
    public static final MachineDef NETHER_TREE_FARM = defMulti("nether_tree_farm", 30, drop("minecraft:crimson_stem", 1, 2), drop("minecraft:warped_stem", 0, 1), drop("minecraft:nether_wart_block", 0, 1, 0.2f), drop("minecraft:shroomlight", 0, 1, 0.1f));
    public static final MachineDef CHORUS_FARM = def("chorus_farm", "minecraft:chorus_fruit", 1, 30);
    public static final MachineDef DROWNED_TOWER = defMulti("drowned_tower", 30, drop("minecraft:rotten_flesh", 0, 2), drop("minecraft:copper_ingot", 0, 1, 0.5f), drop("minecraft:nautilus_shell", 0, 1, 0.03f), drop("minecraft:trident", 0, 1, 0.015f));
    /** 万能熔炉：运行时走 SmeltPlanner 原版熔炼表（接什么烧什么），此处 inputs/outputs 仅占位。 */
    public static final MachineDef SUPER_SMELTER = defConsume("super_smelter", 20, List.of(), drop("minecraft:iron_ingot", 1, 1));

    // ---- 追加机器(m48：恶魂塔/旋风人塔/骨粉机/苔藓机/切石机) ----
    public static final MachineDef GHAST_TOWER = defMulti("ghast_tower", 35,
            drop("minecraft:gunpowder", 0, 2), drop("minecraft:ghast_tear", 0, 1, 0.1f));
    public static final MachineDef BREEZE_FARM = defMulti("breeze_farm", 30,
            drop("minecraft:breeze_rod", 1, 2), drop("minecraft:wind_charge", 0, 2, 0.3f));
    public static final MachineDef BONEMEAL_MACHINE = def("bonemeal_machine", "minecraft:bone_meal", 3, 15);
    public static final MachineDef MOSS_FARM = defMulti("moss_farm", 20,
            drop("minecraft:moss_block", 1, 2), drop("minecraft:moss_carpet", 0, 1, 0.3f));

    // ===== m84a 缺口七机（量产覆盖审计 #1~#8）=====
    public static final MachineDef AMETHYST_FARM = defMulti("amethyst_farm", 40,
            drop("minecraft:amethyst_shard", 1, 3));
    public static final MachineDef CLAY_MACHINE = defMulti("clay_machine", 30,
            drop("minecraft:clay_ball", 2, 4), drop("minecraft:mud", 0, 1, 0.3f));
    public static final MachineDef DRIPSTONE_FARM = defMulti("dripstone_farm", 40,
            drop("minecraft:pointed_dripstone", 1, 2), drop("minecraft:dripstone_block", 0, 1, 0.4f));
    public static final MachineDef SNOW_MACHINE = defMulti("snow_machine", 15,
            drop("minecraft:snowball", 2, 4), drop("minecraft:snow_block", 0, 1, 0.3f));
    public static final MachineDef BASALT_MACHINE = defMulti("basalt_machine", 20,
            drop("minecraft:basalt", 2, 4));
    public static final MachineDef FISHING_MACHINE = defMulti("fishing_machine", 60,
            drop("minecraft:cod", 1, 1), drop("minecraft:salmon", 0, 1, 0.4f),
            drop("minecraft:pufferfish", 0, 1, 0.15f), drop("minecraft:nautilus_shell", 0, 1, 0.03f),
            drop("minecraft:name_tag", 0, 1, 0.01f), drop("minecraft:saddle", 0, 1, 0.01f));
    public static final MachineDef DISC_MACHINE = defMulti("disc_machine", 200,
            drop("minecraft:music_disc_13", 0, 1, 0.15f), drop("minecraft:music_disc_cat", 0, 1, 0.15f),
            drop("minecraft:music_disc_blocks", 0, 1, 0.1f), drop("minecraft:music_disc_stal", 0, 1, 0.1f),
            drop("minecraft:gunpowder", 0, 2));
    public static final MachineDef STONECUTTER_MACHINE = defConsume("stonecutter_machine", 10,
            List.of(in("minecraft:stone", 1)), drop("minecraft:stone_bricks", 1, 1));

    public static final MachineDef VILLAGER_BREEDER = defConsume("villager_breeder", 60,
            List.of(in("minecraft:bread", 3)), drop("sdzjz:villager_contract", 1, 1));

    // ---- helpers ----
    private static MachineDef def(String id, String product, int perCycle, int interval) {
        return new MachineDef(id, List.of(new MachineDef.Drop(product, perCycle, perCycle, 1f)), interval, false, List.of());
    }

    private static MachineDef defMulti(String id, int interval, MachineDef.Drop... drops) {
        return new MachineDef(id, List.of(drops), interval, false, List.of());
    }

    private static MachineDef defConsume(String id, int interval, List<MachineDef.Input> inputs, MachineDef.Drop... drops) {
        return new MachineDef(id, List.of(drops), interval, true, inputs);
    }

    private static MachineDef.Input in(String item, int count) {
        return new MachineDef.Input(item, count);
    }

    private static MachineDef.Drop drop(String item, int min, int max) {
        return new MachineDef.Drop(item, min, max, 1f);
    }

    private static MachineDef.Drop drop(String item, int min, int max, float chance) {
        return new MachineDef.Drop(item, min, max, chance);
    }
}
