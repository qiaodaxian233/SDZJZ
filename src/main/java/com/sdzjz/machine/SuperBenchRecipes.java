package com.sdzjz.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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
        "sdzjz:sheep_farm", "sdzjz:cow_farm", "sdzjz:pig_farm", "sdzjz:honey_farm",
        "sdzjz:iron_smelter", "sdzjz:gold_smelter", "sdzjz:super_smelter");
    static final java.util.Set<String> TIER3 = java.util.Set.of(
        "sdzjz:wither_farm", "sdzjz:wither_skeleton_farm", "sdzjz:ghast_tower", "sdzjz:breeze_farm",
        "sdzjz:shulker_farm", "sdzjz:pearl_farm", "sdzjz:chorus_farm", "sdzjz:sculk_line",
        "sdzjz:deep_mining_platform", "sdzjz:archaeology_station", "sdzjz:end_expedition_platform",
        "sdzjz:trial_farm", "sdzjz:guardian_farm", "sdzjz:raid_tower", "sdzjz:villager_trader",
        "sdzjz:enchant_factory", "sdzjz:mega_cobble_maker", "sdzjz:mega_iron_farm",
        "sdzjz:mega_slime_farm", "sdzjz:mega_piglin_barter", "sdzjz:mega_mob_tower",
        "sdzjz:mega_witch_tower", "sdzjz:mega_guardian_farm"); // m168-m171：工程款全入Ⅲ档
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
        bom("sdzjz:g_misc_machine", "", "minecraft:cobweb", 4, "minecraft:spore_blossom", 2,
                "minecraft:amethyst_block", 4, "minecraft:calcite", 8, "minecraft:moss_block", 4, GL, 8, HOP, 4, CHE, 2);
        bom("sdzjz:sculk_line", "", "minecraft:sculk_catalyst", 2, "minecraft:sculk_sensor", 4,
                "minecraft:sculk_shrieker", 2, "minecraft:sculk", 16, "minecraft:white_wool", 8, // 羊毛=原版消音
                "minecraft:deepslate_bricks", 16, HOP, 4, CHE, 2);
        bom("sdzjz:villager_discount_machine", "", "minecraft:golden_apple", 4, "minecraft:fermented_spider_eye", 2, // 原版治愈仪式
                BOT, 2, "minecraft:gunpowder", 2, "minecraft:iron_bars", 8, BED, 1, "minecraft:emerald", 8, HOP, 2, CHE, 1);
        bom("sdzjz:villager_trader", "minecraft:villager", "minecraft:emerald_block", 4, CHE, 4, GL, 8,
                PLK, 16, TRAP, 4, "minecraft:lectern", 1, "minecraft:barrel", 1); // 交易大厅
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
        // m168 百万刷石机（m167 蓝图 BOM 移交给它）：对表用户 520万/h 工程蓝图（litematic 实测 99587 块：
        // 活塞10069/侦测器7162/白玻璃17895/黑曜石9261/圆石壳29334/岩浆源3936/水源3979/锁链2091/音符盒955/
        // TNT复制引擎…，÷≈700 取整，岩浆桶对齐"近四千桶"取 10(÷394)，黏活塞/黏液/TNT 各留 1 引擎标志件）
        // ——全表最重 123 件，产能 722/10t≈520万/h 与配方难度同挡。
        bom("sdzjz:mega_cobble_maker", "", COB, 24, "minecraft:white_stained_glass", 18, PIS, 12, OBSI, 10,
                OSV, 9, "minecraft:diorite_wall", 8, "minecraft:stone_brick_wall", 5, LB, 10, WB, 5,
                RSD, 4, "minecraft:chain", 3, "minecraft:note_block", 2, "minecraft:sticky_piston", 1,
                "minecraft:slime_block", 1, "minecraft:tnt", 1, HOP, 4, CHE, 2);
        bom("sdzjz:drowned_tower", "minecraft:drowned", COB, 24, WB, 2, "minecraft:turtle_egg", 1,
                "minecraft:copper_ingot", 4, HOP, 4, CHE, 2);
        bom("sdzjz:flesh_farm", "minecraft:zombie", COB, 24, WB, 2, TRAP, 4, "minecraft:rotten_flesh", 4, HOP, 4, CHE, 2);
        bom("sdzjz:glass_kiln", "", FUR, 8, SND, 16, "minecraft:charcoal", 8, HOP, 8, CHE, 2);
        bom("sdzjz:gold_smelter", "", FUR, 8, "minecraft:raw_gold", 8, LB, 1, HOP, 8, CHE, 2);
        bom("sdzjz:guardian_farm", "minecraft:guardian", "minecraft:sponge", 8, "minecraft:prismarine", 24, // 海绵=原版抽水神殿
                "minecraft:sea_lantern", 2, WB, 2, HOP, 4, CHE, 2);
        // m171 强力守卫者农场：对表用户 zip 三件套（电梯+击杀舱+仓储，litematic 合计 3909 块：玻璃880/
        // 传送门230格+黑曜石188=门运/海晶砖系333/漏斗238/红石228/白混凝土242/侦测器114/蓝冰84/
        // 船实体64=船收集/蛛网28缓降/气泡柱28电梯，÷≈12 取整；神殿排水不入原理图→海绵16=基础款×2 顶抽水税）
        // ——90 件 15 种，守卫者笼照基础款。
        bom("sdzjz:mega_guardian_farm", "minecraft:guardian", GL, 16, "minecraft:sponge", 16, OBSI, 10,
                "minecraft:prismarine_bricks", 8, HOP, 8, RSD, 4, "minecraft:white_concrete", 4, CHE, 4,
                OSV, 3, "minecraft:powered_rail", 3, "minecraft:dropper", 2, "minecraft:comparator", 2,
                "minecraft:blue_ice", 2, "minecraft:oak_boat", 2, "minecraft:flint_and_steel", 1);
        bom("sdzjz:gunpowder_farm", "minecraft:creeper", COB, 24, "minecraft:white_carpet", 8, TRAP, 8, // 地毯=原版防蜘蛛
                "minecraft:gunpowder", 4, HOP, 4, CHE, 2);
        bom("sdzjz:honey_farm", "minecraft:bee", "minecraft:beehive", 4, "minecraft:campfire", 2, // 营火=原版安抚蜂群
                "minecraft:dandelion", 8, BOT, 8, "minecraft:shears", 1, PLK, 8, HOP, 4, CHE, 2);
        bom("sdzjz:ice_maker", "", WB, 2, "minecraft:ice", 4, SST, 16, GL, 8, HOP, 4, CHE, 2);
        // 刷铁机：照用户实拍进货单（单核刷铁机一箱料），村民+僵尸两只活的都要（m166 多生物首例）
        bom("sdzjz:iron_farm", "minecraft:villager,minecraft:zombie", DRT, 26, PLK, 24, TRAP, 2, BED, 4,
                "minecraft:oak_sign", 3, TCH, 8, GL, 1, "minecraft:oak_boat", 1, WB, 1, CHE, 2, HOP, 1,
                "minecraft:campfire", 2);
        // m169 40核刷铁机：对表用户 40 核蓝图（litematic 实测 11044 块：白桦栅栏门1856/白桦台阶1804+橡木280/
        // 水源1924/玻璃1010/白桦楼梯968/闪长岩墙952/白桦栅栏680/床120张=3床×40核/村民实体120=3×40核/
        // 火把229/岩浆60击杀舱/船10，÷≈100 取整，床÷10=12 撑核心身份）——119 件 18 种，村民+僵尸双笼照单核。
        bom("sdzjz:mega_iron_farm", "minecraft:villager,minecraft:zombie",
                "minecraft:birch_fence_gate", 18, "minecraft:birch_slab", 18, BED, 12, GL, 10,
                "minecraft:birch_stairs", 10, "minecraft:diorite_wall", 10, "minecraft:birch_fence", 7,
                WB, 6, "minecraft:birch_planks", 5, TRAP, 4, TCH, 3, OSV, 2, LB, 1,
                "minecraft:oak_boat", 1, HOP, 4, CHE, 2);
        bom("sdzjz:iron_smelter", "", FUR, 8, "minecraft:raw_iron", 8, LB, 1, HOP, 8, CHE, 2);
        bom("sdzjz:kelp_farm", "", "minecraft:kelp", 8, WB, 2, PIS, 4, OSV, 4, GL, 16, RSD, 4, HOP, 4, CHE, 2);
        bom("sdzjz:magma_farm", "minecraft:magma_cube", "minecraft:magma_block", 8, "minecraft:iron_bars", 8,
                NBK, 12, "minecraft:magma_cream", 2, HOP, 4, CHE, 2);
        bom("sdzjz:mob_tower", "minecraft:zombie", COB, 48, WB, 4, TRAP, 8, TCH, 8, HOP, 4, CHE, 4); // 通用大黑塔=堆料最多
        // m170 920万船吸刷怪塔：对表用户蓝图（litematic 实测 133507 块：平滑石台阶26494/黑曜石23678+
        // 传送门20064格=门阵运怪/灵魂沙23618/白玻璃16089/云杉告示牌10032/发射器3344+船实体3345=船吸本体/
        // 凋灵玫瑰674击杀/龟蛋150诱饵，÷≈1000 取整，打火石=点门仪式）——116 件 16 种。
        bom("sdzjz:mega_mob_tower", "minecraft:zombie", "minecraft:smooth_stone_slab", 24, OBSI, 20,
                "minecraft:soul_sand", 20, "minecraft:white_stained_glass", 14, "minecraft:spruce_sign", 8,
                HOP, 4, CHE, 4, "minecraft:oak_leaves", 3, "minecraft:dispenser", 3, OSV, 3,
                "minecraft:oak_boat", 3, "minecraft:stone_brick_stairs", 1, "minecraft:wither_rose", 1,
                "minecraft:birch_trapdoor", 1, "minecraft:turtle_egg", 1, "minecraft:flint_and_steel", 1);
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
        // m170 140猪灵交易场：对表用户蓝图（litematic 实测 2972 块：白混凝土740/白玻璃450/漏斗235/
        // 侦测器159/平滑石131/红石火把153/比较器62，÷≈30 取整，金块4=140猪灵的交易本钱标志件）——68 件 15 种。
        bom("sdzjz:mega_piglin_barter", "minecraft:piglin", "minecraft:white_concrete", 12,
                "minecraft:white_stained_glass", 8, HOP, 8, OSV, 5, SST, 4, CHE, 4, RSD, 4,
                "minecraft:gold_block", 4, "minecraft:repeater", 3, "minecraft:dropper", 3,
                "minecraft:redstone_torch", 3, "minecraft:comparator", 2, "minecraft:powered_rail", 2,
                "minecraft:note_block", 1);
        bom("sdzjz:pigman_tower", "minecraft:zombified_piglin", "minecraft:turtle_egg", 4, OBSI, 16, // 龟蛋=原版仇恨诱饵
                TRAP, 8, "minecraft:gold_nugget", 8, HOP, 4, CHE, 4);
        bom("sdzjz:raid_tower", "minecraft:pillager", "minecraft:bell", 1, BED, 1, COB, 32, WB, 2, // 钟+床=一格假村庄
                "minecraft:emerald", 4, HOP, 4, CHE, 4);
        bom("sdzjz:rail_machine", "", "minecraft:iron_ingot", 16, "minecraft:stick", 8, "minecraft:rail", 4,
                FUR, 2, HOP, 4, CHE, 2);
        bom("sdzjz:sand_maker", "", "minecraft:tnt", 2, SND, 8, SST, 16, RSD, 4, HOP, 4, CHE, 2);
        bom("sdzjz:shulker_farm", "minecraft:shulker", "minecraft:purpur_block", 24, "minecraft:end_rod", 4,
                GL, 8, "minecraft:shulker_shell", 2, HOP, 4, CHE, 2);
        bom("sdzjz:slime_farm", "minecraft:slime", COB, 40, "minecraft:iron_block", 4, "minecraft:carved_pumpkin", 1, // 铁块4+南瓜=原版铁傀儡诱饵
                TCH, 8, "minecraft:slime_ball", 2, HOP, 4, CHE, 2);
        // m170 200万史莱姆农场：对表用户双维度蓝图 by tuzier（litematic 实测 5860 块：白玻璃2681/漏斗790/
        // 投掷器400/动力铁轨248/侦测器220/合成器111打包/蓝冰108冰道/凋灵玫瑰36击杀/灵魂沙60，÷≈50 取整；
        // 蓝图内 repeating_command_block×5 属放置辅助不入料）——82 件 17 种。
        bom("sdzjz:mega_slime_farm", "minecraft:slime", "minecraft:white_stained_glass", 24, HOP, 16,
                "minecraft:dropper", 8, "minecraft:powered_rail", 5, OSV, 4, CHE, 4, "minecraft:oak_leaves", 2,
                "minecraft:repeater", 2, "minecraft:crafter", 2, "minecraft:blue_ice", 2,
                "minecraft:white_concrete", 2, "minecraft:wither_rose", 2, "minecraft:composter", 1,
                "minecraft:soul_sand", 1, "minecraft:note_block", 1, "minecraft:slime_block", 1);
        bom("sdzjz:sugarcane_farm", "", "minecraft:sugar_cane", 8, SND, 8, WB, 2, OSV, 8, PIS, 8, RSD, 8,
                SST, 12, HOP, 4, CHE, 2);
        bom("sdzjz:super_smelter", "", FUR, 16, HOP, 16, CHE, 8, "minecraft:coal_block", 4); // 原版超级熔炉阵=炉与漏斗海
        bom("sdzjz:swamp_spawner", "minecraft:bogged", "minecraft:mud", 16, TRAP, 4, WB, 1,
                "minecraft:arrow", 4, HOP, 4, CHE, 2);
        bom("sdzjz:tree_farm", "", "minecraft:oak_sapling", 8, DRT, 16, BM, 16, "minecraft:apple", 2, HOP, 4, CHE, 2);
        bom("sdzjz:wire_brusher", "minecraft:spider", "minecraft:cobweb", 2, COB, 24, WB, 2,
                "minecraft:string", 4, HOP, 4, CHE, 2);
        bom("sdzjz:witch_tower", "minecraft:witch", PLK, 24, "minecraft:cauldron", 1, TRAP, 4, WB, 2, // 锅=原版女巫小屋标配
                BOT, 4, HOP, 4, CHE, 2);
        // m171 沼泽刷怪塔：对表用户 v2 蓝图（litematic 实测 6013 块：平滑石台阶2746/平滑石1159铺台/
        // 白玻璃528/石砖楼梯344/樱花活板门180/岩浆块124击杀层/灵魂营火22/矿车62收集车队/漏斗矿车2，
        // ÷≈50 取整）——83 件 17 种，女巫笼照基础款、锅=女巫身份件。
        bom("sdzjz:mega_witch_tower", "minecraft:witch", "minecraft:smooth_stone_slab", 24, SST, 12,
                "minecraft:white_stained_glass", 8, "minecraft:stone_brick_stairs", 6, RSD, 4, HOP, 4,
                "minecraft:cherry_trapdoor", 4, "minecraft:magma_block", 3, WB, 3, OSV, 2,
                "minecraft:minecart", 2, CHE, 2, "minecraft:white_concrete", 1, "minecraft:soul_campfire", 1,
                "minecraft:packed_ice", 1, "minecraft:cauldron", 1);
        bom("sdzjz:wither_skeleton_farm", "minecraft:wither_skeleton", NBK, 32, "minecraft:soul_sand", 4,
                "minecraft:coal", 4, "minecraft:bone", 2, HOP, 4, CHE, 2);
        bom("sdzjz:chicken_farm", "minecraft:chicken", "minecraft:egg", 8, "minecraft:dispenser", 1, LB, 1, // 熔岩刀=原版全自动鸡场
                GL, 8, SST, 8, HOP, 4, CHE, 2);
        bom("sdzjz:sheep_farm", "minecraft:sheep", "minecraft:dispenser", 4, "minecraft:shears", 4, // 发射器持剪=原版自动薅毛
                "minecraft:grass_block", 8, GL, 8, HOP, 4, CHE, 2);
        bom("sdzjz:cow_farm", "minecraft:cow", "minecraft:wheat", 16, LB, 1, GL, 8, SST, 8,
                "minecraft:leather", 2, HOP, 4, CHE, 2);
        bom("sdzjz:pig_farm", "minecraft:pig", "minecraft:carrot", 16, LB, 1, GL, 8, DRT, 8,
                "minecraft:porkchop", 2, HOP, 4, CHE, 2); // m92
        bom("sdzjz:crop_farm", "", DRT, 16, WB, 1, "minecraft:wheat_seeds", 8, "minecraft:carrot", 4,
                "minecraft:potato", 4, "minecraft:composter", 1, HOP, 5, "minecraft:minecart", 1, CHE, 2); // 漏斗5+矿车=漏斗矿车收菜
        bom("sdzjz:deep_mining_platform", "", "minecraft:diamond", 2, "minecraft:ancient_debris", 2, // 引子:先亲手挖到样本
                "minecraft:tnt", 8, OBSI, 8, "minecraft:rail", 8, "minecraft:minecart", 1, TCH, 8, HOP, 4, CHE, 4);
        bom("sdzjz:archaeology_station", "", "minecraft:brush", 2, "minecraft:echo_shard", 2, // 引子:远古城+藏宝图亲手跑
                "minecraft:heart_of_the_sea", 1, SND, 16, "minecraft:terracotta", 8, HOP, 4, CHE, 4);
        bom("sdzjz:end_expedition_platform", "", "minecraft:ender_eye", 12, "minecraft:dragon_breath", 2, // 眼12=原版找要塞；引子:先亲手打一次龙
                EST, 16, OBSI, 10, BOT, 4, HOP, 4, CHE, 4);
        bom("sdzjz:trial_farm", "", "minecraft:trial_key", 2, "minecraft:ominous_bottle", 1, // 引子:试炼密室+亲手杀袭击队长
                "minecraft:copper_block", 8, "minecraft:copper_bulb", 4, "minecraft:tuff_bricks", 12, HOP, 4, CHE, 4);
        // 逻辑节点小件（灵魂件各异 → 多重集互相唯一；9 件的小多重集也不可能撞机器 BOM）
        addSmall("sdzjz:filter_node", "minecraft:hopper");
        addSmall("sdzjz:sensor_node", "minecraft:comparator");
        addSmall("sdzjz:switch_node", "minecraft:lever");
        addSmall("sdzjz:distributor_node", "minecraft:dropper");
        // m84a 缺口七机
        bom("sdzjz:amethyst_farm", "", "minecraft:amethyst_shard", 4, "minecraft:amethyst_block", 4,
                "minecraft:calcite", 8, "minecraft:smooth_basalt", 8, PIS, 4, OSV, 4, HOP, 4, CHE, 2);
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
                : new ItemStack(Registries.ITEM.get(Identifier.of(r.result)), Math.max(1, r.count()));
    }
}
