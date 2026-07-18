package com.sdzjz.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 超大工作台合成表。每台机器一张固定 12×12 蓝图布局（边框/角螺栓/玻璃窗/红石线/核心节点/中央标志物）。
 * 匹配仍走多重集（位置无关，手动摆料也友好）；自动填充按 layout 指定位置铺满。
 * 布局由共享模板 + 每台 4 格标志物生成，标志物保证每台唯一。
 */
public final class SuperBenchRecipes {
    public static final int GRID = 12;
    public static final int SLOTS = 144;

    /** 12×12 模板：I=铁 C=铜 R=红石 G=玻璃 O=侦测器 M=核心模块 S=标志物 .=空 */
    static final String TEMPLATE =
        "IIIICCCCIIIIIOGR....RGOIIG........GIIR.M....M.RIC....GG....CC...GSSG...CC...GSSG...CC....GG....CIR.M....M.RIIG........GIIOGR....RGOIIIIICCCCIIII";
    static final Map<Character, String> LEGEND = new HashMap<>();
    static {
        LEGEND.put('I', "minecraft:iron_ingot");
        LEGEND.put('C', "minecraft:copper_ingot");
        LEGEND.put('R', "minecraft:redstone");
        LEGEND.put('G', "minecraft:glass");
        LEGEND.put('O', "minecraft:observer");
        LEGEND.put('M', "sdzjz:core_module");
    }

    /** layout[i] = 该格物品 id（null=空）。ingredients 为多重集用量。 */
    public record Recipe(String result, String[] layout, Map<String, Integer> ingredients) {}
    public static final List<Recipe> ALL = new ArrayList<>();

