package com.sdzjz.machine;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 超大工作台合成表（m61 重做，m166 全表换制：原版建造清单 BOM）。
 * 每台机器的配方=在原版里亲手搭这座农场的进货单（刷铁机=床+木板+泥土+漏斗+营火+村民+僵尸；
 * 小黑塔=末地石+命名牌+矿车+漏斗+末影人……），外加 4 枚核心模块（模组机芯税，石英门槛与原版
 * 侦测器农场的"去一次下界"同构）。刷怪类每种生物一只抓物笼（m166 起支持多生物）。
 * 匹配仍走多重集（位置无关，手动摆料也友好，格内可堆叠）；自动填充按 autoLayout 逐行居中铺
 * （1 格 1 件，故单方 BOM 总件数 ≤144，离线校验）。多重集全表唯一（生成时离线校验）。
 * 档位角标 Ⅰ/Ⅱ/Ⅲ（TIER1/TIER3 名单）纯属浏览器分类提示，不再影响用料（m165 材质盘制已废）。
 */
public final class SuperBenchRecipes {
    public static final int GRID = 12;
    public static final int SLOTS = 144;

    /** 档位名单（浏览器角标用）：Ⅰ=主世界露天就能搭的原版农场，Ⅲ=原版终局工程，其余 Ⅱ。 */
    static final java.util.Set<String> TIER1 = java.util.Set.of(
        "sdzjz:bamboo_farm", "sdzjz:cactus_farm", "sdzjz:sugarcane_farm", "sdzjz:kelp_farm",
        "sdzjz:tree_farm", "sdzjz:crop_farm", "sdzjz:moss_farm", "sdzjz:bonemeal_machine",
        "sdzjz:cobble_maker", "sdzjz:sand_maker", "sdzjz:ice_maker", "sdzjz:charcoal_kiln",
        "sdzjz:glass_kiln", "sdzjz:stonecutter_machine", "sdzjz:carpet_machine", "sdzjz:rail_machine",
        "sdzjz:clay_machine", "sdzjz:fishing_machine", "sdzjz:mob_tower", "sdzjz:flesh_farm",
        "sdzjz:bone_farm", "sdzjz:gunpowder_farm", "sdzjz:wire_brusher", "sdzjz:chicken_farm",
        "sdzjz:sheep_farm", "sdzjz:cow_farm", "sdzjz:pig_farm", "sdzjz:animal_farm", "sdzjz:honey_farm",
        "sdzjz:iron_smelter", "sdzjz:gold_smelter", "sdzjz:super_smelter");
    static final java.util.Set<String> TIER3 = java.util.Set.of(
        "sdzjz:wither_farm", "sdzjz:wither_skeleton_farm", "sdzjz:ghast_tower", "sdzjz:breeze_farm",
        "sdzjz:shulker_farm", "sdzjz:pearl_farm", "sdzjz:chorus_farm", "sdzjz:sculk_line",
        "sdzjz:deep_mining_platform", "sdzjz:archaeology_station", "sdzjz:end_expedition_platform",
        "sdzjz:trial_farm", "sdzjz:guardian_farm", "sdzjz:raid_tower", "sdzjz:villager_trader",
        "sdzjz:enchant_factory", "sdzjz:mega_cobble_maker", "sdzjz:mega_iron_farm",
        "sdzjz:mega_slime_farm", "sdzjz:mega_piglin_barter", "sdzjz:mega_mob_tower",
        "sdzjz:mega_witch_tower", "sdzjz:mega_guardian_farm",
        "sdzjz:iron_farm_160", "sdzjz:mega_pigman_tower", "sdzjz:mega_drowned_tower",
        "sdzjz:mega_wither_skeleton_farm", "sdzjz:mega_raid_tower", "sdzjz:mega_honey_farm",
        "sdzjz:mega_amethyst_farm", "sdzjz:mega_fishing_machine", "sdzjz:mega_trial_farm",
        "sdzjz:mega_super_smelter", "sdzjz:mega_crop_farm",
        "sdzjz:wither_rose_farm", "sdzjz:wither_killer", "sdzjz:dragon_cannon",
        "sdzjz:duplicator", "sdzjz:chunk_remover", "sdzjz:chunk_vault", "sdzjz:infinite_beacon"); // m168-m174：工程款与新线终局全入Ⅲ档；m334 复制机=终局中的终局；m376 区块移除器=区块机器线第一台
    private static int tierOf(String result) {
        return TIER1.contains(result) ? 1 : TIER3.contains(result) ? 3 : 2;
    }

    /** layout[i] = 该格物品 id（null=空）。ingredients 为多重集用量。mobs 每项=需一只装着该生物的
     *  抓物笼子（合成后生物装进机器，空笼归还）。count = 单次产出数（m108b，数据线一次 8 根）。 */
    public record Recipe(String result, String[] layout, Map<String, Integer> ingredients,
                         List<String> mobs, int count, int tier) {
        public Recipe(String result, String[] layout, Map<String, Integer> ingredients, String mob) {
            this(result, layout, ingredients,
                 mob.isEmpty() ? java.util.List.<String>of() : java.util.List.of(mob), 1, 0);
        }
        public Recipe(String result, String[] layout, Map<String, Integer> ingredients, String mob, int count) {
            this(result, layout, ingredients, java.util.List.<String>of(), count, 0); // 小件无生物
        }
    }
    public static final String CAGE_ID = "sdzjz:capture_cage";
    public static final List<Recipe> ALL = new ArrayList<>();

