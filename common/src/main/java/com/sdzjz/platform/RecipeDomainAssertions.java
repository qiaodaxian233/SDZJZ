package com.sdzjz.platform;

import com.sdzjz.machine.CraftPlanner;
import com.sdzjz.machine.SmeltPlanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * m372 配方域 SPI 行为契约（跨版本行为不变量，作者拍板 A 线）——同一套断言，两个实现：
 * Legacy(1.21.1) GameTest 卅四号喂 LegacyRecipeAccess，Modern(26.2) GameTest 喂
 * ModernRecipeAccess。判官只此一份放 Common（防 26.3/27.x 时测试指数膨胀），版本侧
 * 只提供真实配方表数据。断言全部锚定原版 datapack 事实（工作台/蛋糕/圆石熔石），
 * 不造假配方；纯函数分支（pickStable/maxCrafts 口径）另配合成数据直测。
 *
 * 失败=AssertionError 带中文病灶信息；包装层各自翻译成 GameTest 失败。
 * 七类判定（①~⑤=m372 作者拍板原文序号；⑥⑦=m373 B 线收口随 brew/ench 适配器扩入）：
 *  ① 任意木板 Ingredient：云杉能满足、maxCrafts 算对；
 *  ② 候选组：混料不虚算不重复消费、takeFor 与 maxCrafts 同口径；
 *  ③ 熔炼稳定选序：minecraft 命名空间优先、同空间按 id 字典序、乱序输入结果不变；
 *  ④ Ingredient 枚举口径：tag 全量展开（Modern items() 与 Legacy getMatchingStacks 对齐的可测代理）；
 *  ⑤ 合成残留：craftRemainderOf 与配方 remainders 双口一致；
 *  ⑥ 酿造域全路径：目标药水→正确基础物(水瓶+玻璃瓶)→正确中间配方(地狱疣→粗制)→正确最终产物，
 *     深链(强化+喷溅)最短步数与材料多重集全对，非法/不可达=null；
 *  ⑦ 附魔域：固定目标计划确定性稳定、无效目标(超上限/零级/不存在)全排除、
 *     成本/等级语义不变(青金石=3×级精确、经验对等级严格线性——线性判据不吃 anvilCost 数据值，
 *     26.x 数据包改倍率不误伤)。
 */
public final class RecipeDomainAssertions {

    private RecipeDomainAssertions() { }

