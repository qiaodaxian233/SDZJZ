package com.sdzjz.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 超大工作台合成表（m61 重做）。5 种 12x12 阵型模板轮换（堡垒双环/菱形矩阵/十字要塞/对角矩阵/同心环廊），
 * 铺满 132~144 格、材料加重（含铁块/双份核心模块）、每台 8 枚标志物（4 种 x2）。
 * 匹配仍走多重集（位置无关，手动摆料也友好）；自动填充按 layout 指定位置铺满。多重集全表唯一（生成时校验）。
 * 角色字符：I=铁锭 C=铜锭 G=玻璃 R=红石 O=侦测器 M=核心模块 X=铁块 S=标志物 .=空
 */
public final class SuperBenchRecipes {
    public static final int GRID = 12;
    public static final int SLOTS = 144;

    static final String[] TEMPLATES = {
        "IIIIIIIIIIIIICCCCCCCCCCIICOGGGGGGOCIICGMRRRRMGCIICGRXSSXRGCIICGRSMMSRGCIICGRSMMSRGCIICGRXSSXRGCIICGMRRRRMGCIICOGGGGGGOCIICCCCCCCCCCIIIIIIIIIIIII",
        ".IICCGGCCII.IICCGGGGCCIIICCGGRRGGCCICCGGRSSRGGCCCGGRMOOMRGGCGGRSOXXOSRGGGGRSOXXOSRGGCGGRMOOMRGGCCCGGRSSRGGCCICCGGRRGGCCIIICCGGGGCCII.IICCGGCCII.",
        "IIIIIIIIIIIIIGGGRCCRGGGIIGOGRCCRGOGIIGGGRCCRGGGIIRRRMSSMRRRIICCCSXXSCCCIICCCSXXSCCCIIRRRMSSMRRRIIGGGRCCRGGGIIGOGRCCRGOGIIGGGRCCRGGGIIIIIIIIIIIII",
        "IIIIIIIIIIIIICCRGGGGRCCIICCCRGGRCCCIIRCCCRRCCCRIIGRCXSSXCRGIIGGRSMMSRGGIIGGRSMMSRGGIIGRCXSSXCRGIIRCCCRRCCCRIICCCRGGRCCCIICCRGGGGRCCIIIIIIIIIIIII",
        "ICICICICICICCGGGGGGGGGGIIGORRRRRROGCCGRCIIIICRGIIGRIXSSXIRGCCGRISMMSIRGIIGRISMMSIRGCCGRIXSSXIRGIIGRCIIIICRGCCGORRRRRROGIIGGGGGGGGGGCCICICICICICI",
    };
    static final Map<Character, String> LEGEND = new HashMap<>();
    static {
        LEGEND.put('I', "minecraft:iron_ingot");
        LEGEND.put('C', "minecraft:copper_ingot");
        LEGEND.put('G', "minecraft:glass");
        LEGEND.put('R', "minecraft:redstone");
        LEGEND.put('O', "minecraft:observer");
        LEGEND.put('M', "sdzjz:core_module");
        LEGEND.put('X', "minecraft:iron_block");
    }

    /** layout[i] = 该格物品 id（null=空）。ingredients 为多重集用量。mob 非空=需装着该生物的抓物笼子（合成后生物装进机器，空笼归还）。 */
    public record Recipe(String result, String[] layout, Map<String, Integer> ingredients, String mob) {}
    public static final String CAGE_ID = "sdzjz:capture_cage";
    public static final List<Recipe> ALL = new ArrayList<>();

