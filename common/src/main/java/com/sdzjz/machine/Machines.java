package com.sdzjz.machine;

import java.util.List;

/** 内置机器表。单产用 def()，多掉落用 defMulti()。全为农场类（免费出）。 */
public final class Machines {
    private Machines() {}

    /** m173 万能熔炉族：基础款+1728工程款共用同一 tick 分支/链式需求/二级界面/tooltip（全库特判点统一走这）。 */
    public static boolean smelterFamily(String id) {
        return "super_smelter".equals(id) || "mega_super_smelter".equals(id);
    }
    /** m173 熔炉族单机倍率：工程款=1728炉/基础16炉=×108，capacity 直乘（升级另乘）。 */
    public static long smelterUnit(String id) {
        return "mega_super_smelter".equals(id) ? 108L : 1L;
    }
    /** m173 全自动农场族单机倍率：多种植物农村工程款=×32（对表蓝图 1968 耕地≈基础款一层×32），产量直乘（升级另乘）。 */
    public static long cropUnit(String id) {
        return "mega_crop_farm".equals(id) ? 32L : 1L;
    }

    // ---- 自动合成机（目标动态，画布上设置；此 def 仅作占位元数据）----
    public static final MachineDef AUTO_CRAFTER = new MachineDef("auto_crafter", List.of(), 40, false, List.of());
    /** 酿造塔（m131b）：目标药水在画布节点徽章选择，配方由 BrewPlanner 从原版酿造注册表解析。 */
    public static final MachineDef BREWING_TOWER = new MachineDef("brewing_tower", List.of(), 40, false, List.of());
    /** 附魔工厂（m132）：目标附魔+等级在画布节点徽章选择，成本由 EnchantPlanner 解析（经验从核心经验池扣）。 */
    public static final MachineDef ENCHANT_FACTORY = new MachineDef("enchant_factory", List.of(), 40, false, List.of());
    /** 无限复制机（m334）：目标物品画布徽章选（全物品注册表），母本压阵+经验池付费复制（组件不复制）。 */
    public static final MachineDef DUPLICATOR = new MachineDef("duplicator", List.of(), 40, false, List.of());
    /** 区块移除器（m376，区块机器线第一台）：世界内右键绑定目标区块，自顶向下整区块移除，
     *  掉落物进输出（tick 专属分支，掉落表空；基岩不动/方块实体默认跳过/仅同维度）。 */
    public static final MachineDef CHUNK_REMOVER = new MachineDef("chunk_remover", List.of(), 40, false, List.of());
    /** 区块过滤器（m377）：区块移除器的规则挂件——画布连线即生效（方向随意，多台 AND 叠加），
     *  方块名单(fl/fb 复用，空=不限)+Y 五挡循环(zp)；本体不收不产不转发（tick 恒待机）。 */
    public static final MachineDef CHUNK_FILTER = new MachineDef("chunk_filter", List.of(), 5, false, List.of());
    /** 虚空处理器（m378）：垃圾炼经验——吞路由进来的物品按汇率入本核心经验池（垃圾桶升级款，
     *  白名单空=连啥炼啥；直连仓不抽/逻辑节点转接=授权照拉，m150/m153 同律）。 */
    public static final MachineDef VOID_PROCESSOR = new MachineDef("void_processor", List.of(), 5, false, List.of());

    // ===== m135 G组杂项（原版生存精准采集也拿不到的三件）→ m143 合并为一台（用户拍板：概念图一图=一机）=====
    /** G组杂项机器：蛛网+孢子花+紫水晶全套一台出（三仓一体，图标=概念图整图）。 */
    public static final MachineDef G_MISC_MACHINE = defMulti("g_misc_machine", 40,
            drop("minecraft:cobweb", 1, 2),
            drop("minecraft:spore_blossom", 1, 1),
            drop("minecraft:budding_amethyst", 1, 1, 0.15f),
            drop("minecraft:small_amethyst_bud", 1, 1, 0.25f),
            drop("minecraft:medium_amethyst_bud", 1, 1, 0.25f),
            drop("minecraft:large_amethyst_bud", 1, 1, 0.25f),
            drop("minecraft:amethyst_cluster", 1, 1, 0.3f));

