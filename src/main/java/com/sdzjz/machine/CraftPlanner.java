package com.sdzjz.machine;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动合成机的配方解析：给定目标物品 id，从原版合成配方表解析出
 * 每次合成的材料清单（多重集）、单次产量、以及残留容器（如桶）。
 * m234：同一目标可能有多条配方（装 ProjectEF 后金锭被"贤者之石+铁"抢注、原版还有 9 金粒/金块两条）——
 * 改为**全候选缓存**：minecraft 命名空间排前、同空间按配方 id 字典序（稳定可复现）；生产端拿到库存
 * 视图后用 pick 挑第一个可满足的候选，都不满足回退第一候选（缺料就按原版材料报，玩家看得懂）。
 * m343：修"槽位替代材料被拍死成首选"的真实语义 bug（外部审计 P0）——原 resolveAll 对每个
 * Ingredient 只取 getMatchingStacks()[0]（任意木板→只认橡木板），仓里 62 组云杉木板照样报缺料。
 * 现 Plan 增设 groups：同候选集的槽位合并成组（候选序=原版 matching 序，首选即旧口径），
 * 计数/扣料/选配方/链需求/accepts 全走候选组：组内候选按序贪心取用，跨组共享候选经虚拟扣减
 * 绝不重复计数（组间无共享时贪心即精确最大值；有共享时对"贪心可行性"二分——N 可行则 N-1 必可行，
 * 单调成立）。needs/remainders 保留=首选口径，只喂显示/回退（残留容器改按实际消耗物结算，见 takeFor）。
 * 开关 craftIngredientAlternatives（默认开；关=全链路回旧首选口径，消耗与路由永远同口径）。
 * 解析结果按目标 id 缓存（服务器停止时清空，见 Sdzjz 注册的 SERVER_STOPPED）。
 */
public final class CraftPlanner {
    private CraftPlanner() {}

    /** m343 槽位候选组：同一配方里候选集相同的槽位合并（count=槽位数/每次合成）。
     *  candidates 序=原版 Ingredient.getMatchingStacks 序，首位即 needs 的首选口径。 */
    public record Group(java.util.List<String> candidates, int count) {}

    /** needs: 每次合成消耗（首选口径 物品id→数量，显示/回退用——实际计数扣料走 groups）；
     *  resultCount: 单次产量；remainders: 每次合成返还（首选口径，显示用——实扣后残留按 takeFor 返回值结算）；
     *  recipeId: 配方注册 id（m235 手选配方按它定位；needs 为 LinkedHashMap 保配方格序，菜单摘要稳定）；
     *  groups: m343 槽位候选组（计数/扣料/链需求唯一权威口径）。 */
    public record Plan(Map<String, Integer> needs, int resultCount, Map<String, Integer> remainders, String recipeId,
                       java.util.List<Group> groups) {}

    /** m343 缺料定位结果：第一处"组内全部替代材料合计仍不够单次"的槽位（id=该组首选，have=候选合计）。 */
    public record Missing(String id, long have, int need) {}

    private static final Map<String, java.util.List<Plan>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, java.util.Set<String>> WANTS = new ConcurrentHashMap<>();       // 全候选口径（开关开）
    private static final Map<String, java.util.Set<String>> WANTS_FIRST = new ConcurrentHashMap<>(); // 仅首选口径（开关关=旧行为）

    public static void clearCache() {
        CACHE.clear();
        WANTS.clear();
        WANTS_FIRST.clear();
    }

    private static boolean altOn() { return com.sdzjz.config.SdzjzConfig.get().craftIngredientAlternatives; }

    /** 组的生效候选：开关开=全部替代材料；关=只剩首选（=旧口径，消耗/路由同减）。 */
    private static java.util.List<String> cands(Group g, boolean alt) {
        return alt ? g.candidates() : g.candidates().subList(0, 1);
    }