    static {
        add("sdzjz:auto_crafter", 0, "minecraft:crafting_table", "minecraft:crafting_table", "minecraft:crafter", "minecraft:crafter");
        add("sdzjz:bamboo_farm", 1, "minecraft:bamboo", "minecraft:bamboo", "minecraft:bamboo", "minecraft:bamboo");
        addM("sdzjz:blaze_farm", 2, "minecraft:blaze", "minecraft:blaze_rod", "minecraft:blaze_rod", "minecraft:blaze_powder", "minecraft:blaze_powder");
        addM("sdzjz:ghast_tower", 3, "minecraft:ghast", "minecraft:ghast_tear", "minecraft:ghast_tear", "minecraft:gunpowder", "minecraft:gunpowder");
        addM("sdzjz:breeze_farm", 4, "minecraft:breeze", "minecraft:breeze_rod", "minecraft:breeze_rod", "minecraft:wind_charge", "minecraft:wind_charge");
        add("sdzjz:bonemeal_machine", 0, "minecraft:bone_meal", "minecraft:bone_meal", "minecraft:bone_meal", "minecraft:moss_block");
        add("sdzjz:moss_farm", 1, "minecraft:moss_block", "minecraft:moss_block", "minecraft:moss_block", "minecraft:moss_carpet");
        add("sdzjz:stonecutter_machine", 2, "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone");
        addM("sdzjz:villager_breeder", 3, "minecraft:villager", "minecraft:bread", "minecraft:bread", "minecraft:bread", "minecraft:emerald");
        addM("sdzjz:bone_farm", 4, "minecraft:skeleton", "minecraft:bone", "minecraft:bone", "minecraft:bone", "minecraft:arrow");
        add("sdzjz:cactus_farm", 0, "minecraft:cactus", "minecraft:cactus", "minecraft:cactus", "minecraft:green_dye");
        add("sdzjz:carpet_machine", 1, "minecraft:white_carpet", "minecraft:white_carpet", "minecraft:white_wool", "minecraft:white_wool");
        add("sdzjz:charcoal_kiln", 2, "minecraft:charcoal", "minecraft:charcoal", "minecraft:charcoal", "minecraft:oak_log");
        add("sdzjz:chorus_farm", 3, "minecraft:chorus_fruit", "minecraft:chorus_fruit", "minecraft:chorus_fruit", "minecraft:popped_chorus_fruit");
        add("sdzjz:cobble_maker", 4, "minecraft:cobblestone", "minecraft:cobblestone", "minecraft:cobblestone", "minecraft:stone");
        addM("sdzjz:drowned_tower", 0, "minecraft:drowned", "minecraft:copper_ingot", "minecraft:copper_ingot", "minecraft:prismarine_shard", "minecraft:rotten_flesh");
        addM("sdzjz:flesh_farm", 1, "minecraft:zombie", "minecraft:rotten_flesh", "minecraft:rotten_flesh", "minecraft:rotten_flesh", "minecraft:rotten_flesh");
        add("sdzjz:glass_kiln", 2, "minecraft:glass", "minecraft:glass", "minecraft:sand", "minecraft:sand");
        add("sdzjz:gold_smelter", 3, "minecraft:raw_gold", "minecraft:raw_gold", "minecraft:raw_gold", "minecraft:gold_ingot");
        addM("sdzjz:guardian_farm", 4, "minecraft:guardian", "minecraft:prismarine_shard", "minecraft:prismarine_shard", "minecraft:prismarine_crystals", "minecraft:prismarine_crystals");
        addM("sdzjz:gunpowder_farm", 0, "minecraft:creeper", "minecraft:gunpowder", "minecraft:gunpowder", "minecraft:gunpowder", "minecraft:tnt");
        addM("sdzjz:honey_farm", 1, "minecraft:bee", "minecraft:honeycomb", "minecraft:honeycomb", "minecraft:honeycomb", "minecraft:honey_bottle");
        add("sdzjz:ice_maker", 2, "minecraft:ice", "minecraft:ice", "minecraft:ice", "minecraft:snowball");
        addM("sdzjz:iron_farm", 3, "minecraft:villager", "minecraft:poppy", "minecraft:poppy", "minecraft:iron_ingot", "minecraft:iron_ingot");
        add("sdzjz:iron_smelter", 4, "minecraft:raw_iron", "minecraft:raw_iron", "minecraft:raw_iron", "minecraft:iron_ingot");
        add("sdzjz:kelp_farm", 0, "minecraft:kelp", "minecraft:kelp", "minecraft:kelp", "minecraft:dried_kelp");
        addM("sdzjz:magma_farm", 1, "minecraft:magma_cube", "minecraft:magma_cream", "minecraft:magma_cream", "minecraft:magma_cream", "minecraft:magma_block");
        addM("sdzjz:mob_tower", 2, "minecraft:zombie", "minecraft:bone", "minecraft:gunpowder", "minecraft:string", "minecraft:arrow");
        add("sdzjz:nether_tree_farm", 3, "minecraft:crimson_stem", "minecraft:crimson_stem", "minecraft:warped_stem", "minecraft:crimson_fungus");
        add("sdzjz:nether_wart_farm", 4, "minecraft:nether_wart", "minecraft:nether_wart", "minecraft:nether_wart", "minecraft:soul_sand");
        add("sdzjz:obsidian_maker", 0, "minecraft:obsidian", "minecraft:obsidian", "minecraft:obsidian", "minecraft:crying_obsidian");
        addM("sdzjz:pearl_farm", 1, "minecraft:enderman", "minecraft:ender_pearl", "minecraft:ender_pearl", "minecraft:ender_pearl", "minecraft:obsidian");
        addM("sdzjz:piglin_barter", 2, "minecraft:piglin", "minecraft:gold_ingot", "minecraft:gold_ingot", "minecraft:gold_ingot", "minecraft:obsidian");
        addM("sdzjz:pigman_tower", 3, "minecraft:zombified_piglin", "minecraft:gold_nugget", "minecraft:gold_nugget", "minecraft:gold_nugget", "minecraft:gold_ingot");
        addM("sdzjz:raid_tower", 4, "minecraft:pillager", "minecraft:emerald", "minecraft:emerald", "minecraft:emerald", "minecraft:emerald");
        add("sdzjz:rail_machine", 0, "minecraft:rail", "minecraft:rail", "minecraft:iron_ingot", "minecraft:stick");
        add("sdzjz:sand_maker", 1, "minecraft:sand", "minecraft:sand", "minecraft:sand", "minecraft:gravel");
        addM("sdzjz:shulker_farm", 2, "minecraft:shulker", "minecraft:shulker_shell", "minecraft:shulker_shell", "minecraft:purpur_block", "minecraft:purpur_block");
        addM("sdzjz:slime_farm", 3, "minecraft:slime", "minecraft:slime_ball", "minecraft:slime_ball", "minecraft:slime_ball", "minecraft:slime_ball");
        add("sdzjz:sugarcane_farm", 4, "minecraft:sugar_cane", "minecraft:sugar_cane", "minecraft:sugar_cane", "minecraft:paper");
        add("sdzjz:super_smelter", 0, "minecraft:raw_iron", "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_gold");
        addM("sdzjz:swamp_spawner", 1, "minecraft:bogged", "minecraft:rotten_flesh", "minecraft:bone", "minecraft:string", "minecraft:slime_ball");
        add("sdzjz:tree_farm", 2, "minecraft:oak_log", "minecraft:oak_log", "minecraft:oak_sapling", "minecraft:apple");
        addM("sdzjz:wire_brusher", 3, "minecraft:spider", "minecraft:string", "minecraft:string", "minecraft:string", "minecraft:cobweb");
        addM("sdzjz:witch_tower", 4, "minecraft:witch", "minecraft:glowstone_dust", "minecraft:glowstone_dust", "minecraft:spider_eye", "minecraft:sugar");
        addM("sdzjz:wither_skeleton_farm", 0, "minecraft:wither_skeleton", "minecraft:bone", "minecraft:coal", "minecraft:coal", "minecraft:soul_sand");
        addM("sdzjz:chicken_farm", 1, "minecraft:chicken", "minecraft:feather", "minecraft:feather", "minecraft:egg", "minecraft:egg");
        addM("sdzjz:sheep_farm", 2, "minecraft:sheep", "minecraft:white_wool", "minecraft:white_wool", "minecraft:mutton", "minecraft:mutton");
        addM("sdzjz:cow_farm", 3, "minecraft:cow", "minecraft:beef", "minecraft:beef", "minecraft:leather", "minecraft:leather");
        add("sdzjz:crop_farm", 4, "minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot");
        // 逻辑节点小件（灵魂件各异 → 多重集互相唯一；9 件的小多重集也不可能撞 140+ 件的机器配方）
        addSmall("sdzjz:filter_node", "minecraft:hopper");
        addSmall("sdzjz:sensor_node", "minecraft:comparator");
        addSmall("sdzjz:switch_node", "minecraft:lever");
        addSmall("sdzjz:distributor_node", "minecraft:dropper");
    }