    static {
        add("sdzjz:bamboo_farm", "minecraft:bamboo", "minecraft:bamboo", "minecraft:bamboo", "minecraft:bamboo");
        add("sdzjz:blaze_farm", "minecraft:blaze_rod", "minecraft:blaze_rod", "minecraft:blaze_powder", "minecraft:blaze_powder");
        add("sdzjz:ghast_tower", "minecraft:ghast_tear", "minecraft:ghast_tear", "minecraft:gunpowder", "minecraft:gunpowder");
        add("sdzjz:breeze_farm", "minecraft:breeze_rod", "minecraft:breeze_rod", "minecraft:wind_charge", "minecraft:wind_charge");
        add("sdzjz:bonemeal_machine", "minecraft:bone_meal", "minecraft:bone_meal", "minecraft:bone_meal", "minecraft:moss_block");
        add("sdzjz:moss_farm", "minecraft:moss_block", "minecraft:moss_block", "minecraft:moss_block", "minecraft:moss_carpet");
        add("sdzjz:stonecutter_machine", "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone");
        add("sdzjz:villager_breeder", "minecraft:bread", "minecraft:bread", "minecraft:bread", "minecraft:emerald");
        add("sdzjz:bone_farm", "minecraft:bone", "minecraft:bone", "minecraft:bone", "minecraft:arrow");
        add("sdzjz:cactus_farm", "minecraft:cactus", "minecraft:cactus", "minecraft:cactus", "minecraft:green_dye");
        add("sdzjz:carpet_machine", "minecraft:white_carpet", "minecraft:white_carpet", "minecraft:white_wool", "minecraft:white_wool");
        add("sdzjz:charcoal_kiln", "minecraft:charcoal", "minecraft:charcoal", "minecraft:charcoal", "minecraft:oak_log");
        add("sdzjz:chorus_farm", "minecraft:chorus_fruit", "minecraft:chorus_fruit", "minecraft:chorus_fruit", "minecraft:popped_chorus_fruit");
        add("sdzjz:cobble_maker", "minecraft:cobblestone", "minecraft:cobblestone", "minecraft:cobblestone", "minecraft:stone");
        add("sdzjz:drowned_tower", "minecraft:copper_ingot", "minecraft:copper_ingot", "minecraft:prismarine_shard", "minecraft:rotten_flesh");
        add("sdzjz:flesh_farm", "minecraft:rotten_flesh", "minecraft:rotten_flesh", "minecraft:rotten_flesh", "minecraft:rotten_flesh");
        add("sdzjz:glass_kiln", "minecraft:glass", "minecraft:glass", "minecraft:sand", "minecraft:sand");
        add("sdzjz:gold_smelter", "minecraft:raw_gold", "minecraft:raw_gold", "minecraft:raw_gold", "minecraft:gold_ingot");
        add("sdzjz:guardian_farm", "minecraft:prismarine_shard", "minecraft:prismarine_shard", "minecraft:prismarine_crystals", "minecraft:prismarine_crystals");
        add("sdzjz:gunpowder_farm", "minecraft:gunpowder", "minecraft:gunpowder", "minecraft:gunpowder", "minecraft:tnt");
        add("sdzjz:honey_farm", "minecraft:honeycomb", "minecraft:honeycomb", "minecraft:honeycomb", "minecraft:honey_bottle");
        add("sdzjz:ice_maker", "minecraft:ice", "minecraft:ice", "minecraft:ice", "minecraft:snowball");
        add("sdzjz:iron_farm", "minecraft:poppy", "minecraft:poppy", "minecraft:iron_ingot", "minecraft:iron_ingot");
        add("sdzjz:iron_smelter", "minecraft:raw_iron", "minecraft:raw_iron", "minecraft:raw_iron", "minecraft:iron_ingot");
        add("sdzjz:kelp_farm", "minecraft:kelp", "minecraft:kelp", "minecraft:kelp", "minecraft:dried_kelp");
        add("sdzjz:magma_farm", "minecraft:magma_cream", "minecraft:magma_cream", "minecraft:magma_cream", "minecraft:magma_block");
        add("sdzjz:mob_tower", "minecraft:bone", "minecraft:gunpowder", "minecraft:string", "minecraft:arrow");
        add("sdzjz:nether_tree_farm", "minecraft:crimson_stem", "minecraft:crimson_stem", "minecraft:warped_stem", "minecraft:crimson_fungus");
        add("sdzjz:nether_wart_farm", "minecraft:nether_wart", "minecraft:nether_wart", "minecraft:nether_wart", "minecraft:soul_sand");
        add("sdzjz:obsidian_maker", "minecraft:obsidian", "minecraft:obsidian", "minecraft:obsidian", "minecraft:crying_obsidian");
        add("sdzjz:pearl_farm", "minecraft:ender_pearl", "minecraft:ender_pearl", "minecraft:ender_pearl", "minecraft:obsidian");
        add("sdzjz:piglin_barter", "minecraft:gold_ingot", "minecraft:gold_ingot", "minecraft:gold_ingot", "minecraft:obsidian");
        add("sdzjz:pigman_tower", "minecraft:gold_nugget", "minecraft:gold_nugget", "minecraft:gold_nugget", "minecraft:gold_ingot");
        add("sdzjz:raid_tower", "minecraft:emerald", "minecraft:emerald", "minecraft:emerald", "minecraft:emerald");
        add("sdzjz:rail_machine", "minecraft:rail", "minecraft:rail", "minecraft:iron_ingot", "minecraft:stick");
        add("sdzjz:sand_maker", "minecraft:sand", "minecraft:sand", "minecraft:sand", "minecraft:gravel");
        add("sdzjz:shulker_farm", "minecraft:shulker_shell", "minecraft:shulker_shell", "minecraft:purpur_block", "minecraft:purpur_block");
        add("sdzjz:slime_farm", "minecraft:slime_ball", "minecraft:slime_ball", "minecraft:slime_ball", "minecraft:slime_ball");
        add("sdzjz:sugarcane_farm", "minecraft:sugar_cane", "minecraft:sugar_cane", "minecraft:sugar_cane", "minecraft:paper");
        add("sdzjz:super_smelter", "minecraft:raw_iron", "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_gold");
        add("sdzjz:swamp_spawner", "minecraft:rotten_flesh", "minecraft:bone", "minecraft:string", "minecraft:slime_ball");
        add("sdzjz:tree_farm", "minecraft:oak_log", "minecraft:oak_log", "minecraft:oak_sapling", "minecraft:apple");
        add("sdzjz:wire_brusher", "minecraft:string", "minecraft:string", "minecraft:string", "minecraft:cobweb");
        add("sdzjz:witch_tower", "minecraft:glowstone_dust", "minecraft:glowstone_dust", "minecraft:spider_eye", "minecraft:sugar");
        add("sdzjz:wither_skeleton_farm", "minecraft:bone", "minecraft:coal", "minecraft:coal", "minecraft:soul_sand");
    }

    private static void add(String result, String... sig) {
        String[] layout = new String[SLOTS];
        Map<String, Integer> ing = new HashMap<>();
        int si = 0;
        for (int i = 0; i < SLOTS; i++) {
            char ch = TEMPLATE.charAt(i);
            String id = null;
            if (ch == 'S') id = sig[si++];
            else if (ch != '.') id = LEGEND.get(ch);
            layout[i] = id;
            if (id != null) ing.merge(id, 1, Integer::sum);
        }
        ALL.add(new Recipe(result, layout, ing));
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
