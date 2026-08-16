package com.sdzjz.legacy;

import com.sdzjz.machine.CraftPlanner;
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

import java.util.Map;

/**
 * m362 Legacy(≤1.21.11) 配方查询实现——CraftPlanner.resolveAll 原文平移（m180 刀法：方法体
 * 一字未改，仅 world 形参由不透明句柄向下转型）。无状态：level 随调用传，服务端/客户端
 * 各用各的配方视图（客户端画布屏也走 plans，故绝不持服务端引用）。
 */
public final class LegacyRecipeAccess implements com.sdzjz.platform.RecipeAccess {

    @Override
    public java.util.List<CraftPlanner.Plan> craftingPlans(Object level, String targetId) {
        World world = (World) level; // 代际句柄向下转型：Legacy 世界即原版 World
        Item target = Registries.ITEM.get(Identifier.of(targetId));
        if (target == Items.AIR) return java.util.List.of();
        java.util.List<Map.Entry<Identifier, CraftPlanner.Plan>> found = new java.util.ArrayList<>();
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
            java.util.List<CraftPlanner.Group> groups = new java.util.ArrayList<>(groupCount.size());
            for (var en : groupCount.entrySet()) groups.add(new CraftPlanner.Group(en.getKey(), en.getValue()));
            found.add(Map.entry(entry.id(),
                    new CraftPlanner.Plan(needs, out.getCount(), remainders, entry.id().toString(), java.util.List.copyOf(groups))));
        }
        found.sort(java.util.Comparator
                .comparingInt((Map.Entry<Identifier, CraftPlanner.Plan> e) -> "minecraft".equals(e.getKey().getNamespace()) ? 0 : 1)
                .thenComparing(e -> e.getKey().toString()));
        return found.stream().map(Map.Entry::getValue).toList();
    }

    @Override
    public String craftRemainderOf(String itemId) {
        Item rem = Registries.ITEM.get(Identifier.of(itemId)).getRecipeRemainder();
        return rem != null ? Registries.ITEM.getId(rem).toString() : null;
    }
}
