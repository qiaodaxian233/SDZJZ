package com.sdzjz.machine;

import java.util.List;

/** 内置机器表。单产用 def()，多掉落用 defMulti()。全为农场类（免费出）。 */
public final class Machines {
    private Machines() {}

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
            drop("minecraft:gold_nugget", 0, 3), drop("minecraft:rotten_flesh", 0, 2), drop("minecraft:gold_ingot", 1, 1, 0.15f));

    // ---- helpers ----
    private static MachineDef def(String id, String product, int perCycle, int interval) {
        return new MachineDef(id, List.of(new MachineDef.Drop(product, perCycle, perCycle, 1f)), interval, false, List.of());
    }

    private static MachineDef defMulti(String id, int interval, MachineDef.Drop... drops) {
        return new MachineDef(id, List.of(drops), interval, false, List.of());
    }

    private static MachineDef.Drop drop(String item, int min, int max) {
        return new MachineDef.Drop(item, min, max, 1f);
    }

    private static MachineDef.Drop drop(String item, int min, int max, float chance) {
        return new MachineDef.Drop(item, min, max, chance);
    }
}