    static {
        // 常用建材缩写（与文末 addSmall9 的变量名错开，勿撞）
        String HOP = "minecraft:hopper", CHE = "minecraft:chest", WB = "minecraft:water_bucket",
               LB = "minecraft:lava_bucket", COB = "minecraft:cobblestone", SST = "minecraft:smooth_stone",
               PLK = "minecraft:oak_planks", TRAP = "minecraft:oak_trapdoor", GL = "minecraft:glass",
               RSD = "minecraft:redstone", PIS = "minecraft:piston", OSV = "minecraft:observer",
               TCH = "minecraft:torch", BED = "minecraft:white_bed", FUR = "minecraft:furnace",
               BM = "minecraft:bone_meal", BOT = "minecraft:glass_bottle", DRT = "minecraft:dirt",
               SND = "minecraft:sand", OBSI = "minecraft:obsidian", NBK = "minecraft:nether_bricks",
               EST = "minecraft:end_stone";

        bom("sdzjz:auto_crafter", "", "minecraft:crafter", 4, "minecraft:crafting_table", 2, HOP, 8, CHE, 4,
                RSD, 12, "minecraft:comparator", 2, "minecraft:repeater", 2, "minecraft:iron_ingot", 8);
        bom("sdzjz:brewing_tower", "", "minecraft:brewing_stand", 4, "minecraft:blaze_powder", 4,
                "minecraft:nether_wart", 8, BOT, 12, HOP, 8, CHE, 2, "minecraft:comparator", 4, RSD, 12, SST, 8);
        bom("sdzjz:enchant_factory", "", "minecraft:enchanting_table", 1, "minecraft:bookshelf", 15, // 15 书架=原版满级附魔台
                "minecraft:book", 8, "minecraft:lapis_lazuli", 16, OBSI, 4, "minecraft:diamond", 2, HOP, 4, CHE, 2);
        // 凋灵机：召唤材按原版仪式=灵魂沙4+凋骷头3；星×1 引子=先亲手打一次凋灵
        bom("sdzjz:wither_farm", "", "minecraft:soul_sand", 4, "minecraft:wither_skeleton_skull", 3,
                "minecraft:nether_star", 1, OBSI, 26, GL, 8, HOP, 4, CHE, 2);
        // m174 龙池杀凋机：对表用户 150 块蓝图（11×11×22：基岩41+末地传送门20=末地场地
        // 自带不计料；玻璃17/灵魂沙13/活塞10/铁块8/发射器3放头/漏斗矿车1收账），÷1 近原样＝90件18种。
        bom("sdzjz:wither_killer", "", GL, 17, "minecraft:soul_sand", 13, PIS, 10, RSD, 9,
                "minecraft:iron_block", 8, OSV, 7, "minecraft:target", 3, CHE, 3,
                "minecraft:cobblestone_wall", 3, "minecraft:dispenser", 3, HOP, 2, OBSI, 2,
                "minecraft:iron_trapdoor", 2, "minecraft:heavy_weighted_pressure_plate", 1,
                "minecraft:dropper", 1, "minecraft:sticky_piston", 1, "minecraft:hopper_minecart", 1);
        bom("sdzjz:g_misc_machine", "", "minecraft:cobweb", 4, "minecraft:spore_blossom", 2,
                "minecraft:amethyst_block", 4, "minecraft:calcite", 8, "minecraft:moss_block", 4, GL, 8, HOP, 4, CHE, 2);
        bom("sdzjz:sculk_line", "", "minecraft:sculk_catalyst", 2, "minecraft:sculk_sensor", 4,
                "minecraft:sculk_shrieker", 2, "minecraft:sculk", 16, "minecraft:white_wool", 8, // 羊毛=原版消音
                "minecraft:deepslate_bricks", 16, HOP, 4, CHE, 2);
        bom("sdzjz:villager_discount_machine", "", "minecraft:golden_apple", 4, "minecraft:fermented_spider_eye", 2, // 原版治愈仪式
                BOT, 2, "minecraft:gunpowder", 2, "minecraft:iron_bars", 8, BED, 1, "minecraft:emerald", 8, HOP, 2, CHE, 1);
        bom("sdzjz:villager_trader", "minecraft:villager", "minecraft:emerald_block", 4, CHE, 4, GL, 8,
                PLK, 16, TRAP, 4, "minecraft:lectern", 1, "minecraft:barrel", 1); // 交易大厅
        bom("sdzjz:duplicator", "", // m334 无限复制机=全表最贵（作者点名"配方要超级难"）：8星+双信标+重核+末影水晶阵
                "minecraft:nether_star", 8, "minecraft:netherite_block", 8, "minecraft:beacon", 2,
                "minecraft:heavy_core", 1, "minecraft:enchanted_golden_apple", 4, "minecraft:end_crystal", 4,
                "minecraft:echo_shard", 8, "minecraft:diamond_block", 16, "minecraft:emerald_block", 32,
                "minecraft:amethyst_block", 16); // 合计 99+核心模块4=103 布局位（≤144）
        bom("sdzjz:chunk_remover", "", // m376 区块移除器=工业化采矿终局（挖掘主题：镐阵+TNT+黑曜石机身+重核驱动）
                "minecraft:nether_star", 2, "minecraft:heavy_core", 1, "minecraft:beacon", 1,
                "minecraft:netherite_pickaxe", 4, "minecraft:diamond_block", 8, "minecraft:obsidian", 32,
                "minecraft:tnt", 16, PIS, 8, HOP, 8, CHE, 4); // 合计 84+核心模块4=88 布局位（≤144）
        bom("sdzjz:chunk_vault", "", // m381 区块储存器=第100台（存档主题：末影箱阵+潜影壳+紫晶+重核）
                "minecraft:nether_star", 2, "minecraft:heavy_core", 1, "minecraft:ender_chest", 8,
                "minecraft:shulker_shell", 4, "minecraft:amethyst_block", 16, "minecraft:obsidian", 32,
                "minecraft:diamond_block", 8, HOP, 8, CHE, 4); // 合计 83+核心模块4=87 布局位（≤144）
        bom("sdzjz:infinite_beacon", "", // m399 无限距离信标=第101台（信标主题：四信标+双星+回声碎片"无视距离"意象）
                "minecraft:beacon", 4, "minecraft:nether_star", 2, "minecraft:echo_shard", 8,
                "minecraft:netherite_block", 2, "minecraft:diamond_block", 8, "minecraft:emerald_block", 8,
                "minecraft:obsidian", 16, HOP, 4, CHE, 2); // 合计 54+核心模块4=58 布局位（≤144）
        bom("sdzjz:grindstone_recycler", "", "minecraft:grindstone", 2, "minecraft:book", 4,
                "minecraft:stone", 8, "minecraft:iron_ingot", 8, HOP, 4, CHE, 2);
        bom("sdzjz:bamboo_farm", "", "minecraft:bamboo", 8, OSV, 8, PIS, 8, RSD, 8, SST, 24, HOP, 4, CHE, 2);
        bom("sdzjz:blaze_farm", "minecraft:blaze", NBK, 24, GL, 8, "minecraft:iron_trapdoor", 4,
                "minecraft:blaze_rod", 2, HOP, 4, CHE, 2);
        bom("sdzjz:ghast_tower", "minecraft:ghast", OBSI, 16, GL, 16, "minecraft:ghast_tear", 1, HOP, 4, CHE, 2);
        bom("sdzjz:breeze_farm", "minecraft:breeze", "minecraft:tuff_bricks", 16, "minecraft:copper_block", 8,
                "minecraft:copper_grate", 4, "minecraft:breeze_rod", 2, HOP, 4, CHE, 2);
        bom("sdzjz:bonemeal_machine", "", "minecraft:composter", 8, "minecraft:moss_block", 8, PLK, 16,
                RSD, 8, BM, 4, HOP, 8, CHE, 2);
        bom("sdzjz:moss_farm", "", "minecraft:moss_block", 4, BM, 16, PIS, 6, OSV, 4, SST, 16, RSD, 6, HOP, 4, CHE, 2);
        bom("sdzjz:stonecutter_machine", "", "minecraft:stonecutter", 4, "minecraft:stone", 16, RSD, 4, HOP, 4, CHE, 2);
        bom("sdzjz:villager_breeder", "minecraft:villager", BED, 3, "minecraft:carrot", 16, "minecraft:bread", 8, // 3床=原版繁殖门槛
                PLK, 16, GL, 8, "minecraft:composter", 1, HOP, 2, CHE, 1);
        bom("sdzjz:bone_farm", "minecraft:skeleton", COB, 32, WB, 2, TRAP, 4, "minecraft:bone", 4, HOP, 4, CHE, 2);
        bom("sdzjz:cactus_farm", "", "minecraft:cactus", 8, SND, 8, "minecraft:string", 8, GL, 8, SST, 16, HOP, 4, CHE, 2);
        bom("sdzjz:carpet_machine", "", "minecraft:white_wool", 8, "minecraft:shears", 1, "minecraft:white_carpet", 4,
                PLK, 8, HOP, 4, CHE, 2);
        bom("sdzjz:charcoal_kiln", "", FUR, 8, "minecraft:oak_log", 16, "minecraft:charcoal", 4, HOP, 8, CHE, 2);
        bom("sdzjz:chorus_farm", "", "minecraft:chorus_flower", 4, EST, 24, GL, 8,
                "minecraft:popped_chorus_fruit", 2, HOP, 4, CHE, 2);
        bom("sdzjz:cobble_maker", "", WB, 1, LB, 1, PIS, 2, OSV, 2, RSD, 4, SST, 8, HOP, 4, CHE, 2); // m168 回归：入门款=原版就俩桶，全表最便宜
        // m247 重取整（m245 首账，策略修正后溢价 18.1%→8.3%）：520万刷石机.litematic 全量 99587 块；计料实测 95830→BOM 103774（溢价 8.3%），保守槽位 75。
        bomPacked("sdzjz:mega_cobble_maker", "",
                "minecraft:cobblestone", 32768,
                "minecraft:white_stained_glass", 20480,
                "minecraft:piston", 10112,
                "minecraft:obsidian", 9280,
                "minecraft:observer", 8192,
                "minecraft:diorite_wall", 5952,
                "minecraft:lava_bucket", 4096,
                "minecraft:stone_brick_wall", 4096,
                "minecraft:chain", 2112,
                "minecraft:polished_diorite", 1472,
                "minecraft:note_block", 960,
                "minecraft:activator_rail", 576,
                "minecraft:redstone", 576,
                "minecraft:iron_trapdoor", 448,
                "minecraft:white_concrete", 256,
                "minecraft:scaffolding", 256,
                "minecraft:polished_diorite_stairs", 192,
                "minecraft:powered_rail", 192,
                "minecraft:repeater", 192,
                "minecraft:polished_diorite_slab", 192,
                "minecraft:light_gray_glazed_terracotta", 192,
                "minecraft:sticky_piston", 192,
                "minecraft:iron_door", 192,
                "minecraft:oak_fence_gate", 128,
                "minecraft:slime_block", 128,
                "minecraft:redstone_block", 128,
                "minecraft:water_bucket", 64,
                "minecraft:tnt", 64,
                "minecraft:comparator", 60,
                "minecraft:warped_fence_gate", 37,
                "minecraft:oak_leaves", 30,
                "minecraft:oak_log", 30,
                "minecraft:redstone_torch", 20,
                "minecraft:dropper", 15,
                "minecraft:dead_horn_coral_fan", 14,
                "minecraft:stone_button", 13,
                "minecraft:furnace", 10,
                "minecraft:composter", 8,
                "minecraft:crimson_fence_gate", 8,
                "minecraft:birch_log", 8,
                "minecraft:birch_leaves", 8,
                "minecraft:oak_sign", 6,
                "minecraft:ender_chest", 4,
                "minecraft:lever", 3,
                "minecraft:anvil", 3,
                "minecraft:target", 3,
                "minecraft:black_stained_glass", 2,
                "minecraft:cherry_sign", 2,
                "minecraft:redstone_lamp", 1,
                "minecraft:white_stained_glass_pane", 1);
        bom("sdzjz:drowned_tower", "minecraft:drowned", COB, 24, WB, 2, "minecraft:turtle_egg", 1,
                "minecraft:copper_ingot", 4, HOP, 4, CHE, 2);
        // m247 全量过账：僵尸增援溺尸塔.litematic 4990 块（他模组背包×1 剔除；盔甲架3+展示框1=实体入料；僵尸笼=增援种子口径照旧）；计料实测 5022→BOM 5348（溢价 6.5%），保守槽位 55。
        bomPacked("sdzjz:mega_drowned_tower", "minecraft:zombie",
                "minecraft:white_stained_glass", 2112,
                "minecraft:iron_block", 1728,
                "minecraft:scaffolding", 256,
                "minecraft:slime_block", 192,
                "minecraft:redstone", 192,
                "minecraft:honey_block", 128,
                "minecraft:glass", 128,
                "minecraft:oak_leaves", 128,
                "minecraft:comparator", 128,
                "minecraft:water_bucket", 64,
                "minecraft:white_glazed_terracotta", 54,
                "minecraft:smooth_stone", 35,
                "minecraft:sticky_piston", 31,
                "minecraft:powered_rail", 14,
                "minecraft:hopper", 14,
                "minecraft:obsidian", 14,
                "minecraft:redstone_torch", 12,
                "minecraft:repeater", 11,
                "minecraft:oak_sign", 10,
                "minecraft:dropper", 9,
                "minecraft:spruce_trapdoor", 9,
                "minecraft:observer", 8,
                "minecraft:magma_block", 8,
                "minecraft:lever", 8,
                "minecraft:composter", 6,
                "minecraft:note_block", 5,
                "minecraft:smooth_stone_slab", 5,
                "minecraft:soul_sand", 4,
                "minecraft:target", 4,
                "minecraft:powder_snow_bucket", 3,
                "minecraft:armor_stand", 3,
                "minecraft:iron_trapdoor", 2,
                "minecraft:crafter", 2,
                "minecraft:stone_brick_slab", 2,
                "minecraft:dispenser", 2,
                "minecraft:oak_button", 2,
                "minecraft:furnace", 2,
                "minecraft:white_carpet", 2,
                "minecraft:redstone_lamp", 1,
                "minecraft:packed_ice", 1,
                "minecraft:oak_trapdoor", 1,
                "minecraft:sculk_catalyst", 1,
                "minecraft:redstone_block", 1,
                "minecraft:lava_bucket", 1,
                "minecraft:stone_button", 1,
                "minecraft:rail", 1,
                "minecraft:activator_rail", 1,
                "minecraft:barrel", 1,
                "minecraft:item_frame", 1);
        bom("sdzjz:flesh_farm", "minecraft:zombie", COB, 24, WB, 2, TRAP, 4, "minecraft:rotten_flesh", 4, HOP, 4, CHE, 2);
        bom("sdzjz:glass_kiln", "", FUR, 8, SND, 16, "minecraft:charcoal", 8, HOP, 8, CHE, 2);
        bom("sdzjz:gold_smelter", "", FUR, 8, "minecraft:raw_gold", 8, LB, 1, HOP, 8, CHE, 2);
        bom("sdzjz:guardian_farm", "minecraft:guardian", "minecraft:sponge", 8, "minecraft:prismarine", 24, // 海绵=原版抽水神殿
                "minecraft:sea_lantern", 2, WB, 2, HOP, 4, CHE, 2);
        // m248 全量过账：守卫者三件套 2664+820+425 块合账（船收集/展示类实体入料，神殿排水不入原理图→海绵16 抽水税照 m171 保留）；实体账[minecraft:glow_item_frame×6入料; minecraft:chest_minecart×3入料; minecraft:boat×1入料; minecraft:armor_stand×1入料; minecraft:boat×63入料]；计料实测 3752→BOM 4195（溢价 11.8%），保守槽位 83（m251：wall_torch 归一并入 torch=23 销死料、海绵16外挂账并入脚本总账）。
        bomPacked("sdzjz:mega_guardian_farm", "minecraft:guardian",
                "minecraft:glass", 896,
                "minecraft:white_concrete", 256,
                "minecraft:hopper", 256,
                "minecraft:redstone", 256,
                "minecraft:prismarine_bricks", 256,
                "minecraft:obsidian", 192,
                "minecraft:white_stained_glass", 192,
                "minecraft:observer", 128,
                "minecraft:powered_rail", 128,
                "minecraft:redstone_torch", 128,
                "minecraft:dropper", 128,
                "minecraft:chest", 128,
                "minecraft:comparator", 128,
                "minecraft:prismarine_slab", 128,
                "minecraft:blue_ice", 128,
                "minecraft:stone_button", 128,
                "minecraft:water_bucket", 64,
                "minecraft:oak_boat", 64,
                "minecraft:repeater", 62,
                "minecraft:jack_o_lantern", 44,
                "minecraft:warped_trapdoor", 43,
                "minecraft:note_block", 33,
                "minecraft:soul_sand", 30,
                "minecraft:ladder", 28,
                "minecraft:cobweb", 28,
                "minecraft:torch", 23,
                "minecraft:iron_trapdoor", 22,
                "minecraft:white_stained_glass_pane", 22,
                "minecraft:sticky_piston", 22,
                "minecraft:prismarine_brick_slab", 22,
                "minecraft:cyan_glazed_terracotta", 19,
                "minecraft:composter", 16,
                "minecraft:sponge", 16,
                "minecraft:activator_rail", 15,
                "minecraft:dispenser", 15,
                "minecraft:redstone_block", 14,
                "minecraft:target", 11,
                "minecraft:diorite_wall", 11,
                "minecraft:shulker_box", 11,
                "minecraft:piston", 11,
                "minecraft:rail", 10,
                "minecraft:stone_bricks", 10,
                "minecraft:warped_fence", 8,
                "minecraft:slime_block", 6,
                "minecraft:honey_block", 6,
                "minecraft:glow_item_frame", 6,
                "minecraft:light_weighted_pressure_plate", 5,
                "minecraft:scaffolding", 5,
                "minecraft:deepslate_tile_wall", 5,
                "minecraft:wither_skeleton_skull", 4,
                "minecraft:prismarine_wall", 4,
                "minecraft:warped_sign", 3,
                "minecraft:detector_rail", 3,
                "minecraft:nether_brick_fence", 3,
                "minecraft:chest_minecart", 3,
                "minecraft:warped_fence_gate", 2,
                "minecraft:iron_bars", 2,
                "minecraft:campfire", 2,
                "minecraft:turtle_egg", 2,
                "minecraft:lever", 1,
                "minecraft:spruce_trapdoor", 1,
                "minecraft:lantern", 1,
                "minecraft:armor_stand", 1);
        bom("sdzjz:gunpowder_farm", "minecraft:creeper", COB, 24, "minecraft:white_carpet", 8, TRAP, 8, // 地毯=原版防蜘蛛
                "minecraft:gunpowder", 4, HOP, 4, CHE, 2);
        bom("sdzjz:honey_farm", "minecraft:bee", "minecraft:beehive", 4, "minecraft:campfire", 2, // 营火=原版安抚蜂群
                "minecraft:dandelion", 8, BOT, 8, "minecraft:shears", 1, PLK, 8, HOP, 4, CHE, 2);
        // m248 全量过账：蜜匹农场 2195 块（92 组采收单元全量）；实体账[无]；计料实测 2195→BOM 2524（溢价 15.0%），保守槽位 15。
        bomPacked("sdzjz:mega_honey_farm", "minecraft:bee",
                "minecraft:iron_block", 512,
                "minecraft:redstone", 512,
                "minecraft:glass", 448,
                "minecraft:grass_block", 128,
                "minecraft:stone_brick_stairs", 128,
                "minecraft:hopper", 128,
                "minecraft:comparator", 128,
                "minecraft:dispenser", 128,
                "minecraft:observer", 128,
                "minecraft:dropper", 128,
                "minecraft:stone_button", 128,
                "minecraft:redstone_block", 11,
                "minecraft:redstone_lamp", 11,
                "minecraft:chest", 4,
                "minecraft:oak_door", 2);
        bom("sdzjz:ice_maker", "", WB, 2, "minecraft:ice", 4, SST, 16, GL, 8, HOP, 4, CHE, 2);
        // 刷铁机：照用户实拍进货单（单核刷铁机一箱料），村民+僵尸两只活的都要（m166 多生物首例）
        bom("sdzjz:iron_farm", "minecraft:villager,minecraft:zombie", DRT, 26, PLK, 24, TRAP, 2, BED, 4,
                "minecraft:oak_sign", 3, TCH, 8, GL, 1, "minecraft:oak_boat", 1, WB, 1, CHE, 2, HOP, 1,
                "minecraft:campfire", 2);
        // m253 粗账过账（摘录截断=下限账）：40核刷铁机 11044 块；床120张=item120、水1924→64桶税、岩浆60桶、船10=载具入料、村民120活体不入；摘录覆盖 10003/11044 块（未点名 1041 块≈9% 无从入账，重传投影后走 m172 管线精账重跑）；计料实测 8033→BOM 8262（溢价 2.9%），保守槽位 17。
        bomPacked("sdzjz:mega_iron_farm", "minecraft:villager,minecraft:zombie",
                "minecraft:birch_fence_gate", 1856,
                "minecraft:birch_slab", 1856,
                "minecraft:glass", 1024,
                "minecraft:birch_stairs", 1024,
                "minecraft:diorite_wall", 960,
                "minecraft:birch_fence", 704,
                "minecraft:oak_slab", 320,
                "minecraft:torch", 256,
                "minecraft:white_bed", 128,
                "minecraft:water_bucket", 64,
                "minecraft:lava_bucket", 60,
                "minecraft:oak_boat", 10);
        // m248 全量过账：160核刷铁机+合成收集 20103+644 块合账（村民573实体=活体不入料，双笼口径照旧）；实体账[minecraft:villager×573不入; minecraft:minecart×10入料]；计料实测 18628→BOM 19138（溢价 2.7%），保守槽位 71（m251：farmland 128 归一成 dirt 销死料）。
        bomPacked("sdzjz:iron_farm_160", "minecraft:villager,minecraft:zombie",
                "minecraft:white_stained_glass", 6016,
                "minecraft:powered_rail", 3008,
                "minecraft:smooth_stone_slab", 2752,
                "minecraft:smooth_stone", 2304,
                "minecraft:obsidian", 1984,
                "minecraft:white_bed", 1024,
                "minecraft:redstone_torch", 256,
                "minecraft:lever", 192,
                "minecraft:activator_rail", 192,
                "minecraft:bamboo_trapdoor", 192,
                "minecraft:dirt", 128,
                "minecraft:carrot", 128,
                "minecraft:soul_sand", 128,
                "minecraft:wither_rose", 128,
                "minecraft:iron_block", 128,
                "minecraft:water_bucket", 64,
                "minecraft:rail", 63,
                "minecraft:oak_trapdoor", 63,
                "minecraft:redstone_block", 44,
                "minecraft:turtle_egg", 40,
                "minecraft:stone_button", 37,
                "minecraft:hopper", 30,
                "minecraft:glass", 28,
                "minecraft:redstone", 27,
                "minecraft:observer", 24,
                "minecraft:repeater", 17,
                "minecraft:comparator", 15,
                "minecraft:warped_fence_gate", 15,
                "minecraft:dropper", 12,
                "minecraft:oak_sign", 11,
                "minecraft:minecart", 10,
                "minecraft:packed_ice", 9,
                "minecraft:lava_bucket", 9,
                "minecraft:sticky_piston", 8,
                "minecraft:piston", 8,
                "minecraft:note_block", 7,
                "minecraft:oak_fence_gate", 6,
                "minecraft:crafter", 6,
                "minecraft:slime_block", 5,
                "minecraft:white_concrete", 3,
                "minecraft:ender_chest", 3,
                "minecraft:chest", 2,
                "minecraft:composter", 2,
                "minecraft:glowstone", 2,
                "minecraft:stone_brick_wall", 2,
                "minecraft:lightning_rod", 1,
                "minecraft:dispenser", 1,
                "minecraft:oak_button", 1,
                "minecraft:sand", 1,
                "minecraft:cactus", 1,
                "minecraft:redstone_lamp", 1);
        bom("sdzjz:iron_smelter", "", FUR, 8, "minecraft:raw_iron", 8, LB, 1, HOP, 8, CHE, 2);
        bom("sdzjz:kelp_farm", "", "minecraft:kelp", 8, WB, 2, PIS, 4, OSV, 4, GL, 16, RSD, 4, HOP, 4, CHE, 2);
        bom("sdzjz:magma_farm", "minecraft:magma_cube", "minecraft:magma_block", 8, "minecraft:iron_bars", 8,
                NBK, 12, "minecraft:magma_cream", 2, HOP, 4, CHE, 2);
        bom("sdzjz:mob_tower", "minecraft:zombie", COB, 48, WB, 4, TRAP, 8, TCH, 8, HOP, 4, CHE, 4); // 通用大黑塔=堆料最多
        // m253 粗账过账（摘录截断=下限账）：920万船吸刷怪塔 133507 块；传送门20064格=点火产物剔除、船实体3345=载具入料、打火石=点门仪式；摘录覆盖 104079/133507 块（未点名 9364 块≈7% 无从入账，重传投影后走 m172 管线精账重跑）；计料实测 107425→BOM 111937（溢价 4.2%），保守槽位 33。
        bomPacked("sdzjz:mega_mob_tower", "minecraft:zombie",
                "minecraft:smooth_stone_slab", 28672,
                "minecraft:obsidian", 24576,
                "minecraft:soul_sand", 24576,
                "minecraft:white_stained_glass", 16384,
                "minecraft:spruce_sign", 10048,
                "minecraft:oak_boat", 3392,
                "minecraft:dispenser", 3392,
                "minecraft:wither_rose", 704,
                "minecraft:turtle_egg", 192,
                "minecraft:flint_and_steel", 1);
        bom("sdzjz:nether_tree_farm", "", "minecraft:crimson_fungus", 2, "minecraft:warped_fungus", 2,
                "minecraft:netherrack", 16, BM, 16, PIS, 2, HOP, 4, CHE, 2);
        bom("sdzjz:nether_wart_farm", "", "minecraft:nether_wart", 8, "minecraft:soul_sand", 16, PIS, 4,
                RSD, 6, HOP, 4, CHE, 2);
        bom("sdzjz:obsidian_maker", "", LB, 4, WB, 1, "minecraft:pointed_dripstone", 2, SST, 12, PIS, 2,
                RSD, 4, OBSI, 2, HOP, 4, CHE, 2);
        // 小黑塔（用户点名）：末地石平台+命名牌末影螨+矿车诱饵+沿边活板门
        bom("sdzjz:pearl_farm", "minecraft:enderman", EST, 32, "minecraft:name_tag", 1, "minecraft:minecart", 1,
                "minecraft:rail", 2, TRAP, 8, "minecraft:ender_pearl", 2, HOP, 4, CHE, 2);
        bom("sdzjz:piglin_barter", "minecraft:piglin", OBSI, 10, "minecraft:gold_ingot", 16, // 黑曜石10=原版下界门最省砌法
                "minecraft:flint_and_steel", 1, HOP, 4, CHE, 4);
        // m253 粗账过账（摘录截断=下限账）：140猪灵交易场 2972 块；金块4=交易本钱标志件(不在蓝图,口径照旧)；摘录覆盖 1930/2972 块（未点名 1042 块≈35% 无从入账，重传投影后走 m172 管线精账重跑）；计料实测 1934→BOM 2178（溢价 12.6%），保守槽位 13。
        bomPacked("sdzjz:mega_piglin_barter", "minecraft:piglin",
                "minecraft:white_concrete", 768,
                "minecraft:white_stained_glass", 512,
                "minecraft:hopper", 256,
                "minecraft:observer", 192,
                "minecraft:redstone_torch", 192,
                "minecraft:smooth_stone", 192,
                "minecraft:comparator", 62,
                "minecraft:gold_block", 4);
        bom("sdzjz:pigman_tower", "minecraft:zombified_piglin", "minecraft:turtle_egg", 4, OBSI, 16, // 龟蛋=原版仇恨诱饵
                TRAP, 8, "minecraft:gold_nugget", 8, HOP, 4, CHE, 4);
        // m248 全量过账：80w猪人塔+收集背包 149679+2005 块合账（传送门98208格=点火产物剔除、黑曜石门阵全量入料）；实体账[他模组sophisticatedbackpacks:diamond_backpack×1剔除; minecraft:armor_stand×12入料; minecraft:item_frame×1入料]；计料实测 53455→BOM 55901（溢价 4.6%），保守槽位 63。
        bomPacked("sdzjz:mega_pigman_tower", "minecraft:zombified_piglin",
                "minecraft:obsidian", 45056,
                "minecraft:white_stained_glass", 8192,
                "minecraft:cyan_carpet", 1088,
                "minecraft:white_concrete", 256,
                "minecraft:iron_block", 256,
                "minecraft:hopper", 192,
                "minecraft:redstone", 128,
                "minecraft:smooth_quartz_slab", 128,
                "minecraft:comparator", 128,
                "minecraft:observer", 128,
                "minecraft:water_bucket", 64,
                "minecraft:repeater", 49,
                "minecraft:birch_trapdoor", 37,
                "minecraft:redstone_torch", 31,
                "minecraft:packed_ice", 28,
                "minecraft:crafter", 22,
                "minecraft:dropper", 22,
                "minecraft:white_stained_glass_pane", 18,
                "minecraft:armor_stand", 12,
                "minecraft:note_block", 7,
                "minecraft:glow_lichen", 6,
                "minecraft:quartz_slab", 6,
                "minecraft:blue_ice", 5,
                "minecraft:bamboo_trapdoor", 4,
                "minecraft:turtle_egg", 4,
                "minecraft:soul_sand", 4,
                "minecraft:sticky_piston", 3,
                "minecraft:ender_chest", 3,
                "minecraft:oak_leaves", 3,
                "minecraft:oak_trapdoor", 3,
                "minecraft:beacon", 3,
                "minecraft:iron_trapdoor", 2,
                "minecraft:redstone_block", 2,
                "minecraft:bamboo_door", 2,
                "minecraft:powered_rail", 2,
                "minecraft:oak_stairs", 1,
                "minecraft:ladder", 1,
                "minecraft:composter", 1,
                "minecraft:lever", 1,
                "minecraft:rail", 1,
                "minecraft:dispenser", 1,
                "minecraft:item_frame", 1);
        // m174 疣猪兽农场：对表用户"疣猪兽农场-单人双层"蓝图（15×15×9，587 块：平滑石416/
        // 火把48防僵尸化/诡异菌16=诱饵标志件/岩浆9击杀/箱8），÷≈8 蒸馏＝78件13种。
        bom("sdzjz:hoglin_farm", "minecraft:hoglin", SST, 52, TCH, 6, GL, 3, DRT, 2,
                TRAP, 2, "minecraft:warped_fungus", 2, "minecraft:oak_fence", 2, HOP, 1,
                "minecraft:smooth_stone_slab", 1, LB, 1, CHE, 1);
        bom("sdzjz:raid_tower", "minecraft:pillager", "minecraft:bell", 1, BED, 1, COB, 32, WB, 2, // 钟+床=一格假村庄
                "minecraft:emerald", 4, HOP, 4, CHE, 4);
        // m247 全量过账：百万劫掠塔.litematic 7594 块（他模组背包×3 剔除；盔甲架×2=实体入料；村民16/劫掠兽=活体不入料，掠夺者笼照旧）；计料实测 7460→BOM 8146（溢价 9.2%），保守槽位 63。
        bomPacked("sdzjz:mega_raid_tower", "minecraft:pillager",
                "minecraft:white_stained_glass", 2176,
                "minecraft:smooth_stone", 1664,
                "minecraft:smooth_stone_slab", 448,
                "minecraft:redstone", 384,
                "minecraft:hopper", 320,
                "minecraft:powered_rail", 320,
                "minecraft:observer", 256,
                "minecraft:iron_block", 256,
                "minecraft:repeater", 192,
                "minecraft:blue_stained_glass", 192,
                "minecraft:stone_button", 192,
                "minecraft:slime_block", 192,
                "minecraft:comparator", 192,
                "minecraft:stone_brick_wall", 192,
                "minecraft:sticky_piston", 192,
                "minecraft:white_glazed_terracotta", 128,
                "minecraft:sand", 128,
                "minecraft:redstone_torch", 128,
                "minecraft:piston", 128,
                "minecraft:water_bucket", 64,
                "minecraft:obsidian", 40,
                "minecraft:white_stained_glass_pane", 38,
                "minecraft:iron_trapdoor", 36,
                "minecraft:scaffolding", 32,
                "minecraft:stone_brick_stairs", 32,
                "minecraft:fletching_table", 28,
                "minecraft:cobblestone_stairs", 28,
                "minecraft:redstone_block", 24,
                "minecraft:crafter", 24,
                "minecraft:lantern", 16,
                "minecraft:packed_ice", 12,
                "minecraft:oak_trapdoor", 12,
                "minecraft:spruce_trapdoor", 11,
                "minecraft:note_block", 9,
                "minecraft:oak_button", 9,
                "minecraft:cobblestone_wall", 8,
                "minecraft:pointed_dripstone", 6,
                "minecraft:birch_sign", 5,
                "minecraft:honey_block", 4,
                "minecraft:lever", 4,
                "minecraft:iron_door", 4,
                "minecraft:redstone_lamp", 3,
                "minecraft:beacon", 3,
                "minecraft:soul_sand", 3,
                "minecraft:oak_leaves", 2,
                "minecraft:cake", 2,
                "minecraft:spruce_sign", 2,
                "minecraft:armor_stand", 2,
                "minecraft:sculk_catalyst", 1,
                "minecraft:dispenser", 1,
                "minecraft:composter", 1);
        bom("sdzjz:rail_machine", "", "minecraft:iron_ingot", 16, "minecraft:stick", 8, "minecraft:rail", 4,
                FUR, 2, HOP, 4, CHE, 2);
        bom("sdzjz:sand_maker", "", "minecraft:tnt", 2, SND, 8, SST, 16, RSD, 4, HOP, 4, CHE, 2);
        bom("sdzjz:shulker_farm", "minecraft:shulker", "minecraft:purpur_block", 24, "minecraft:end_rod", 4,
                GL, 8, "minecraft:shulker_shell", 2, HOP, 4, CHE, 2);
        bom("sdzjz:slime_farm", "minecraft:slime", COB, 40, "minecraft:iron_block", 4, "minecraft:carved_pumpkin", 1, // 铁块4+南瓜=原版铁傀儡诱饵
                TCH, 8, "minecraft:slime_ball", 2, HOP, 4, CHE, 2);
        // m253 粗账过账（摘录截断=下限账）：200万史莱姆农场 5860 块；repeating_command_block×5=放置辅助剔除；摘录覆盖 4654/5860 块（未点名 1201 块≈20% 无从入账，重传投影后走 m172 管线精账重跑）；计料实测 4654→BOM 4832（溢价 3.8%），保守槽位 16。
        bomPacked("sdzjz:mega_slime_farm", "minecraft:slime",
                "minecraft:white_stained_glass", 2688,
                "minecraft:hopper", 832,
                "minecraft:dropper", 448,
                "minecraft:powered_rail", 256,
                "minecraft:observer", 256,
                "minecraft:crafter", 128,
                "minecraft:blue_ice", 128,
                "minecraft:soul_sand", 60,
                "minecraft:wither_rose", 36);
        bom("sdzjz:sugarcane_farm", "", "minecraft:sugar_cane", 8, SND, 8, WB, 2, OSV, 8, PIS, 8, RSD, 8,
                SST, 12, HOP, 4, CHE, 2);
        bom("sdzjz:super_smelter", "", FUR, 16, HOP, 16, CHE, 8, "minecraft:coal_block", 4); // 原版超级熔炉阵=炉与漏斗海
        // m247 全量过账：1728熔炉背包版.litematic 20076 块（他模组背包×2 剔除；漏斗矿车×9=实体入料）；计料实测 19510→BOM 20475（溢价 4.9%），保守槽位 80。
        bomPacked("sdzjz:mega_super_smelter", "",
                "minecraft:white_stained_glass", 5696,
                "minecraft:hopper", 4800,
                "minecraft:powered_rail", 4096,
                "minecraft:furnace", 1728,
                "minecraft:dropper", 896,
                "minecraft:smooth_stone_slab", 576,
                "minecraft:oak_leaves", 320,
                "minecraft:white_concrete", 192,
                "minecraft:redstone_block", 192,
                "minecraft:slime_block", 192,
                "minecraft:blue_ice", 192,
                "minecraft:composter", 128,
                "minecraft:white_stained_glass_pane", 128,
                "minecraft:rail", 128,
                "minecraft:redstone_torch", 128,
                "minecraft:moss_block", 128,
                "minecraft:lever", 128,
                "minecraft:white_carpet", 128,
                "minecraft:water_bucket", 64,
                "minecraft:iron_trapdoor", 60,
                "minecraft:piston", 51,
                "minecraft:activator_rail", 49,
                "minecraft:light_gray_carpet", 48,
                "minecraft:observer", 47,
                "minecraft:redstone", 41,
                "minecraft:repeater", 33,
                "minecraft:chest", 30,
                "minecraft:note_block", 30,
                "minecraft:oak_fence_gate", 26,
                "minecraft:mud", 25,
                "minecraft:detector_rail", 24,
                "minecraft:stone_pressure_plate", 24,
                "minecraft:dead_bubble_coral_fan", 24,
                "minecraft:stone_brick_stairs", 14,
                "minecraft:lava_bucket", 12,
                "minecraft:stone_button", 12,
                "minecraft:magma_block", 12,
                "minecraft:stonecutter", 12,
                "minecraft:sticky_piston", 11,
                "minecraft:comparator", 10,
                "minecraft:hopper_minecart", 9,
                "minecraft:glow_lichen", 7,
                "minecraft:lantern", 7,
                "minecraft:spruce_trapdoor", 5,
                "minecraft:soul_sand", 2,
                "minecraft:scaffolding", 2,
                "minecraft:cauldron", 1,
                "minecraft:stone_brick_wall", 1,
                "minecraft:dispenser", 1,
                "minecraft:redstone_lamp", 1,
                "minecraft:honey_block", 1,
                "minecraft:oak_sign", 1,
                "minecraft:daylight_detector", 1,
                "minecraft:target", 1);
        bom("sdzjz:swamp_spawner", "minecraft:bogged", "minecraft:mud", 16, TRAP, 4, WB, 1,
                "minecraft:arrow", 4, HOP, 4, CHE, 2);
        bom("sdzjz:tree_farm", "", "minecraft:oak_sapling", 8, DRT, 16, BM, 16, "minecraft:apple", 2, HOP, 4, CHE, 2);
        bom("sdzjz:wire_brusher", "minecraft:spider", "minecraft:cobweb", 2, COB, 24, WB, 2,
                "minecraft:string", 4, HOP, 4, CHE, 2);
        bom("sdzjz:witch_tower", "minecraft:witch", PLK, 24, "minecraft:cauldron", 1, TRAP, 4, WB, 2, // 锅=原版女巫小屋标配
                BOT, 4, HOP, 4, CHE, 2);
        // m247 全量过账：沼泽刷怪塔v2.litematic 6013 块（收集车队 矿车62+漏斗矿车2、展示框 3+荧光5=实体入料）；计料实测 5833→BOM 6038（溢价 3.5%），保守槽位 57。
        bomPacked("sdzjz:mega_witch_tower", "minecraft:witch",
                "minecraft:smooth_stone_slab", 2752,
                "minecraft:smooth_stone", 1216,
                "minecraft:white_stained_glass", 576,
                "minecraft:stone_brick_stairs", 384,
                "minecraft:redstone", 256,
                "minecraft:cherry_trapdoor", 192,
                "minecraft:magma_block", 128,
                "minecraft:water_bucket", 64,
                "minecraft:minecart", 62,
                "minecraft:hopper", 58,
                "minecraft:white_concrete", 56,
                "minecraft:observer", 36,
                "minecraft:cherry_fence_gate", 26,
                "minecraft:dropper", 23,
                "minecraft:soul_campfire", 22,
                "minecraft:packed_ice", 18,
                "minecraft:comparator", 18,
                "minecraft:redstone_torch", 16,
                "minecraft:powered_rail", 15,
                "minecraft:cherry_pressure_plate", 14,
                "minecraft:note_block", 11,
                "minecraft:cherry_button", 8,
                "minecraft:repeater", 7,
                "minecraft:white_stained_glass_pane", 7,
                "minecraft:composter", 7,
                "minecraft:cherry_sign", 6,
                "minecraft:slime_block", 6,
                "minecraft:sticky_piston", 6,
                "minecraft:dispenser", 5,
                "minecraft:piston", 5,
                "minecraft:shulker_box", 5,
                "minecraft:glow_item_frame", 5,
                "minecraft:scaffolding", 4,
                "minecraft:powder_snow_bucket", 4,
                "minecraft:crafter", 4,
                "minecraft:chest", 4,
                "minecraft:soul_sand", 3,
                "minecraft:barrel", 3,
                "minecraft:item_frame", 3,
                "minecraft:hopper_minecart", 2,
                "minecraft:iron_trapdoor", 1);
        bom("sdzjz:wither_skeleton_farm", "minecraft:wither_skeleton", NBK, 32, "minecraft:soul_sand", 4,
                "minecraft:coal", 4, "minecraft:bone", 2, HOP, 4, CHE, 2);
        // m247 全量过账：凋零骷髅农场.litematic 5260 块（铁傀儡×15仇恨=铁块60+雕刻南瓜15 按召唤仪式入料）；计料实测 5255→BOM 5481（溢价 4.3%），保守槽位 17。
        bomPacked("sdzjz:mega_wither_skeleton_farm", "minecraft:wither_skeleton",
                "minecraft:wither_rose", 2176,
                "minecraft:dirt", 2176,
                "minecraft:smooth_stone_slab", 448,
                "minecraft:cherry_trapdoor", 192,
                "minecraft:white_stained_glass", 128,
                "minecraft:cobblestone_wall", 128,
                "minecraft:obsidian", 128,
                "minecraft:iron_block", 60,
                "minecraft:glass_pane", 24,
                "minecraft:carved_pumpkin", 15,
                "minecraft:grass_block", 4,
                "minecraft:turtle_egg", 1,
                "minecraft:cherry_sign", 1);
        // m174 凋灵玫瑰农场：对表用户"26k凋灵玫瑰农场"蓝图（53×53×69，3632 块：末地石砖1562/
        // 白玻璃1359/白地毯332/箱64/漏斗48/冰道30；实体=铁傀儡+凋灵+矿车），÷≈40 蒸馏；
        // 灵魂沙4+凋骷头3=凋灵召唤料（与凋骷农场自咬合），铁傀儡笼=受害者。102件15种。
        bom("sdzjz:wither_rose_farm", "minecraft:iron_golem",
                "minecraft:end_stone_bricks", 39, "minecraft:white_stained_glass", 34,
                "minecraft:white_carpet", 8, "minecraft:soul_sand", 4, "minecraft:wither_skeleton_skull", 3,
                CHE, 2, HOP, 1, "minecraft:end_stone_brick_slab", 1, RSD, 1, "minecraft:packed_ice", 1,
                COB, 1, WB, 1, "minecraft:minecart", 1);
        bom("sdzjz:chicken_farm", "minecraft:chicken", "minecraft:egg", 8, "minecraft:dispenser", 1, LB, 1, // 熔岩刀=原版全自动鸡场
                GL, 8, SST, 8, HOP, 4, CHE, 2);
        bom("sdzjz:sheep_farm", "minecraft:sheep", "minecraft:dispenser", 4, "minecraft:shears", 4, // 发射器持剪=原版自动薅毛
                "minecraft:grass_block", 8, GL, 8, HOP, 4, CHE, 2);
        bom("sdzjz:cow_farm", "minecraft:cow", "minecraft:wheat", 16, LB, 1, GL, 8, SST, 8,
                "minecraft:leather", 2, HOP, 4, CHE, 2);
        bom("sdzjz:pig_farm", "minecraft:pig", "minecraft:carrot", 16, LB, 1, GL, 8, DRT, 8,
                "minecraft:porkchop", 2, HOP, 4, CHE, 2); // m92
        // m174 动物农场：对表用户 326 块蓝图（7×12×13 微型舱：平滑石167/玻璃88/发射器6水流/
        // 动力铁轨7/侦测器4；实体=兔21+猪13+牛6+羊5+鸡1 五物种→五笼），÷≈6 蒸馏＝63件14种。
        bom("sdzjz:animal_farm", "minecraft:cow,minecraft:pig,minecraft:sheep,minecraft:chicken,minecraft:rabbit",
                SST, 28, GL, 15, RSD, 2, "minecraft:powered_rail", 1, "minecraft:dispenser", 1,
                WB, 1, "minecraft:oak_fence", 1, HOP, 1, TRAP, 1, OSV, 1,
                "minecraft:comparator", 1, "minecraft:sticky_piston", 1);
        bom("sdzjz:crop_farm", "", DRT, 16, WB, 1, "minecraft:wheat_seeds", 8, "minecraft:carrot", 4,
                "minecraft:potato", 4, "minecraft:composter", 1, HOP, 5, "minecraft:minecart", 1, CHE, 2); // 漏斗5+矿车=漏斗矿车收菜
        // m252 全量过账：多种植物农村.litematic 15193 块（21×51×21 塔楼；耕地1968→土并原生24=1992、裸wall_torch32+torch16=48、四作物/瓜茎归一成种子）；实体账[minecraft:item×16掉落物不计]；计料实测 14575→BOM 15258（溢价 4.7%），保守槽位 45。
        bomPacked("sdzjz:mega_crop_farm", "",
                "minecraft:white_stained_glass", 3456,
                "minecraft:glass", 3264,
                "minecraft:dirt", 2048,
                "minecraft:moss_block", 1280,
                "minecraft:glowstone", 448,
                "minecraft:slime_block", 448,
                "minecraft:smooth_stone", 448,
                "minecraft:jack_o_lantern", 384,
                "minecraft:melon_seeds", 384,
                "minecraft:pumpkin_seeds", 384,
                "minecraft:smooth_stone_slab", 384,
                "minecraft:carrot", 320,
                "minecraft:wheat_seeds", 320,
                "minecraft:potato", 320,
                "minecraft:beetroot_seeds", 320,
                "minecraft:oak_fence", 128,
                "minecraft:redstone_torch", 128,
                "minecraft:sticky_piston", 128,
                "minecraft:sand", 128,
                "minecraft:cactus", 128,
                "minecraft:chain", 128,
                "minecraft:water_bucket", 64,
                "minecraft:oak_leaves", 60,
                "minecraft:torch", 48,
                "minecraft:redstone_lamp", 36,
                "minecraft:composter", 16,
                "minecraft:oak_trapdoor", 16,
                "minecraft:repeater", 12,
                "minecraft:redstone", 6,
                "minecraft:observer", 6,
                "minecraft:oak_fence_gate", 4,
                "minecraft:comparator", 4,
                "minecraft:hopper", 4,
                "minecraft:note_block", 2,
                "minecraft:redstone_block", 2,
                "minecraft:pumpkin", 1,
                "minecraft:melon", 1);
        bom("sdzjz:deep_mining_platform", "", "minecraft:diamond", 2, "minecraft:ancient_debris", 2, // 引子:先亲手挖到样本
                "minecraft:tnt", 8, OBSI, 8, "minecraft:rail", 8, "minecraft:minecart", 1, TCH, 8, HOP, 4, CHE, 4);
        // m174 弱加载盾构机：对表 Dark牌2025版蓝图（86×86×11，269 块：平滑石71/侦测器33/
        // 音符盒10=弱加载核心/诡异木牌12/黑曜石10；矿车2=蓝图点名手动摆放），÷≈3 蒸馏；
        // TNT4=弹药本钱件（照猪灵交易场金块本钱先例）。87件18种。
        bom("sdzjz:tunnel_borer", "", SST, 24, OSV, 11, RSD, 5, "minecraft:warped_sign", 4,
                "minecraft:redstone_torch", 4, "minecraft:tnt", 4, "minecraft:sticky_piston", 3,
                "minecraft:note_block", 3, "minecraft:repeater", 3, OBSI, 3, "minecraft:comparator", 3,
                "minecraft:dropper", 2, "minecraft:powered_rail", 2, "minecraft:scaffolding", 2,
                HOP, 2, "minecraft:iron_trapdoor", 2, "minecraft:minecart", 2);
        bom("sdzjz:archaeology_station", "", "minecraft:brush", 2, "minecraft:echo_shard", 2, // 引子:远古城+藏宝图亲手跑
                "minecraft:heart_of_the_sea", 1, SND, 16, "minecraft:terracotta", 8, HOP, 4, CHE, 4);
        bom("sdzjz:end_expedition_platform", "", "minecraft:ender_eye", 12, "minecraft:dragon_breath", 2, // 眼12=原版找要塞；引子:先亲手打一次龙
                EST, 16, OBSI, 10, BOT, 4, HOP, 4, CHE, 4);
        // m174 屠龙炮：对表用户 10540 块蓝图（82×90×56：遮光玻璃2160/红石716/黑地毯705/
        // 末地烛502/TNT320/标靶258/侦测器227；基岩2108=末地场地自带不计料；船1=标志件），
        // ÷≈80 蒸馏＝98件18种打满。
        bom("sdzjz:dragon_cannon", "", "minecraft:tinted_glass", 27, RSD, 9, "minecraft:black_carpet", 9,
                "minecraft:smooth_quartz_slab", 7, "minecraft:end_rod", 6, "minecraft:repeater", 4,
                "minecraft:tnt", 4, "minecraft:black_glazed_terracotta", 4, "minecraft:sticky_piston", 3,
                "minecraft:target", 3, "minecraft:redstone_torch", 3, "minecraft:smooth_quartz_stairs", 3,
                OSV, 3, "minecraft:birch_fence_gate", 3, PIS, 3, "minecraft:powered_rail", 2,
                "minecraft:oak_boat", 1);
        bom("sdzjz:trial_farm", "", "minecraft:trial_key", 2, "minecraft:ominous_bottle", 1, // 引子:试炼密室+亲手杀袭击队长
                "minecraft:copper_block", 8, "minecraft:copper_bulb", 4, "minecraft:tuff_bricks", 12, HOP, 4, CHE, 4);
        // m248 全量过账：试炼大厅农场 20867 块（97×63×131）；实体账[minecraft:zombified_piglin×2不入; minecraft:hopper_minecart×1入料; minecraft:minecart×1入料]；计料实测 18295→BOM 18697（溢价 2.2%），保守槽位 66。
        bomPacked("sdzjz:mega_trial_farm", "",
                "minecraft:white_stained_glass", 9856,
                "minecraft:snow", 2368,
                "minecraft:obsidian", 2368,
                "minecraft:powered_rail", 2176,
                "minecraft:glass", 576,
                "minecraft:redstone_torch", 192,
                "minecraft:oak_trapdoor", 192,
                "minecraft:redstone_block", 192,
                "minecraft:iron_block", 128,
                "minecraft:packed_ice", 128,
                "minecraft:water_bucket", 64,
                "minecraft:rail", 51,
                "minecraft:sand", 45,
                "minecraft:cactus", 45,
                "minecraft:turtle_egg", 44,
                "minecraft:hopper", 41,
                "minecraft:cherry_fence", 40,
                "minecraft:cherry_sign", 33,
                "minecraft:redstone", 27,
                "minecraft:trial_spawner", 24,
                "minecraft:comparator", 17,
                "minecraft:repeater", 14,
                "minecraft:chest", 10,
                "minecraft:sea_pickle", 9,
                "minecraft:vault", 8,
                "minecraft:dropper", 7,
                "minecraft:iron_trapdoor", 4,
                "minecraft:observer", 4,
                "minecraft:lava_bucket", 4,
                "minecraft:cherry_trapdoor", 4,
                "minecraft:lever", 3,
                "minecraft:soul_sand", 3,
                "minecraft:crafter", 3,
                "minecraft:scaffolding", 2,
                "minecraft:redstone_lamp", 2,
                "minecraft:glowstone", 2,
                "minecraft:emerald_block", 2,
                "minecraft:oak_sign", 1,
                "minecraft:dispenser", 1,
                "minecraft:activator_rail", 1,
                "minecraft:detector_rail", 1,
                "minecraft:white_wool", 1,
                "minecraft:sticky_piston", 1,
                "minecraft:quartz_stairs", 1,
                "minecraft:hopper_minecart", 1,
                "minecraft:minecart", 1);
        // 逻辑节点小件（灵魂件各异 → 多重集互相唯一；9 件的小多重集也不可能撞机器 BOM）
        addSmall("sdzjz:filter_node", "minecraft:hopper");
        addSmall("sdzjz:chunk_filter", "minecraft:stonecutter"); // m377 灵魂件=切石机（切方块/筛地层）
        addSmall("sdzjz:void_processor", "minecraft:soul_campfire"); // m378 灵魂件=灵魂营火（烧成灵魂/经验）
        addSmall("sdzjz:chunk_scanner", "minecraft:spyglass"); // m380 灵魂件=望远镜（侦察）
        addSmall("sdzjz:sensor_node", "minecraft:comparator");
        addSmall("sdzjz:switch_node", "minecraft:lever");
        addSmall("sdzjz:distributor_node", "minecraft:dropper");
        // m84a 缺口七机
        bom("sdzjz:amethyst_farm", "", "minecraft:amethyst_shard", 4, "minecraft:amethyst_block", 4,
                "minecraft:calcite", 8, "minecraft:smooth_basalt", 8, PIS, 4, OSV, 4, HOP, 4, CHE, 2);
        // m248 全量过账：紫水晶农场 72556 块（212×115×225，苔藓=飞行机骨架全量）；实体账[无]；计料实测 64540→BOM 66963（溢价 3.8%），保守槽位 50。
        bomPacked("sdzjz:mega_amethyst_farm", "",
                "minecraft:moss_block", 32768,
                "minecraft:glass", 5440,
                "minecraft:slime_block", 4736,
                "minecraft:honey_block", 3328,
                "minecraft:scaffolding", 2560,
                "minecraft:redstone", 2496,
                "minecraft:iron_block", 2048,
                "minecraft:observer", 1600,
                "minecraft:amethyst_cluster", 1536,
                "minecraft:sticky_piston", 1280,
                "minecraft:obsidian", 1216,
                "minecraft:spruce_sign", 1216,
                "minecraft:medium_amethyst_bud", 960,
                "minecraft:budding_amethyst", 960,
                "minecraft:large_amethyst_bud", 960,
                "minecraft:small_amethyst_bud", 704,
                "minecraft:white_stained_glass", 576,
                "minecraft:note_block", 448,
                "minecraft:redstone_lamp", 448,
                "minecraft:spruce_button", 384,
                "minecraft:iron_trapdoor", 256,
                "minecraft:target", 256,
                "minecraft:repeater", 256,
                "minecraft:oak_trapdoor", 128,
                "minecraft:packed_ice", 128,
                "minecraft:gold_block", 128,
                "minecraft:water_bucket", 64,
                "minecraft:hopper", 22,
                "minecraft:redstone_block", 14,
                "minecraft:comparator", 14,
                "minecraft:dropper", 7,
                "minecraft:redstone_torch", 7,
                "minecraft:crafter", 5,
                "minecraft:soul_sand", 3,
                "minecraft:stone_button", 3,
                "minecraft:white_concrete", 2,
                "minecraft:white_wool", 2,
                "minecraft:birch_door", 2,
                "minecraft:beacon", 1,
                "minecraft:lever", 1);
        bom("sdzjz:clay_machine", "", "minecraft:mud", 16, "minecraft:pointed_dripstone", 4, DRT, 8, WB, 1, // 泥+滴水石锥=原版泥转黏土
                "minecraft:clay_ball", 4, HOP, 4, CHE, 2);
        bom("sdzjz:dripstone_farm", "", "minecraft:pointed_dripstone", 4, "minecraft:dripstone_block", 8,
                WB, 1, LB, 1, SST, 16, HOP, 4, CHE, 2);
        bom("sdzjz:snow_machine", "minecraft:snow_golem", "minecraft:carved_pumpkin", 1, "minecraft:snow_block", 2, // 南瓜+雪块2=原版堆雪傀儡
                SST, 8, GL, 8, "minecraft:snowball", 4, HOP, 4, CHE, 2);
        bom("sdzjz:basalt_machine", "", "minecraft:soul_soil", 4, "minecraft:blue_ice", 2, LB, 1, // 原版玄武岩三件套
                PIS, 4, OSV, 4, SST, 8, "minecraft:basalt", 2, HOP, 4, CHE, 2);
        bom("sdzjz:fishing_machine", "", "minecraft:fishing_rod", 1, WB, 2, TRAP, 2, "minecraft:note_block", 1,
                "minecraft:string", 8, PLK, 8, HOP, 4, CHE, 2);
        // m252 全量过账：鳕鱼鲑鱼农场.litematic 86533 块（沙74112全量=19超级包、水4000→64桶税、气泡柱=水形态剔除、他模组背包件×1剔除）；实体账[鳕鱼12/鲑鱼8/鱿鱼8/流浪商人+羊驼=活体不入; minecraft:hopper_minecart×1入料; minecraft:item×128掉落物不计]；计料实测 78930→BOM 82779（溢价 4.9%），保守槽位 48。
        bomPacked("sdzjz:mega_fishing_machine", "minecraft:cod,minecraft:salmon",
                "minecraft:sand", 77824,
                "minecraft:glass", 2944,
                "minecraft:smooth_stone", 896,
                "minecraft:powered_rail", 576,
                "minecraft:magma_block", 320,
                "minecraft:water_bucket", 64,
                "minecraft:redstone_block", 47,
                "minecraft:rail", 45,
                "minecraft:glowstone", 36,
                "minecraft:smooth_stone_slab", 9,
                "minecraft:oak_trapdoor", 3,
                "minecraft:soul_sand", 3,
                "minecraft:packed_ice", 2,
                "minecraft:hopper", 2,
                "minecraft:iron_block", 1,
                "minecraft:white_stained_glass", 1,
                "minecraft:comparator", 1,
                "minecraft:dispenser", 1,
                "minecraft:observer", 1,
                "minecraft:cauldron", 1,
                "minecraft:amethyst_cluster", 1,
                "minecraft:hopper_minecart", 1);
        bom("sdzjz:disc_machine", "minecraft:creeper,minecraft:skeleton", "minecraft:jukebox", 1, // 骷髅射爬行者=原版唱片机制,俩都要抓
                "minecraft:note_block", 1, COB, 16, TRAP, 4, HOP, 4, CHE, 2);
        addSmall("sdzjz:auto_feeder", "minecraft:bread"); // m80d 自动喂食器
        // m108b 基础件全量进浏览器。图样/用量与原版配方文件逐字一致，不开新获取捷径；
        // 多重集离线校验两两唯一（9 件小配方也不可能撞机器 BOM）。
        String E="minecraft:emerald", I="minecraft:iron_ingot", P="minecraft:paper", B="minecraft:bread",
               G="minecraft:glass", GP="minecraft:glass_pane", EP="minecraft:ender_pearl", MM="sdzjz:core_module",
               R="minecraft:redstone", C="minecraft:copper_ingot", CH="minecraft:chest", IB="minecraft:iron_bars",
               L="minecraft:lapis_lazuli", EE="minecraft:ender_eye", W="sdzjz:wireless_node", NS="minecraft:nether_star",
               Q="minecraft:quartz", GB="minecraft:gold_block", RB="minecraft:redstone_block",
               AB="minecraft:amethyst_block", D="minecraft:diamond";
        addSmall9("sdzjz:trade_center",      1, E,I,E,   I,MM,I,   E,I,E);   // 原版为无序配方，多重集一致
        addSmall9("sdzjz:villager_contract", 1, P,P,P,   B,E,B,    P,P,P);
        addSmall9("sdzjz:terminal",          1, GP,GP,GP, EP,MM,EP, I,I,I);
        addSmall9("sdzjz:portable_vault",    1, CH,EE,CH, EP,MM,EP, I,CH,I);   // m311 随身仓库：箱子×3+末影之眼+珍珠×2+铁×2+核心模块
        addSmall9("sdzjz:linker",            1, R,EP,R,  EP,MM,EP, R,EP,R);
        addSmall9("sdzjz:capture_cage",      1, IB,I,IB, I,MM,I,   IB,I,IB);
        addSmall9("sdzjz:data_panel",        1, G,L,G,   EP,CH,EP, G,MM,G);
        addSmall9("sdzjz:storage_core",      1, I,CH,I,  CH,MM,CH, I,CH,I);
        addSmall9("sdzjz:data_cable",        8, G,G,G,   R,MM,R,   G,G,G);
        addSmall9("sdzjz:wireless_node",     1, C,EP,C,  EP,MM,EP, C,EP,C);
        addSmall9("sdzjz:satellite_node",    1, EE,W,EE, W,NS,W,   EE,MM,EE);
        addSmall9("sdzjz:core_module",       1, C,R,C,   R,Q,R,    C,R,C);
        addSmall9("sdzjz:storage_upgrade",   1, R,CH,R,  CH,MM,CH, R,CH,R);
        addSmall9("sdzjz:speed_upgrade",     1, GB,RB,GB, RB,MM,RB, GB,RB,GB);
        addSmall9("sdzjz:count_upgrade",     1, AB,GB,AB, GB,MM,GB, AB,GB,AB);
        addSmall9("sdzjz:parallel_upgrade",  1, D,GB,D,  D,MM,D,   D,GB,D);
    }

