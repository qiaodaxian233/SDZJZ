package com.sdzjz.machine;

import java.util.List;

/** 内置机器表。单产用 def()，多掉落用 defMulti()。全为农场类（免费出）。 */
public final class Machines {
    private Machines() {}

    // ---- 自动合成机（目标动态，画布上设置；此 def 仅作占位元数据）----
    public static final MachineDef AUTO_CRAFTER = new MachineDef("auto_crafter", List.of(), 40, false, List.of());

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
    public static final MachineDef SUPER_SMELTER = defConsume("super_smelter", 2, List.of(in("minecraft:raw_iron", 1)), drop("minecraft:iron_ingot", 1, 1));

    // ---- 追加机器(m48：恶魂塔/旋风人塔/骨粉机/苔藓机/切石机) ----
    public static final MachineDef GHAST_TOWER = defMulti("ghast_tower", 35,
            drop("minecraft:gunpowder", 0, 2), drop("minecraft:ghast_tear", 0, 1, 0.1f));
    public static final MachineDef BREEZE_FARM = defMulti("breeze_farm", 30,
            drop("minecraft:breeze_rod", 1, 2), drop("minecraft:wind_charge", 0, 2, 0.3f));
    public static final MachineDef BONEMEAL_MACHINE = def("bonemeal_machine", "minecraft:bone_meal", 3, 15);
    public static final MachineDef MOSS_FARM = defMulti("moss_farm", 20,
            drop("minecraft:moss_block", 1, 2), drop("minecraft:moss_carpet", 0, 1, 0.3f));
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
