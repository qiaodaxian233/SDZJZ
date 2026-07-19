package com.sdzjz.machine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全自动农场的作物表：画布上点节点徽章选一种作物，产出对应掉落（免费，对齐原版农场）。
 * key = 代表物品 id（也是产出主物品）。
 */
public final class CropFarms {
    private static final Map<String, List<MachineDef.Drop>> TABLE = new LinkedHashMap<>();
    public static final List<String> KEYS;

    static {
        TABLE.put("minecraft:wheat", List.of(
                new MachineDef.Drop("minecraft:wheat", 1, 2, 1f),
                new MachineDef.Drop("minecraft:wheat_seeds", 0, 1, 0.5f)));
        TABLE.put("minecraft:carrot", List.of(new MachineDef.Drop("minecraft:carrot", 1, 3, 1f)));
        TABLE.put("minecraft:potato", List.of(
                new MachineDef.Drop("minecraft:potato", 1, 3, 1f),
                new MachineDef.Drop("minecraft:poisonous_potato", 0, 1, 0.02f)));
        TABLE.put("minecraft:beetroot", List.of(
                new MachineDef.Drop("minecraft:beetroot", 1, 2, 1f),
                new MachineDef.Drop("minecraft:beetroot_seeds", 0, 1, 0.5f)));
        TABLE.put("minecraft:melon_slice", List.of(new MachineDef.Drop("minecraft:melon_slice", 3, 7, 1f)));
        TABLE.put("minecraft:pumpkin", List.of(new MachineDef.Drop("minecraft:pumpkin", 1, 1, 1f)));
        TABLE.put("minecraft:sugar_cane", List.of(new MachineDef.Drop("minecraft:sugar_cane", 1, 2, 1f)));
        TABLE.put("minecraft:red_mushroom", List.of(
                new MachineDef.Drop("minecraft:red_mushroom", 0, 1, 0.7f),
                new MachineDef.Drop("minecraft:brown_mushroom", 0, 1, 0.7f)));
        TABLE.put("minecraft:cocoa_beans", List.of(new MachineDef.Drop("minecraft:cocoa_beans", 1, 3, 1f)));
        KEYS = List.copyOf(TABLE.keySet());
    }

    public static List<MachineDef.Drop> get(String id) { return id == null ? null : TABLE.get(id); }
    public static boolean has(String id) { return id != null && TABLE.containsKey(id); }
}
