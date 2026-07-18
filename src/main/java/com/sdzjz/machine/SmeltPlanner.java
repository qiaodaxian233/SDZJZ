package com.sdzjz.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * 万能熔炼表：从原版熔炼配方（RecipeType.SMELTING）构建 输入id→[输出id,单次产量]。
 * 超级熔炉组用它做到"接什么烧什么"（圆石→石头、原木→木炭、粗铁→铁锭、沙子→玻璃……）。
 * 懒加载 + 服务器停止时清空（与 CraftPlanner 同步策略）。
 */
public final class SmeltPlanner {

    private static Map<String, Object[]> cache; // inputId → {outputId(String), outCount(Integer)}

    public static synchronized Object[] resultOf(World world, String inputId) {
        if (cache == null) build(world);
        return cache.get(inputId);
    }

    private static void build(World world) {
        cache = new HashMap<>();
        for (RecipeEntry<SmeltingRecipe> e : world.getRecipeManager().listAllOfType(RecipeType.SMELTING)) {
            try {
                ItemStack out = e.value().getResult(world.getRegistryManager());
                if (out == null || out.isEmpty()) continue;
                String outId = Registries.ITEM.getId(out.getItem()).toString();
                int outCount = out.getCount();
                for (Ingredient ing : e.value().getIngredients()) {
                    for (ItemStack s : ing.getMatchingStacks()) {
                        cache.putIfAbsent(Registries.ITEM.getId(s.getItem()).toString(), new Object[]{outId, outCount});
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public static synchronized void clearCache() {
        cache = null;
    }
}