    /** m166 机器配方=原版建造清单：kv 为 (物品id, 数量) 交替；mobsCsv 逗号分隔（""=无生物）。
     *  自动追加 4 枚核心模块与每生物一只抓物笼；笼子排清单首位（浏览器一眼可见）。 */
    private static void bom(String result, String mobsCsv, Object... kv) {
        List<String> mobs = mobsCsv.isEmpty() ? java.util.List.<String>of() : List.of(mobsCsv.split(","));
        Map<String, Integer> ing = new java.util.LinkedHashMap<>();
        if (!mobs.isEmpty()) ing.put(CAGE_ID, mobs.size());
        for (int i = 0; i < kv.length; i += 2) ing.merge((String) kv[i], (Integer) kv[i + 1], Integer::sum);
        ing.merge("sdzjz:core_module", 4, Integer::sum);
        ALL.add(new Recipe(result, autoLayout(ing), ing, mobs, 1, tierOf(result)));
    }

    /** m244 打包版 BOM（工程款全量总数用）：kv 仍为 (原版物品id, 原版总数) 交替——匹配/缺料全按
     *  原版计数（m242 内核认包），数字直接写 litematic 全量过账后的取整值（m247 修正策略：二级仅在
     *  向上取整溢价≤15% 时用，否则一级向上取整——旧"一级超32格→全二级"的前提是 1格1件排包，
     *  已被包堆叠 64/格 淘汰，中等量级会造 46%~96% 虚溢价）。layout=null 即"打包填料"标记：
     *  填料钮不铺蓝图，改从背包按内容物贪心搬压缩包（二级→一级→散件），见 Handler.pullPacked。
     *  离线断言（类加载即炸，宁可开不了游戏不可带病上线）：①大宗(≥64)须 64 整倍；
     *  ②保守槽位账 Σceil(一级包数/64)+小件件数/16 ≤144（一级最密口径，二级只会更省）。 */
    private static void bomPacked(String result, String mobsCsv, Object... kv) {
        List<String> mobs = mobsCsv.isEmpty() ? java.util.List.<String>of() : List.of(mobsCsv.split(","));
        Map<String, Integer> ing = new java.util.LinkedHashMap<>();
        if (!mobs.isEmpty()) ing.put(CAGE_ID, mobs.size());
        for (int i = 0; i < kv.length; i += 2) ing.merge((String) kv[i], (Integer) kv[i + 1], Integer::sum);
        ing.merge("sdzjz:core_module", 4, Integer::sum);
        int slots = 0;
        for (Map.Entry<String, Integer> e : ing.entrySet()) {
            int n = e.getValue();
            if (n < 64) { slots += (n + 15) / 16; continue; } // 小件按 16/格 保守计（若引入 maxCount=1 的小件需另行过账）
            if (n % 64 != 0) throw new IllegalStateException(result + " 大宗 " + e.getKey() + "×" + n + " 非64整倍");
            slots += (n / 64 + 63) / 64; // 一级包 64/格 最密口径
        }
        if (slots > SLOTS) throw new IllegalStateException(result + " 打包槽位账 " + slots + " > 144");
        ALL.add(new Recipe(result, null, ing, mobs, 1, tierOf(result)));
    }

