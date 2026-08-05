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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动合成机的配方解析：给定目标物品 id，从原版合成配方表解析出
 * 每次合成的材料清单（多重集）、单次产量、以及残留容器（如桶）。
 * m234：同一目标可能有多条配方（装 ProjectEF 后金锭被"贤者之石+铁"抢注、原版还有 9 金粒/金块两条）——
 * 改为**全候选缓存**：minecraft 命名空间排前、同空间按配方 id 字典序（稳定可复现）；生产端拿到库存
 * 视图后用 pick 挑第一个可满足的候选，都不满足回退第一候选（缺料就按原版材料报，玩家看得懂）。
 * 解析结果按目标 id 缓存（服务器停止时清空，见 Sdzjz 注册的 SERVER_STOPPED）。
 */
public final class CraftPlanner {
    private CraftPlanner() {}

    /** needs: 每次合成消耗（物品id→数量）；resultCount: 单次产量；remainders: 每次合成返还（桶等容器）。 */
    public record Plan(Map<String, Integer> needs, int resultCount, Map<String, Integer> remainders) {}

    private static final Map<String, java.util.List<Plan>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, java.util.Set<String>> WANTS = new ConcurrentHashMap<>();

    public static void clearCache() {
        CACHE.clear();
        WANTS.clear();
    }

    /** m234 目标物品的全部合成候选（原版排前；不可变；无配方=空表）。 */
    public static java.util.List<Plan> plans(World world, String targetId) {
        return CACHE.computeIfAbsent(targetId, id -> resolveAll(world, id));
    }

    /** m234 按库存挑第一个"至少能做一次"的候选；都不满足回退第一候选（缺料报告用原版材料口径）。 */
    public static Plan pick(java.util.List<Plan> plans, java.util.function.ToLongFunction<String> stock) {
        for (Plan p : plans) {
            boolean ok = true;
            for (var en : p.needs().entrySet())
                if (stock.applyAsLong(en.getKey()) < en.getValue()) { ok = false; break; }
            if (ok) return p;
        }
        return plans.get(0);
    }

    /** m234 链需求/收料判定用：全候选材料并集（任一候选用得上的料都"想要"，路由不偏科）。 */
    public static java.util.Set<String> wants(World world, String targetId) {
        return WANTS.computeIfAbsent(targetId, id -> {
            java.util.Set<String> u = new java.util.HashSet<>();
            for (Plan p : plans(world, id)) u.addAll(p.needs().keySet());
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

            Map<String, Integer> needs = new HashMap<>();
            Map<String, Integer> remainders = new HashMap<>();
            boolean ok = true;
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matching = ing.getMatchingStacks();
                if (matching == null || matching.length == 0) { ok = false; break; }
                Item pick = matching[0].getItem(); // 取该材料的第一个候选（如任意木板→橡木板）
                needs.merge(Registries.ITEM.getId(pick).toString(), 1, Integer::sum);
                Item rem = pick.getRecipeRemainder();
                if (rem != null) remainders.merge(Registries.ITEM.getId(rem).toString(), 1, Integer::sum);
            }
            if (!ok || needs.isEmpty()) continue; // 无固定材料的特殊配方不支持
            found.add(Map.entry(entry.id(), new Plan(needs, out.getCount(), remainders)));
        }
        found.sort(java.util.Comparator
                .comparingInt((Map.Entry<Identifier, Plan> e) -> "minecraft".equals(e.getKey().getNamespace()) ? 0 : 1)
                .thenComparing(e -> e.getKey().toString()));
        return found.stream().map(Map.Entry::getValue).toList();
    }
}
