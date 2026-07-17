package com.sdzjz.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 超大工作台合成表：无形状(多重集精确匹配)。由机器配方 JSON 自动生成。 */
public final class SuperBenchRecipes {
    public record Recipe(Map<String, Integer> ingredients, String result) {}
    public static final List<Recipe> ALL = new ArrayList<>();
    static {
        add("sdzjz:bamboo_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:bamboo", 1);
        add("sdzjz:blaze_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:blaze_rod", 1);
        add("sdzjz:bone_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:bone", 1);
        add("sdzjz:cactus_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:cactus", 1);
        add("sdzjz:carpet_machine", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:white_carpet", 1);
        add("sdzjz:charcoal_kiln", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:charcoal", 1);
        add("sdzjz:chorus_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:chorus_fruit", 1);
        add("sdzjz:cobble_maker", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:cobblestone", 1);
        add("sdzjz:drowned_tower", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 3);
        add("sdzjz:flesh_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:rotten_flesh", 1);
        add("sdzjz:glass_kiln", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:glass", 1);
        add("sdzjz:gold_smelter", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:raw_gold", 1);
        add("sdzjz:guardian_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:prismarine_shard", 1);
        add("sdzjz:gunpowder_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:gunpowder", 1);
        add("sdzjz:honey_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:honeycomb", 1);
        add("sdzjz:ice_maker", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:ice", 1);
        add("sdzjz:iron_farm", "minecraft:iron_ingot", 3, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2);
        add("sdzjz:iron_smelter", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:raw_iron", 1);
        add("sdzjz:kelp_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:kelp", 1);
        add("sdzjz:magma_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:magma_cream", 1);
        add("sdzjz:mob_tower", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:bone", 1);
        add("sdzjz:nether_tree_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:crimson_stem", 1);
        add("sdzjz:nether_wart_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:nether_wart", 1);
        add("sdzjz:obsidian_maker", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:obsidian", 1);
        add("sdzjz:pearl_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:ender_pearl", 1);
        add("sdzjz:piglin_barter", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:gold_ingot", 1);
        add("sdzjz:pigman_tower", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:gold_nugget", 1);
        add("sdzjz:raid_tower", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:emerald", 1);
        add("sdzjz:rail_machine", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:rail", 1);
        add("sdzjz:sand_maker", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:sand", 1);
        add("sdzjz:shulker_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:shulker_shell", 1);
        add("sdzjz:slime_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:slime_ball", 1);
        add("sdzjz:sugarcane_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:sugar_cane", 1);
        add("sdzjz:super_smelter", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:raw_iron", 1);
        add("sdzjz:swamp_spawner", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:rotten_flesh", 1);
        add("sdzjz:tree_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:oak_log", 1);
        add("sdzjz:wire_brusher", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:string", 1);
        add("sdzjz:witch_tower", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:glowstone_dust", 1);
        add("sdzjz:wither_skeleton_farm", "minecraft:iron_ingot", 2, "minecraft:observer", 1, "minecraft:redstone", 2, "sdzjz:core_module", 1, "minecraft:copper_ingot", 2, "minecraft:bone", 1);
    }

    private static void add(String result, Object... kv) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.merge((String) kv[i], (Integer) kv[i + 1], Integer::sum);
        ALL.add(new Recipe(m, result));
    }

    /** 网格多重集精确匹配到配方。 */
    public static Recipe match(Map<String, Integer> grid) {
        if (grid.isEmpty()) return null;
        for (Recipe r : ALL) if (r.ingredients.equals(grid)) return r;
        return null;
    }

    public static ItemStack resultStack(Recipe r) {
        return r == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(Identifier.of(r.result)));
    }
}
