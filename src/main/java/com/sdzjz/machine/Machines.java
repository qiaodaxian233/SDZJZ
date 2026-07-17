package com.sdzjz.machine;

import java.util.List;

/**
 * 内置机器表（Phase 2 批量：农场类，consumesInputs=false 免费出，对齐原版刷怪/采集农场）。
 * 加工/合成类（consumesInputs=true）下一步做。加机器：这里加一条 + ModItems 注册 MachineItem + 配方/模型/lang。
 */
public final class Machines {
    private Machines() {}

    //                                     id              product                 每次  周期t 消耗  输入
    public static final MachineDef WIRE_BRUSHER   = def("wire_brusher",   "minecraft:string",       1, 20);
    public static final MachineDef COBBLE_MAKER   = def("cobble_maker",   "minecraft:cobblestone",  1, 10);
    public static final MachineDef BONE_FARM      = def("bone_farm",      "minecraft:bone",         1, 20);
    public static final MachineDef GUNPOWDER_FARM = def("gunpowder_farm", "minecraft:gunpowder",    1, 25);
    public static final MachineDef FLESH_FARM     = def("flesh_farm",     "minecraft:rotten_flesh", 1, 20);
    public static final MachineDef PEARL_FARM     = def("pearl_farm",     "minecraft:ender_pearl",  1, 30);
    public static final MachineDef SLIME_FARM     = def("slime_farm",     "minecraft:slime_ball",   1, 25);
    public static final MachineDef IRON_FARM      = def("iron_farm",      "minecraft:iron_ingot",   1, 40);
    public static final MachineDef TREE_FARM      = def("tree_farm",      "minecraft:oak_log",      1, 30);
    public static final MachineDef SUGARCANE_FARM = def("sugarcane_farm", "minecraft:sugar_cane",   1, 20);
    public static final MachineDef BAMBOO_FARM    = def("bamboo_farm",    "minecraft:bamboo",       1, 15);
    public static final MachineDef SAND_MAKER     = def("sand_maker",     "minecraft:sand",         1, 15);
    public static final MachineDef ICE_MAKER      = def("ice_maker",      "minecraft:ice",          1, 20);
    public static final MachineDef OBSIDIAN_MAKER = def("obsidian_maker", "minecraft:obsidian",     1, 40);

    private static MachineDef def(String id, String product, int perCycle, int interval) {
        return new MachineDef(id, product, perCycle, interval, false, List.of());
    }
}