    // ===== m137 凋灵机+四副机 → m143 合并为一台（概念图本就是一座含四副仓的综合体）=====
    /** 凋灵机：主产下界之星（0.04 压最稀档；引子含星本体=先亲手打一次凋灵），副仓四线
     *  （三色青蛙灯/山羊角8变体组件特判键在掉落物id照常/犰狳鳞/嗅探兽双花籽）同台齐出。 */
    public static final MachineDef WITHER_FARM = defMulti("wither_farm", 40,
            drop("minecraft:nether_star", 1, 1, 0.04f),
            drop("minecraft:ochre_froglight", 1, 2, 0.5f),
            drop("minecraft:verdant_froglight", 1, 2, 0.5f),
            drop("minecraft:pearlescent_froglight", 1, 2, 0.5f),
            drop("minecraft:goat_horn", 1, 1, 0.25f),
            drop("minecraft:armadillo_scute", 1, 2, 0.8f),
            drop("minecraft:torchflower_seeds", 1, 1, 0.5f),
            drop("minecraft:pitcher_pod", 1, 1, 0.5f));

    // ===== m138 幽匿线三塔（吃核心经验池）→ m143 合并为一台（三张掉落表齐滚，经验单价=2+9+9合计）=====
    /** 幽匿线：散块流水+催化体/传感器/尖啸体同台齐出；20经验/轮（=原三台各跑一轮的合计，总账不变）。 */
    public static final MachineDef SCULK_LINE = defMulti("sculk_line", 40,
            drop("minecraft:sculk", 4, 8),
            drop("minecraft:sculk_vein", 2, 4),
            drop("minecraft:sculk_catalyst", 1, 1, 0.08f),
            drop("minecraft:sculk_sensor", 1, 1, 0.6f),
            drop("minecraft:sculk_shrieker", 1, 1, 0.5f));

