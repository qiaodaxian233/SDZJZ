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
 * 解析结果按目标 id 缓存（服务器停止时清空，见 Sdzjz 注册的 SERVER_STOPPED）。
 */
public final class CraftPlanner {
    private CraftPlanner() {}

    /** needs: 每次合成消耗（物品id→数量）；resultCount: 单次产量；remainders: 每次合成返还（桶等容器）。 */
    public record Plan(Map<String, Integer> needs, int resultCount, Map<String, Integer> remainders) {}

    private static final Map<String, Optional<Plan>> CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        CACHE.clear();
    }

    /** 返回目标物品的合成计划；无配方/无效 id 返回 null。 */
    public static Plan plan(World world, String targetId) {
        return CACHE.computeIfAbsent(targetId, id -> Optional.ofNullable(resolve(world, id))).orElse(null);
    }

    private static Plan resolve(World world, String targetId) {
        Item target = Registries.ITEM.get(Identifier.of(targetId));
        if (target == Items.AIR) return null;
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
            return new Plan(needs, out.getCount(), remainders);
        }
        return null;
    }
}
