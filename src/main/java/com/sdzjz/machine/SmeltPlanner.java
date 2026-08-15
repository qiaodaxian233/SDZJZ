package com.sdzjz.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 万能熔炼表：从原版熔炼配方（RecipeType.SMELTING）构建 输入id→[输出id,单次产量]。
 * 超级熔炉组用它做到"接什么烧什么"（圆石→石头、原木→木炭、粗铁→铁锭、沙子→玻璃……）。
 * 懒加载 + 服务器停止时清空（与 CraftPlanner 同步策略）。
 * m346：同一输入被多条熔炼配方覆盖时（数据包常态，原版无同输入重复配方故原版行为逐字节不变），
 * 旧 putIfAbsent=RecipeManager 遍历序先到先得，重启/换包间掷骰——改稳定选序：minecraft 命名
 * 空间排前、其余按配方 id 全串字典序（m234/CraftPlanner 同口径），选序抽 pickStable 纯函数直测。
 */
public final class SmeltPlanner {

    private static Map<String, Object[]> cache; // inputId → {outputId(String), outCount(Integer)}

    public static synchronized Object[] resultOf(World world, String inputId) {
        if (cache == null) build(world);
        return cache.get(inputId);
    }

    /** m346 稳定选序纯函数：候选=[recipeId(String), outId(String), outCount(Integer)]，
     *  胜者=minecraft 空间排前、同档按配方 id 字典序最小；空列表返回 null。入参列表不被修改，
     *  任意洗牌同一多重集必得同一胜者（GameTest 廿八号直测口径）。 */
    public static Object[] pickStable(List<Object[]> cands) {
        Object[] best = null;
        String bestId = null;
        for (Object[] c : cands) {
            String rid = (String) c[0];
            if (best == null || cmpRecipeId(rid, bestId) < 0) { best = c; bestId = rid; }
        }
        return best;
    }

    private static int cmpRecipeId(String a, String b) {
        boolean va = a.startsWith("minecraft:"), vb = b.startsWith("minecraft:");
        if (va != vb) return va ? -1 : 1;
        return a.compareTo(b);
    }

    private static void build(World world) {
        // 两趟：先按输入收全候选（带配方 id），再逐输入稳定选序落表——胜者与遍历序无关。
        Map<String, List<Object[]>> cands = new HashMap<>();
        for (RecipeEntry<SmeltingRecipe> e : world.getRecipeManager().listAllOfType(RecipeType.SMELTING)) {
            try {
                ItemStack out = e.value().getResult(world.getRegistryManager());
                if (out == null || out.isEmpty()) continue;
                Identifier rid = e.id();
                String outId = Registries.ITEM.getId(out.getItem()).toString();
                int outCount = out.getCount();
                for (Ingredient ing : e.value().getIngredients()) {
                    for (ItemStack s : ing.getMatchingStacks()) {
                        cands.computeIfAbsent(Registries.ITEM.getId(s.getItem()).toString(), k -> new ArrayList<>())
                                .add(new Object[]{rid.toString(), outId, outCount});
                    }
                }
            } catch (Exception ignored) {}
        }
        Map<String, Object[]> built = new HashMap<>();
        for (Map.Entry<String, List<Object[]>> en : cands.entrySet()) {
            Object[] win = pickStable(en.getValue());
            if (win != null) built.put(en.getKey(), new Object[]{win[1], win[2]});
        }
        cache = built;
    }

    public static synchronized void clearCache() {
        cache = null;
    }
}