    /** m139 砂轮祛魔机：扫源仓附魔书磨成裸书+经验回核心池（tick 专属分支，掉落表空）。 */
    public static final MachineDef GRINDSTONE_RECYCLER = new MachineDef("grindstone_recycler", List.of(), 40, false, List.of());
    /** m145 村民打折机：自动治愈——吃网络金苹果，给共网交易所里的合同升折扣（tick 专属分支，掉落表空）。 */
    public static final MachineDef VILLAGER_DISCOUNT_MACHINE = new MachineDef("villager_discount_machine", List.of(), 40, false, List.of());
    /** m146 村民无限交易机：目标交易在画布徽章选择（TradePlanner 解析"职业|序号"），tick 专属分支，掉落表空。 */
    public static final MachineDef VILLAGER_TRADER = new MachineDef("villager_trader", List.of(), 40, false, List.of());
    /** 画布逻辑节点：本体不产不耗，逻辑在结构核心 tick 里（过滤分流/闸门）。 */
    public static final MachineDef FILTER_NODE = new MachineDef("filter_node", List.of(), 5, false, List.of());
    /** m154 抽取节点：点击启停的主动泵——无条件抽上游仓沿线推走（不问链式需求）。 */
    public static final MachineDef EXTRACTOR_NODE = new MachineDef("extractor_node", List.of(), 5, false, List.of());
    /** m150 垃圾桶节点：连啥吞啥的终点节点（distribute 最低优先级）。 */
    public static final MachineDef TRASH_NODE = new MachineDef("trash_node", List.of(), 5, false, List.of());
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
    /** m174 动物农场（无尽贪婪投影新线）：五畜综合繁殖击杀舱，对表用户 326 块蓝图
     *  （7×12×13 微型舱：兔21/猪13/牛6/羊5/鸡1 五物种+发射器6水流循环）；兔子线全库首补
     *  （兔肉/兔皮/兔子脚→跳跃药水链）。约 11.6件/30t ≈ 2.8万件/h。 */
    public static final MachineDef ANIMAL_FARM = defMulti("animal_farm", 30,
            drop("minecraft:beef", 1, 2), drop("minecraft:porkchop", 1, 2),
            drop("minecraft:mutton", 1, 2), drop("minecraft:chicken", 1, 2),
            drop("minecraft:white_wool", 1, 2), drop("minecraft:leather", 0, 2, 0.6f),
            drop("minecraft:feather", 0, 2, 0.6f), drop("minecraft:egg", 0, 1, 0.5f),
            drop("minecraft:rabbit", 0, 1, 0.7f), drop("minecraft:rabbit_hide", 0, 1, 0.5f),
            drop("minecraft:rabbit_foot", 0, 1, 0.02f));
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
    /** m174 弱加载盾构机（无尽贪婪投影新线）：TNT 连爆掘进,对表 Dark牌2025版蓝图（269 块,
     *  86×86×11 音符盒弱加载环+矿车2手动摆放=蓝图点名）;同包 1.17版/巨型弱加载(10013块)/
     *  未使用盾构机(1047块) 三张=同族版本档不单列。吃 TNT 1/20t 出地形方块
     *  ≈52块/s≈18.8万块/h（燧石0.3走砂砾梗）。 */
    public static final MachineDef TUNNEL_BORER = defConsume("tunnel_borer", 20,
            List.of(in("minecraft:tnt", 1)),
            drop("minecraft:cobblestone", 16, 24), drop("minecraft:cobbled_deepslate", 16, 24),
            drop("minecraft:tuff", 4, 8), drop("minecraft:gravel", 2, 4),
            drop("minecraft:dirt", 2, 4), drop("minecraft:flint", 0, 1, 0.3f));
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
    /** m174 屠龙炮（无尽贪婪投影新线,终局工程）：末地复活龙循环击杀,对表用户 10540 块蓝图
     *  （82×90×56:遮光玻璃2160/TNT320/标靶258/末地烛502;基岩2108=末地场地自带不计料;船1=标志件）。
     *  吃 末影水晶4+玻璃瓶8/200t 出龙息8≈2880息/h;经验500/轮（原版复活龙击杀经验）
     *  =18万xp/h 全库最强经验引擎——水晶链自咬合:恶魂塔泪+末影珍珠+玻璃。 */
    public static final MachineDef DRAGON_CANNON = defConsume("dragon_cannon", 200,
            List.of(in("minecraft:end_crystal", 4), in("minecraft:glass_bottle", 8)),
            drop("minecraft:dragon_breath", 8, 8),
            drop("minecraft:dragon_egg", 1, 1, 0.005f)); // m190 龙蛋（用户点名"屠龙炮不该掉龙蛋?"）:
            // 原版仅首杀掉蛋、复活龙不掉——终局纪念品按 heavy_core 极稀待遇,200t 周期下≈1.8枚/h
    public static final MachineDef TRIAL_FARM = defMulti("trial_farm", 40,
            drop("minecraft:trial_key", 1, 1, 0.25f),
            drop("minecraft:ominous_trial_key", 1, 1, 0.06f),
            drop("minecraft:ominous_bottle", 1, 1, 0.15f),
            drop("minecraft:flow_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:guster_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:scrape_pottery_sherd", 1, 1, 0.04f),
            drop("minecraft:heavy_core", 1, 1, 0.008f));
    /** m172 试炼大厅农场（工程款）：七项=基础款×24（对表蓝图 trial_spawner×24；大厅+地狱部分两张
     *  合账），均值14.1件/40t≈2.5万件/h；重锤核心 0.19/周期换算保稀有。 */
    public static final MachineDef MEGA_TRIAL_FARM = defMulti("mega_trial_farm", 40,
            drop("minecraft:trial_key", 3, 9),
            drop("minecraft:ominous_trial_key", 0, 3),
            drop("minecraft:ominous_bottle", 0, 7),
            drop("minecraft:flow_pottery_sherd", 0, 2),
            drop("minecraft:guster_pottery_sherd", 0, 2),
            drop("minecraft:scrape_pottery_sherd", 0, 2),
            drop("minecraft:heavy_core", 1, 1, 0.19f));
    /** 全自动农场：产出按所选作物（CropFarms 表），此处仅占位定义。 */
    public static final MachineDef CROP_FARM = new MachineDef("crop_farm", List.of(), 40, false, List.of());
    /** m173 多种植物农业塔（全自动农场工程款）：同一 CropFarmItem 分支，产量×32
     *  （对表用户"多种植物农村"蓝图：21×51×21 塔楼 15193 块，耕地1968格=四作物各316+瓜/南瓜茎
     *  各351+仙人掌81+苔藓1256，选作物玩法照基础款）。 */
    public static final MachineDef MEGA_CROP_FARM = new MachineDef("mega_crop_farm", List.of(), 40, false, List.of());