    private static void chk(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    /** 全套契约。level=不透明代际世界句柄（服务端），recipes=被测实现。 */
    public static void runAll(Object level, RecipeAccess recipes) {
        // ========== ①④ 任意木板 + Ingredient 枚举口径（工作台=4×#planks tag） ==========
        List<CraftPlanner.Plan> plans = recipes.craftingPlans(level, "minecraft:crafting_table");
        chk(!plans.isEmpty(), "①工作台应有合成配方（枚举口没跑通）");
        CraftPlanner.Plan plan = plans.get(0);
        chk("minecraft:crafting_table".equals(plan.recipeId()),
                "①配方 id 口径漂移：期望 minecraft:crafting_table 实得 " + plan.recipeId());
        chk(plan.resultCount() == 1, "①工作台产量应=1 实得 " + plan.resultCount());
        chk(!plan.groups().isEmpty() && plan.groups().get(0).candidates().size() > 1,
                "①木板槽应解析出多候选（tag #planks），实得组=" + plan.groups());
        List<String> cands = plan.groups().get(0).candidates();
        for (String want : List.of("minecraft:oak_planks", "minecraft:spruce_planks",
                "minecraft:crimson_planks", "minecraft:warped_planks")) {
            chk(cands.contains(want), "④候选集缺 " + want + "（Ingredient 全量枚举口径疑漂移），实得=" + cands);
        }
        chk(new HashSet<>(cands).size() == cands.size(), "④候选集含重复项（去重口径漂移）：" + cands);
        chk(plan.groups().size() == 1 && plan.groups().get(0).count() == 4,
                "①四个木板槽同候选集应合并为一组×4（m343 口径），实得=" + plan.groups());

        // ========== ①② maxCrafts / takeFor 同口径（廿六号原断言，数据换 access 直供） ==========
        Map<String, Long> stock = new HashMap<>();
        stock.put("minecraft:spruce_planks", 64L);
        java.util.function.ToLongFunction<String> stk = k -> stock.getOrDefault(k, 0L);
        chk(CraftPlanner.maxCrafts(plan, 999, stk, true) == 16, "①64 云杉应=16 次（任意木板口径，4 板/次）");
        chk(CraftPlanner.maxCrafts(plan, 999, stk, false) == 0, "①关口径=旧首选行为，云杉不该算数");
        Map<String, Long> taken = CraftPlanner.takeFor(plan, 2, stk,
                (id, amt) -> stock.merge(id, -amt, Long::sum), true);
        chk(taken.getOrDefault("minecraft:spruce_planks", 0L) == 8L
                        && stock.get("minecraft:spruce_planks") == 56L,
                "②实扣 2 次应=8 块云杉且账对上（takeFor 与 maxCrafts 同口径），taken=" + taken + " 余=" + stock);
        chk(CraftPlanner.remaindersOf(taken).isEmpty(), "②木板无容器残留");
        Map<String, Long> mix = new HashMap<>();
        mix.put("minecraft:oak_planks", 3L);
        mix.put("minecraft:spruce_planks", 3L);
        chk(CraftPlanner.maxCrafts(plan, 999, k -> mix.getOrDefault(k, 0L), true) == 1,
                "②3 橡木+3 云杉应=1 次（跨候选合计 6/4，缺 2 不虚算不重复消费）");

        // ========== ⑤ 合成残留双口（milk_bucket→bucket；蛋糕配方 3 桶残留） ==========
        chk("minecraft:bucket".equals(recipes.craftRemainderOf("minecraft:milk_bucket")),
                "⑤craftRemainderOf(milk_bucket) 应=minecraft:bucket 实得 " + recipes.craftRemainderOf("minecraft:milk_bucket"));
        chk(recipes.craftRemainderOf("minecraft:stone") == null, "⑤石头不该有残留");
        List<CraftPlanner.Plan> cakes = recipes.craftingPlans(level, "minecraft:cake");
        chk(!cakes.isEmpty(), "⑤蛋糕应有配方");
        CraftPlanner.Plan cake = cakes.get(0);
        chk(Integer.valueOf(3).equals(cake.needs().get("minecraft:milk_bucket")),
                "⑤蛋糕需求应含 3 桶牛奶，实得 needs=" + cake.needs());
        chk(Integer.valueOf(3).equals(cake.remainders().get("minecraft:bucket")),
                "⑤蛋糕配方残留应=3 空桶（Item 残留口与配方口同源），实得=" + cake.remainders());

        // ========== ③ 熔炼稳定选序（真锚点 + 纯函数乱序不变量） ==========
        Map<String, List<Object[]>> sm = recipes.smeltingCandidates(level);
        List<Object[]> cobble = sm.get("minecraft:cobblestone");
        chk(cobble != null && !cobble.isEmpty(), "③圆石应有熔炼候选（熔炼枚举口没跑通）");
        boolean anchored = false;
        for (Object[] c : cobble) {
            if ("minecraft:stone".equals(c[1]) && Integer.valueOf(1).equals(c[2])) { anchored = true; break; }
        }
        chk(anchored, "③圆石候选里应有 石头×1 锚点，实得=" + dump(cobble));
        Object[] w1 = SmeltPlanner.pickStable(cobble);
        List<Object[]> rev = new ArrayList<>(cobble);
        Collections.reverse(rev);
        Object[] w2 = SmeltPlanner.pickStable(rev);
        chk(w1[0].equals(w2[0]), "③真候选乱序后选序应不变：正序=" + w1[0] + " 倒序=" + w2[0]);
        // 纯函数合成数据：命名空间优先 + 同空间 id 字典序，均乱序不变（m346/廿八号口径）
        List<Object[]> syn = new ArrayList<>();
        syn.add(new Object[]{"zmod:aaa", "minecraft:stone", 1});
        syn.add(new Object[]{"minecraft:zzz", "minecraft:stone", 1});
        syn.add(new Object[]{"minecraft:aaa", "minecraft:stone", 1});
        chk("minecraft:aaa".equals(SmeltPlanner.pickStable(syn)[0]),
                "③选序应=minecraft 优先+同空间 id 字典序（期望 minecraft:aaa）");
        Collections.reverse(syn);
        chk("minecraft:aaa".equals(SmeltPlanner.pickStable(syn)[0]), "③合成候选倒序输入选序应不变");

        // ========== ⑥ 酿造域全路径（原版锚点：迅捷=水瓶→粗制(地狱疣)→迅捷(糖)） ==========
        com.sdzjz.machine.BrewPlanner.Plan bp = recipes.brewingPlan(level, "minecraft:swiftness|p");
        chk(bp != null, "⑥迅捷药水应可达（酿造 BFS 没跑通）");
        chk(bp.steps() == 2, "⑥迅捷路径应恰 2 步（基础物→中间粗制→最终），实得 " + bp.steps());
        chk(Integer.valueOf(com.sdzjz.machine.BrewPlanner.BOTTLES_PER_BATCH).equals(bp.needs().get("minecraft:glass_bottle")),
                "⑥基础物应含玻璃瓶×每批瓶数，实得 needs=" + bp.needs());
        chk(Integer.valueOf(1).equals(bp.needs().get("minecraft:nether_wart")),
                "⑥中间配方应耗地狱疣×1（水瓶→粗制），实得 needs=" + bp.needs());
        chk(Integer.valueOf(1).equals(bp.needs().get("minecraft:sugar")),
                "⑥最终一步应耗糖×1（粗制→迅捷），实得 needs=" + bp.needs());
        chk(bp.needs().size() == 3, "⑥迅捷 needs 应恰 3 项（瓶/疣/糖），实得=" + bp.needs());
        chk(bp.result() != null, "⑥目标产物样板句柄不该为空");
        // 深链：强化迅捷·喷溅=疣+糖+荧石粉+火药，BFS 最短 4 步（材料多重集与步数全对=整条路径判官）
        com.sdzjz.machine.BrewPlanner.Plan dp = recipes.brewingPlan(level, "minecraft:strong_swiftness|s");
        chk(dp != null, "⑥强化迅捷喷溅应可达");
        chk(dp.steps() == 4, "⑥深链最短应恰 4 步（疣/糖/荧石/火药各一步），实得 " + dp.steps());
        for (String want : List.of("minecraft:nether_wart", "minecraft:sugar",
                "minecraft:glowstone_dust", "minecraft:gunpowder")) {
            chk(Integer.valueOf(1).equals(dp.needs().get(want)),
                    "⑥深链材料应含 " + want + "×1，实得 needs=" + dp.needs());
        }
        chk(recipes.brewingPlan(level, "minecraft:water|p") == null, "⑥目标=水瓶（0 步）应判无酿造意义=null");
        chk(recipes.brewingPlan(level, "不是目标串") == null, "⑥非法目标串应=null");
        chk(recipes.brewTargetStack("minecraft:swiftness|p") != null, "⑥合法目标样板栈不该为空");
        chk(recipes.brewTargetStack("minecraft:swiftness|x") == null, "⑥非法形态码应=null");

        // ========== ⑦ 附魔域（原版锚点：锋利上限 5；线性判据不吃 anvilCost 数据值） ==========
        com.sdzjz.machine.EnchantPlanner.Plan e5 = recipes.enchantingPlan(level, "minecraft:sharpness|5");
        chk(e5 != null && e5.result() != null, "⑦锋利V 计划与产物句柄不该为空");
        chk(Integer.valueOf(1).equals(e5.needs().get(com.sdzjz.machine.EnchantPlanner.BOOK_ID))
                        && Integer.valueOf(5 * com.sdzjz.machine.EnchantPlanner.LAPIS_PER_LEVEL)
                                .equals(e5.needs().get(com.sdzjz.machine.EnchantPlanner.LAPIS_ID))
                        && e5.needs().size() == 2,
                "⑦锋利V 消耗应=书×1+青金石×15 恰两项，实得 needs=" + e5.needs());
        com.sdzjz.machine.EnchantPlanner.Plan e1 = recipes.enchantingPlan(level, "minecraft:sharpness|1");
        chk(e1 != null && e1.xpCost() > 0 && e1.xpCost() * 5 == e5.xpCost(),
                "⑦经验成本应对等级严格线性（书倍率×等级×单价语义），I=" + (e1 == null ? "null" : e1.xpCost())
                        + " V=" + e5.xpCost());
        com.sdzjz.machine.EnchantPlanner.Plan e5b = recipes.enchantingPlan(level, "minecraft:sharpness|5");
        chk(e5b != null && e5b.xpCost() == e5.xpCost() && e5b.needs().equals(e5.needs()),
                "⑦固定目标重复解析应确定性稳定");
        chk(recipes.enchantingPlan(level, "minecraft:sharpness|6") == null, "⑦越过 maxLevel 应排除=null");
        chk(recipes.enchantingPlan(level, "minecraft:sharpness|0") == null, "⑦零级应排除=null");
        chk(recipes.enchantingPlan(level, "minecraft:sdzjz_no_such_ench|1") == null, "⑦不存在的附魔应排除=null");
        chk(recipes.enchantTargetStack(level, "minecraft:sharpness|5") != null, "⑦目标样板书不该为空");
        chk(recipes.enchantTargetName(level, "minecraft:sharpness|5") != null, "⑦展示名句柄不该为空");
        chk(recipes.enchantTargetStack(level, "minecraft:sharpness|9") == null, "⑦样板口越界应=null");
    }

    private static String dump(List<Object[]> l) {
        StringBuilder sb = new StringBuilder("[");
        for (Object[] o : l) sb.append(java.util.Arrays.toString(o)).append(' ');
        return sb.append(']').toString();
    }
}