    /** BOM 自动布局：按清单顺序逐行铺进 12×12（1 格 1 件），整体垂直居中、末行水平居中。 */
    private static String[] autoLayout(Map<String, Integer> ing) {
        List<String> seq = new ArrayList<>();
        ing.forEach((id, c) -> { for (int i = 0; i < c; i++) seq.add(id); });
        String[] layout = new String[SLOTS];
        int rows = (seq.size() + GRID - 1) / GRID, r0 = Math.max(0, (GRID - rows) / 2), p = 0;
        for (int r = 0; r < rows && p < seq.size(); r++) {
            int rem = Math.min(GRID, seq.size() - p);
            int off = (GRID - rem) / 2;
            for (int c = 0; c < rem; c++) layout[(r0 + r) * GRID + off + c] = seq.get(p++);
        }
        return layout;
    }

    /** m108b 通用小配方：显式 3×3 图样（行优先 9 格）居中进 12×12，count=单次产出。与原版配方同料同量。 */
    private static void addSmall9(String result, int count, String... nine) {
        String[] layout = new String[SLOTS];
        Map<String, Integer> ing = new java.util.LinkedHashMap<>();
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++) {
                String id = nine[r * 3 + c];
                if (id == null || id.isEmpty()) continue;
                layout[(4 + r) * GRID + (4 + c)] = id;
                ing.merge(id, 1, Integer::sum);
            }
        ALL.add(new Recipe(result, layout, ing, "", count));
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

    /** 网格多重集精确匹配到配方。 */
    public static Recipe match(Map<String, Integer> grid) {
        if (grid.isEmpty()) return null;
        for (Recipe r : ALL) if (r.ingredients.equals(grid)) return r;
        return null;
    }

    public static ItemStack resultStack(Recipe r) {
        return r == null ? ItemStack.EMPTY
                : new ItemStack(com.sdzjz.item.ItemData.itemById(r.result), Math.max(1, r.count())); // m522：走 ItemData 世代口（1.20.1 上挂）
    }
}