    // ---- 单产农场 ----
    public static final MachineDef WIRE_BRUSHER   = def("wire_brusher",   "minecraft:string",       1, 20);
    public static final MachineDef COBBLE_MAKER   = def("cobble_maker",   "minecraft:cobblestone",  1, 10);
    /** m168 百万刷石机：722/10t=72.2/t≈520万/h，对表用户 520万刷石机 litematic 实测产能（升级另乘）。 */
    public static final MachineDef MEGA_COBBLE_MAKER = def("mega_cobble_maker", "minecraft:cobblestone", 722, 10);
    public static final MachineDef BONE_FARM      = def("bone_farm",      "minecraft:bone",         1, 20);
    public static final MachineDef GUNPOWDER_FARM = def("gunpowder_farm", "minecraft:gunpowder",    1, 25);
    public static final MachineDef FLESH_FARM     = def("flesh_farm",     "minecraft:rotten_flesh", 1, 20);
    public static final MachineDef PEARL_FARM     = def("pearl_farm",     "minecraft:ender_pearl",  1, 30);
    public static final MachineDef SLIME_FARM     = def("slime_farm",     "minecraft:slime_ball",   1, 25);
    /** m170 200万史莱姆农场：695/25t≈200.2万/h，对表用户双维度蓝图（升级另乘）。 */
    public static final MachineDef MEGA_SLIME_FARM = def("mega_slime_farm", "minecraft:slime_ball", 695, 25);
    public static final MachineDef IRON_FARM      = def("iron_farm",      "minecraft:iron_ingot",   1, 40);
    /** m169 40核刷铁机：40/40t=单核×40=7.2万/h，对表用户 40 核蓝图（120村民/120床=3+3×40核，升级另乘）。 */
    public static final MachineDef MEGA_IRON_FARM = def("mega_iron_farm", "minecraft:iron_ingot", 40, 40);
    /** m172 160核刷铁机：160/40t=单核×160=28.8万/h，对表用户 160 核蓝图（村民573/床495张≈3×160核+
     *  繁殖余量，传送门运傀儡+凋灵玫瑰击杀；合成收集模块合账，见 DEVLOG m172）。 */
    public static final MachineDef IRON_FARM_160 = def("iron_farm_160", "minecraft:iron_ingot", 160, 40);
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
    /** m171 沼泽刷怪塔：七联女巫掉落=基础款×64，均值352件/25t≈101.4万件/h（蓝图无标称产能，
     *  按台面规模取 ×64，要调报个倍数一行改）。 */
    public static final MachineDef MEGA_WITCH_TOWER = defMulti("mega_witch_tower", 25,
            drop("minecraft:redstone", 0, 128), drop("minecraft:glowstone_dust", 0, 128), drop("minecraft:sugar", 0, 128),
            drop("minecraft:glass_bottle", 0, 64), drop("minecraft:gunpowder", 0, 64), drop("minecraft:stick", 0, 128),
            drop("minecraft:spider_eye", 0, 64));

    public static final MachineDef GUARDIAN_FARM = defMulti("guardian_farm", 25,
            drop("minecraft:prismarine_shard", 0, 2), drop("minecraft:prismarine_crystals", 0, 1),
            drop("minecraft:cod", 0, 1));
    /** m171 强力守卫者农场：三联掉落=基础款×16，均值32件/25t≈9.2万件/h（对表门运式守卫者农场量级）。 */
    public static final MachineDef MEGA_GUARDIAN_FARM = defMulti("mega_guardian_farm", 25,
            drop("minecraft:prismarine_shard", 0, 32), drop("minecraft:prismarine_crystals", 0, 16),
            drop("minecraft:cod", 0, 16));