    /** 标志物 4 种各放 2 枚，落在模板的 8 个 S 位上。 */
    private static void add(String result, int tpl, String... sig) {
        build(result, tpl, "", sig);
    }

    /** 小件（逻辑节点）：3×3 居中摆进 12×12——工作台浏览器里可查可一键填料，原版工作台配方同样保留。 */
    private static void addSmall(String result, String soul) {
        String I = "minecraft:iron_ingot", R = "minecraft:redstone", M = "sdzjz:core_module";
        String[][] pat = {{I, soul, I}, {R, M, R}, {I, I, I}};
        String[] layout = new String[SLOTS];
        Map<String, Integer> ing = new java.util.LinkedHashMap<>();
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++) {
                String id = pat[r][c];
                layout[(4 + r) * GRID + (4 + c)] = id;
                ing.merge(id, 1, Integer::sum);
            }
        ALL.add(new Recipe(result, layout, ing, ""));
    }

    /** 刷怪类机器：材料里含 1 个「装着 mob 的抓物笼子」——先去对应地方抓到它才合得出来。 */
    private static void addM(String result, int tpl, String mob, String... sig) {
        build(result, tpl, mob, sig);
    }

    private static void build(String result, int tpl, String mob, String[] sig) {
        String template = TEMPLATES[tpl];
        String[] layout = new String[SLOTS];
        Map<String, Integer> ing = new java.util.LinkedHashMap<>(); // 保留蓝图遇到顺序：材料清单显示稳定
        int si = 0;
        for (int i = 0; i < SLOTS; i++) {
            char ch = template.charAt(i);
            String id = null;
            if (ch == 'S') id = sig[(si++) / 2];
            else if (ch != '.') id = LEGEND.get(ch);
            layout[i] = id;
            if (id != null) ing.merge(id, 1, Integer::sum);
        }
        if (!mob.isEmpty()) { // 用 1 格铁锭的位置放笼子（多重集：铁-1、笼+1，全表唯一性不受影响）
            for (int i = 0; i < SLOTS; i++) {
                if ("minecraft:iron_ingot".equals(layout[i])) {
                    layout[i] = CAGE_ID;
                    ing.merge("minecraft:iron_ingot", -1, Integer::sum);
                    if (ing.getOrDefault("minecraft:iron_ingot", 0) <= 0) ing.remove("minecraft:iron_ingot");
                    ing.put(CAGE_ID, 1);
                    break;
                }
            }
        }
        ALL.add(new Recipe(result, layout, ing, mob));
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