    /** m234 目标物品的全部合成候选（原版排前；不可变；无配方=空表）。 */
    /** m321 计时壳（PHASES 关=直通零开销）。 */
    public static java.util.List<Plan> plans(World world, String targetId) {
        if (!com.sdzjz.debug.CoreProfiler.PHASES) return plans0(world, targetId);
        long __t = System.nanoTime();
        try { return plans0(world, targetId); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_PLANNER, System.nanoTime() - __t); }
    }

    private static java.util.List<Plan> plans0(World world, String targetId) {
        return CACHE.computeIfAbsent(targetId, id -> resolveAll(world, id));
    }

    /** m234 按库存挑第一个"至少能做一次"的候选；都不满足回退第一候选（缺料报告用原版材料口径）。
     *  m343 起"能做一次"按候选组算（任意木板认全部木板）。 */
    public static Plan pick(java.util.List<Plan> plans, java.util.function.ToLongFunction<String> stock) {
        boolean alt = altOn();
        for (Plan p : plans)
            if (maxCrafts(p, 1, stock, alt) >= 1) return p;
        return plans.get(0);
    }

    /** m343 最多可合成次数（≤cap）：组内候选按序贪心取用，跨组共享候选经虚拟扣减绝不重复计数。 */
    public static long maxCrafts(Plan p, long cap, java.util.function.ToLongFunction<String> stock) {
        return maxCrafts(p, cap, stock, altOn());
    }

    /** m343 显式口径版（GameTest 直测两分支用；alt=false 即旧首选行为）。 */
    public static long maxCrafts(Plan p, long cap, java.util.function.ToLongFunction<String> stock, boolean alt) {
        if (cap <= 0 || p.groups().isEmpty()) return Math.max(0, cap);
        cap = Math.min(cap, Long.MAX_VALUE / 16); // 护栏：下方 count(≤9)×N 乘法不溢出
        Map<String, Long> have = new HashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean shared = false;
        long ub = cap;
        for (Group g : p.groups()) {
            long sum = 0;
            for (String c : cands(g, alt)) {
                long v = Math.max(0, have.computeIfAbsent(c, stock::applyAsLong));
                sum = (sum + v < sum) ? Long.MAX_VALUE : sum + v; // 饱和加法（bigStacks 账本量级可观）
                if (!seen.add(c)) shared = true;
            }
            ub = Math.min(ub, sum / g.count());
            if (ub <= 0) return 0;
        }
        if (!shared) return ub; // 组间不共享候选：各组独立，上界即精确最大值（绝大多数配方走这）
        long lo = 0, hi = ub;   // 共享候选：对贪心可行性二分（N 可行→N-1 必可行，单调）
        while (lo < hi) {
            long mid = lo + (hi - lo + 1) / 2;
            if (feasible(p, mid, have, alt)) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /** 贪心可行性：N 次合成能否在虚拟扣减下全组喂饱（与 takeFor 同序同口径）。 */
    private static boolean feasible(Plan p, long n, Map<String, Long> have, boolean alt) {
        Map<String, Long> left = new HashMap<>(have);
        for (Group g : p.groups()) {
            long need = g.count() * n;
            for (String c : cands(g, alt)) {
                long avail = Math.max(0, left.getOrDefault(c, 0L));
                long take = Math.min(need, avail);
                if (take > 0) { left.put(c, avail - take); need -= take; }
                if (need == 0) break;
            }
            if (need > 0) return false;
        }
        return true;
    }

    /** m343 按 maxCrafts 同款贪心序实扣 crafts 次的材料（withdraw 回调逐 id 收扣减量）；
     *  返回实际消耗多重集（id→总量，可能含替代材料）——容器残留请拿返回值过 remaindersOf 结算，
     *  别再用 plan.remainders()×crafts（替代材料的残留容器可能不同）。 */
    public static Map<String, Long> takeFor(Plan p, long crafts, java.util.function.ToLongFunction<String> stock,
                                            java.util.function.ObjLongConsumer<String> withdraw) {
        return takeFor(p, crafts, stock, withdraw, altOn());
    }

    /** m343 显式口径版（GameTest 直测用）。 */
    public static Map<String, Long> takeFor(Plan p, long crafts, java.util.function.ToLongFunction<String> stock,
                                            java.util.function.ObjLongConsumer<String> withdraw, boolean alt) {
        Map<String, Long> taken = new java.util.LinkedHashMap<>();
        if (crafts <= 0) return taken;
        Map<String, Long> left = new HashMap<>();
        for (Group g : p.groups()) {
            long need = g.count() * crafts;
            for (String c : cands(g, alt)) {
                long avail = Math.max(0, left.computeIfAbsent(c, stock::applyAsLong));
                long take = Math.min(need, avail);
                if (take > 0) { left.put(c, avail - take); taken.merge(c, take, Long::sum); need -= take; }
                if (need == 0) break;
            }
            // need>0 理论不可达（crafts 来自 maxCrafts 同序同口径）；防御口径=短多少少扣多少，绝不虚扣
        }
        for (var en : taken.entrySet()) withdraw.accept(en.getKey(), en.getValue());
        return taken;
    }

    /** m343 实际消耗多重集 → 容器残留多重集（桶等），按真被消耗的物品逐件结算。 */
    public static Map<String, Long> remaindersOf(Map<String, Long> taken) {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (var en : taken.entrySet()) {
            Item rem = Registries.ITEM.get(Identifier.of(en.getKey())).getRecipeRemainder();
            if (rem != null) out.merge(Registries.ITEM.getId(rem).toString(), en.getValue(), Long::sum);
        }
        return out;
    }

    // ===== m349 一次成型执行计划（外部审计③轮①③：pick/maxCrafts/takeFor/remainders 从"重复算三遍"
    // 合并为 库存快照→选配方→算次数→算实扣→算残留 单趟；存储每个去重 id 只被查一次，
    // 后续全程内存计算——StorageAccess 将来换成聚合实现也不会被放大成网络级访问）。 =====

    /** m349 库存视图：Planner 的存储输入口（语义同 ToLongFunction<String>，命名接口便于聚合实现对齐）。 */
    @FunctionalInterface
    public interface StockView { long count(String id); }

    /** m349 执行计划：plan=中选配方（都不齐=首候选，缺料报告口径与旧 pick 一致）；crafts=最终次数；
     *  taken=应实扣多重集（调用方拿它逐 id 真扣，序=LinkedHashMap 组序稳定）；remainders=容器残留总量。 */
    public record Exec(Plan plan, long crafts, Map<String, Long> taken, Map<String, Long> remainders) {}

    /** m349 单趟出全套。capOf=按中选配方算次数上限（m99 无存储槽位封顶依赖 plan.resultCount 故为函数）；
     *  manual=m235 手选（非空则只看它，缺料按它报）。选配方口径与旧 pick 逐点一致：探"能做一次"不看 cap，
     *  全不可行回退首候选。m321 计时并入 SUB_PLANNER。 */
    public static Exec exec(java.util.List<Plan> plans, Plan manual, java.util.function.ToLongFunction<Plan> capOf,
                            StockView stock) {
        return exec(plans, manual, capOf, stock, altOn());
    }

    /** m349 显式口径版（GameTest 直测两分支用）。 */
    public static Exec exec(java.util.List<Plan> plans, Plan manual, java.util.function.ToLongFunction<Plan> capOf,
                            StockView stock, boolean alt) {
        if (!com.sdzjz.debug.CoreProfiler.PHASES) return exec0(plans, manual, capOf, stock, alt);
        long __t = System.nanoTime();
        try { return exec0(plans, manual, capOf, stock, alt); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_PLANNER, System.nanoTime() - __t); }
    }

    private static Exec exec0(java.util.List<Plan> plans, Plan manual, java.util.function.ToLongFunction<Plan> capOf,
                              StockView stock, boolean alt) {
        java.util.List<Plan> order = manual != null ? java.util.List.of(manual) : plans;
        // ① 快照物化：全生效候选去重 id 各查存储恰一次（值先钳非负，后续贪心免逐处 Math.max）
        Map<String, Long> snap = new HashMap<>();
        for (Plan p : order)
            for (Group g : p.groups())
                for (String c : cands(g, alt))
                    if (!snap.containsKey(c)) snap.put(c, Math.max(0, stock.count(c)));
        // ② 选配方：第一个"至少能做一次"的候选（快照上探，不看 cap=旧 pick 口径）；全不可行回退首候选
        Plan plan = null;
        for (Plan p : order)
            if (maxCraftsOn(p, 1, snap, alt) >= 1) { plan = p; break; }
        if (plan == null) plan = order.get(0);
        // ③ 次数：中选配方按 capOf 封顶在快照上算满
        long crafts = maxCraftsOn(plan, Math.max(0, capOf.applyAsLong(plan)), snap, alt);
        if (crafts <= 0) return new Exec(plan, 0, Map.of(), Map.of());
        // ④ 实扣 + ⑤ 残留：同快照同贪心序一趟
        Map<String, Long> taken = takeOn(plan, crafts, snap, alt);
        return new Exec(plan, crafts, taken, remaindersOf(taken));
    }

    /** m349 快照版 maxCrafts：与公共版逐行同口径，唯 have 记忆表换现成快照（零 stock 回调零 computeIfAbsent）。 */
    private static long maxCraftsOn(Plan p, long cap, Map<String, Long> snap, boolean alt) {
        if (cap <= 0 || p.groups().isEmpty()) return Math.max(0, cap);
        cap = Math.min(cap, Long.MAX_VALUE / 16); // 护栏：下方 count(≤9)×N 乘法不溢出
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean shared = false;
        long ub = cap;
        for (Group g : p.groups()) {
            long sum = 0;
            for (String c : cands(g, alt)) {
                long v = snap.getOrDefault(c, 0L); // 快照建立时已钳非负
                sum = (sum + v < sum) ? Long.MAX_VALUE : sum + v; // 饱和加法（bigStacks 账本量级可观）
                if (!seen.add(c)) shared = true;
            }
            ub = Math.min(ub, sum / g.count());
            if (ub <= 0) return 0;
        }
        if (!shared) return ub; // 组间不共享候选：上界即精确（绝大多数配方走这）
        long lo = 0, hi = ub;   // 共享候选：贪心可行性二分（feasible 复用，形参本就是 Map）
        while (lo < hi) {
            long mid = lo + (hi - lo + 1) / 2;
            if (feasible(p, mid, snap, alt)) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /** m349 快照版 takeFor：同贪心同序，虚拟账本从快照复制，不回调 withdraw（实扣由调用方按返回值做）。 */
    private static Map<String, Long> takeOn(Plan p, long crafts, Map<String, Long> snap, boolean alt) {
        Map<String, Long> taken = new java.util.LinkedHashMap<>();
        if (crafts <= 0) return taken;
        Map<String, Long> left = new HashMap<>(snap);
        for (Group g : p.groups()) {
            long need = g.count() * crafts;
            for (String c : cands(g, alt)) {
                long avail = left.getOrDefault(c, 0L);
                long take = Math.min(need, avail);
                if (take > 0) { left.put(c, avail - take); taken.merge(c, take, Long::sum); need -= take; }
                if (need == 0) break;
            }
            // need>0 理论不可达（crafts 来自同快照同序 maxCraftsOn）；防御口径=短多少少扣多少，绝不虚扣
        }
        return taken;
    }

    /** m343 单条候选的"想要"集合（m235 手选配方的链需求口径：含各槽全部生效候选，与消耗同口径）。 */
    public static java.util.Set<String> wantsOf(Plan p) {
        boolean alt = altOn();
        java.util.Set<String> u = new java.util.HashSet<>();
        for (Group g : p.groups()) u.addAll(cands(g, alt));
        return u;
    }

    /** m343 accepts 热路径单查：id 是否本候选任一槽位的生效材料（不建集合零垃圾）。 */
    public static boolean wantsItem(Plan p, String id) {
        boolean alt = altOn();
        for (Group g : p.groups())
            if (cands(g, alt).contains(id)) return true;
        return false;
    }

    /** m343 缺料定位：第一处候选合计不够单次的组（null=其实都够，调用方兜底泛化文案）。 */
    public static Missing firstMissing(Plan p, java.util.function.ToLongFunction<String> stock) {
        boolean alt = altOn();
        for (Group g : p.groups()) {
            long have = 0;
            for (String c : cands(g, alt)) {
                long v = Math.max(0, stock.applyAsLong(c));
                have = (have + v < have) ? Long.MAX_VALUE : have + v;
            }
            if (have < g.count()) return new Missing(g.candidates().get(0), have, g.count());
        }
        return null;
    }

    /** m234 链需求/收料判定用：全候选材料并集（任一候选用得上的料都"想要"，路由不偏科）。
     *  m343 起并集含槽位替代材料（开关关=仅首选，两口径各自缓存，翻开关不吃陈账）。 */
    public static java.util.Set<String> wants(World world, String targetId) {
        Map<String, java.util.Set<String>> cache = altOn() ? WANTS : WANTS_FIRST;
        boolean alt = altOn();
        return cache.computeIfAbsent(targetId, id -> {
            java.util.Set<String> u = new java.util.HashSet<>();
            for (Plan p : plans(world, id))
                for (Group g : p.groups()) u.addAll(cands(g, alt));
            return java.util.Set.copyOf(u);
        });
    }

    private static java.util.List<Plan> resolveAll(World world, String targetId) {
        Item target = Registries.ITEM.get(Identifier.of(targetId));
        if (target == Items.AIR) return java.util.List.of();
        java.util.List<Map.Entry<Identifier, Plan>> found = new java.util.ArrayList<>();
        for (RecipeEntry<CraftingRecipe> entry : world.getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
            CraftingRecipe r = entry.value();
            ItemStack out;
            try {
                out = r.getResult(world.getRegistryManager());
            } catch (Exception ex) {
                continue; // 特殊配方（烟花/染色等）取结果可能异常，跳过
            }
            if (out == null || out.isEmpty() || out.getItem() != target) continue;

            Map<String, Integer> needs = new java.util.LinkedHashMap<>();
            Map<String, Integer> remainders = new java.util.LinkedHashMap<>();
            Map<java.util.List<String>, Integer> groupCount = new java.util.LinkedHashMap<>(); // m343 同候选集槽位合并
            boolean ok = true;
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matching = ing.getMatchingStacks();
                if (matching == null || matching.length == 0) { ok = false; break; }
                java.util.LinkedHashSet<String> cset = new java.util.LinkedHashSet<>(); // 保 matching 序去重
                for (ItemStack ms : matching) cset.add(Registries.ITEM.getId(ms.getItem()).toString());
                groupCount.merge(java.util.List.copyOf(cset), 1, Integer::sum);
                Item pick = matching[0].getItem(); // 首选口径（显示/回退；实际计数扣料走 groups——m343）
                needs.merge(Registries.ITEM.getId(pick).toString(), 1, Integer::sum);
                Item rem = pick.getRecipeRemainder();
                if (rem != null) remainders.merge(Registries.ITEM.getId(rem).toString(), 1, Integer::sum);
            }
            if (!ok || needs.isEmpty()) continue; // 无固定材料的特殊配方不支持
            java.util.List<Group> groups = new java.util.ArrayList<>(groupCount.size());
            for (var en : groupCount.entrySet()) groups.add(new Group(en.getKey(), en.getValue()));
            found.add(Map.entry(entry.id(),
                    new Plan(needs, out.getCount(), remainders, entry.id().toString(), java.util.List.copyOf(groups))));
        }
        found.sort(java.util.Comparator
                .comparingInt((Map.Entry<Identifier, Plan> e) -> "minecraft".equals(e.getKey().getNamespace()) ? 0 : 1)
                .thenComparing(e -> e.getKey().toString()));
        return found.stream().map(Map.Entry::getValue).toList();
    }
}