    public static final MachineDef MAGMA_FARM    = def("magma_farm",   "minecraft:magma_cream",   1, 25);
    public static final MachineDef SHULKER_FARM  = def("shulker_farm", "minecraft:shulker_shell", 1, 60);
    public static final MachineDef RAID_TOWER = defMulti("raid_tower", 30,
            drop("minecraft:emerald", 0, 3), drop("minecraft:arrow", 0, 2), drop("minecraft:totem_of_undying", 1, 1, 0.1f));
    /** m172 百万劫掠塔：三项掉落=基础款×160，均值416件/30t≈99.8万件/h 对表蓝图名"百万"（村民16
     *  假村庄+劫掠兽入栏；图腾均值16/周期照 m170 换算 chance=1 区间出货平滑）。 */
    public static final MachineDef MEGA_RAID_TOWER = defMulti("mega_raid_tower", 30,
            drop("minecraft:emerald", 0, 480), drop("minecraft:arrow", 0, 320), drop("minecraft:totem_of_undying", 8, 24));
    public static final MachineDef PIGMAN_TOWER = defMulti("pigman_tower", 25,
            drop("minecraft:gold_nugget", 1, 3), drop("minecraft:rotten_flesh", 0, 1),
            drop("minecraft:gold_ingot", 0, 1, 0.05f), drop("minecraft:golden_sword", 0, 1, 0.05f));
    /** m172 80万猪人塔：四项掉落=基础款×110，均值281件/25t≈80.9万件/h（对表用户"80w"蓝图，
     *  传送门98208格门阵刷猪人；低概率项换算 chance=1 均值等价区间照 m170 出货平滑，要调报倍数一行改）。 */
    public static final MachineDef MEGA_PIGMAN_TOWER = defMulti("mega_pigman_tower", 25,
            drop("minecraft:gold_nugget", 110, 330), drop("minecraft:rotten_flesh", 0, 110),
            drop("minecraft:gold_ingot", 0, 6), drop("minecraft:golden_sword", 0, 6));

    // ---- 消耗类（从连接的数据面板取料）----
    /** m174 疣猪兽农场（无尽贪婪投影新线）：诡异菌诱捕双层击杀舱,对表用户"疣猪兽农场-单人双层"
     *  蓝图（587 块,15×15×9:诡异菌16=诱饵标志件/火把48防僵尸化/岩浆9击杀）。
     *  猪排2-4+皮革0.5/25t≈1万件/h;经验5/轮（疣猪兽原版击杀经验,照刷怪类）。 */
    public static final MachineDef HOGLIN_FARM = defMulti("hoglin_farm", 25,
            drop("minecraft:porkchop", 2, 4), drop("minecraft:leather", 0, 1, 0.5f));
    public static final MachineDef PIGLIN_BARTER = defConsume("piglin_barter", 30,
            java.util.List.of(in("minecraft:gold_ingot", 1)),
            drop("minecraft:ender_pearl", 1, 1, 0.15f), drop("minecraft:string", 1, 3, 0.2f),
            drop("minecraft:quartz", 2, 4, 0.2f), drop("minecraft:glowstone_dust", 2, 4, 0.2f),
            drop("minecraft:obsidian", 1, 1, 0.1f), drop("minecraft:soul_sand", 2, 4, 0.2f),
            drop("minecraft:magma_cream", 1, 1, 0.1f), drop("minecraft:leather", 1, 1, 0.15f));

    /** m170 140猪灵交易场：吃 70金/30t=16.8万金/h（猪人塔喂），产出均值189件/周期≈45.4万件/h
     *  对表用户"46万"蓝图；八项池与基础款同表、区间×70 换算成 chance=1 的均值等价区间（出货更平滑）。 */
    public static final MachineDef MEGA_PIGLIN_BARTER = defConsume("mega_piglin_barter", 30,
            java.util.List.of(in("minecraft:gold_ingot", 70)),
            drop("minecraft:ender_pearl", 0, 21), drop("minecraft:string", 14, 42),
            drop("minecraft:quartz", 21, 63), drop("minecraft:glowstone_dust", 21, 63),
            drop("minecraft:obsidian", 0, 14), drop("minecraft:soul_sand", 21, 63),
            drop("minecraft:magma_cream", 0, 14), drop("minecraft:leather", 0, 21));

    // ---- 追加机器(m22) ----
    public static final MachineDef CACTUS_FARM = def("cactus_farm", "minecraft:cactus", 2, 20);
    public static final MachineDef NETHER_WART_FARM = def("nether_wart_farm", "minecraft:nether_wart", 1, 25);
    public static final MachineDef KELP_FARM = def("kelp_farm", "minecraft:kelp", 2, 20);
    public static final MachineDef BLAZE_FARM = def("blaze_farm", "minecraft:blaze_rod", 1, 30);
    public static final MachineDef WITHER_SKELETON_FARM = defMulti("wither_skeleton_farm", 30, drop("minecraft:bone", 1, 2), drop("minecraft:coal", 0, 1, 0.5f), drop("minecraft:wither_skeleton_skull", 0, 1, 0.025f));
    /** m172 凋零骷髅农场（工程款）：三项掉落=基础款×64，均值113件/30t≈27万件/h、凋骷头均值0.8/周期
     *  （对表用户蓝图：凋灵玫瑰×2166铺土击杀层+铁傀儡×15仇恨+传送门运怪，×64 按玫瑰层规模取）。 */
    public static final MachineDef MEGA_WITHER_SKELETON_FARM = defMulti("mega_wither_skeleton_farm", 30,
            drop("minecraft:bone", 64, 128), drop("minecraft:coal", 0, 32),
            drop("minecraft:wither_skeleton_skull", 1, 1, 0.8f));
    /** m174 凋灵玫瑰农场（无尽贪婪投影新线）：圈养凋灵击杀铁傀儡产玫瑰,对表用户"26k凋灵玫瑰
     *  农场"蓝图（3632 块,53×53×69:末地石砖1562/白玻璃1359/箱64/漏斗48;实体=铁傀儡+凋灵+矿车）。
     *  玫瑰 12-17/40t 均值14.5≈2.61万/h 对表"26k";凋灵召唤料=灵魂沙4+凋骷头3 进 BOM
     *  与凋骷农场自咬合;铁傀儡原版0经验不入表。 */
    public static final MachineDef WITHER_ROSE_FARM = defMulti("wither_rose_farm", 40,
            drop("minecraft:wither_rose", 12, 17));
    public static final MachineDef HONEY_FARM = defMulti("honey_farm", 40, drop("minecraft:honeycomb", 0, 1, 0.7f), drop("minecraft:honey_bottle", 0, 1, 0.5f));
    /** m172 蜜脾农场（工程款）：两项=基础款×92（对表蓝图 92 组"侦测器+发射器持剪"采收单元），
     *  均值55件/40t≈9.9万件/h，换算 chance=1 均值等价区间。 */
    public static final MachineDef MEGA_HONEY_FARM = defMulti("mega_honey_farm", 40,
            drop("minecraft:honeycomb", 16, 48), drop("minecraft:honey_bottle", 12, 34));
    public static final MachineDef IRON_SMELTER = defConsume("iron_smelter", 20, List.of(in("minecraft:raw_iron", 1)), drop("minecraft:iron_ingot", 1, 1));
    public static final MachineDef GOLD_SMELTER = defConsume("gold_smelter", 20, List.of(in("minecraft:raw_gold", 1)), drop("minecraft:gold_ingot", 1, 1));
    public static final MachineDef CHARCOAL_KILN = defConsume("charcoal_kiln", 20, List.of(in("minecraft:oak_log", 1)), drop("minecraft:charcoal", 1, 1));
    public static final MachineDef GLASS_KILN = defConsume("glass_kiln", 15, List.of(in("minecraft:sand", 1)), drop("minecraft:glass", 1, 1));

    // ---- 追加机器(m31) ----
    public static final MachineDef RAIL_MACHINE = def("rail_machine", "minecraft:rail", 2, 20);
    public static final MachineDef CARPET_MACHINE = def("carpet_machine", "minecraft:white_carpet", 2, 20);
    public static final MachineDef MOB_TOWER = defMulti("mob_tower", 25, drop("minecraft:bone", 0, 2), drop("minecraft:gunpowder", 0, 1), drop("minecraft:rotten_flesh", 0, 2), drop("minecraft:string", 0, 2), drop("minecraft:arrow", 0, 1));
    /** m170 920万船吸刷怪塔：五联掉落=基础款×800，均值3200件/25t=128件/t≈921.6万件/h 对表蓝图。 */
    public static final MachineDef MEGA_MOB_TOWER = defMulti("mega_mob_tower", 25, drop("minecraft:bone", 0, 1600), drop("minecraft:gunpowder", 0, 800), drop("minecraft:rotten_flesh", 0, 1600), drop("minecraft:string", 0, 1600), drop("minecraft:arrow", 0, 800));
    public static final MachineDef NETHER_TREE_FARM = defMulti("nether_tree_farm", 30, drop("minecraft:crimson_stem", 1, 2), drop("minecraft:warped_stem", 0, 1), drop("minecraft:nether_wart_block", 0, 1, 0.2f), drop("minecraft:shroomlight", 0, 1, 0.1f));
    public static final MachineDef CHORUS_FARM = def("chorus_farm", "minecraft:chorus_fruit", 1, 30);
    public static final MachineDef DROWNED_TOWER = defMulti("drowned_tower", 30, drop("minecraft:rotten_flesh", 0, 2), drop("minecraft:copper_ingot", 0, 1, 0.5f), drop("minecraft:nautilus_shell", 0, 1, 0.03f), drop("minecraft:trident", 0, 1, 0.015f));
    /** m172 僵尸增援溺尸塔：四项掉落=基础款×32，均值40.7件/30t≈9.8万件/h（蓝图无标称产能按台面
     *  规模取 ×32；增援种子是僵尸、进水才转溺尸——笼子照此要僵尸；三叉戟/鹦鹉螺按均值等价换算）。 */
    public static final MachineDef MEGA_DROWNED_TOWER = defMulti("mega_drowned_tower", 30,
            drop("minecraft:rotten_flesh", 0, 64), drop("minecraft:copper_ingot", 0, 16),
            drop("minecraft:nautilus_shell", 0, 1, 0.96f), drop("minecraft:trident", 0, 1, 0.48f));
    /** m174 龙池杀凋机（无尽贪婪投影新线,终局工程）：末地传送门基岩位循环召唤+压杀凋灵,
     *  对表用户 150 块蓝图（11×11×22:基岩41+末地传送门20=场地自带不计料;灵魂沙13/发射器3
     *  放头/漏斗矿车1收账）。吃 灵魂沙4+凋骷头3/100t 出下界之星1=720星/h,比凋灵机(≈50s/星)
     *  快10倍但照付召唤料——免费慢档与付费快档双轨;经验50/轮同凋灵机。 */
    public static final MachineDef WITHER_KILLER = defConsume("wither_killer", 100,
            List.of(in("minecraft:soul_sand", 4), in("minecraft:wither_skeleton_skull", 3)),
            drop("minecraft:nether_star", 1, 1));
    /** 万能熔炉：运行时走 SmeltPlanner 原版熔炼表（接什么烧什么），此处 inputs/outputs 仅占位。 */
    public static final MachineDef SUPER_SMELTER = defConsume("super_smelter", 20, List.of(), drop("minecraft:iron_ingot", 1, 1));
    /** m173 万级熔炉阵（1728熔炉背包版工程款）：同一万能熔炉分支，capacity×108=每周期 6912 件
     *  （对表蓝图 1728 炉/基础款 16 炉；熔炼经验照旧 0.1/件——放大的是炉数不是单件经验）。 */
    public static final MachineDef MEGA_SUPER_SMELTER = defConsume("mega_super_smelter", 20, List.of(), drop("minecraft:iron_ingot", 1, 1));

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
    /** m172 紫水晶农场（工程款）：碎片=基础款×128，均值256/40t≈46.1万/h（对表用户 72556 块巨构蓝图：
     *  母岩907/晶簇1509/苔藓飞行机骨架31065，按母岩规模取 ×128，要调报倍数一行改）。 */
    public static final MachineDef MEGA_AMETHYST_FARM = defMulti("mega_amethyst_farm", 40,
            drop("minecraft:amethyst_shard", 128, 384));
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
    /** m172 鳕鱼鲑鱼农场（水生刷怪工程款）：鱼类三项=钓鱼机×64 + 墨囊（蓝图有鱿鱼缸），
     *  均值≈90件/60t≈10.8万件/h；宝藏项（鹦鹉螺/命名牌/鞍）保持基础款概率不放大——工程放大的是
     *  刷鱼量不是钓鱼运气。 */
    public static final MachineDef MEGA_FISHING_MACHINE = defMulti("mega_fishing_machine", 60,
            drop("minecraft:cod", 32, 96), drop("minecraft:salmon", 0, 26),
            drop("minecraft:pufferfish", 0, 10), drop("minecraft:ink_sac", 0, 16),
            drop("minecraft:nautilus_shell", 0, 1, 0.03f),
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
