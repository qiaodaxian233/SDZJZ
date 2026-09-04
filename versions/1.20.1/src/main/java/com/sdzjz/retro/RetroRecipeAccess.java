package com.sdzjz.retro;

import com.sdzjz.machine.BrewPlanner;
import com.sdzjz.machine.CraftPlanner;
import com.sdzjz.machine.EnchantPlanner;
import com.sdzjz.platform.RecipeAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * m494（C2-⑤d2）：1.20.1 世代的 {@link RecipeAccess} —— m494 只实装熔炼一域，**m520（C2a）补合成/余料两域**，
 * 其余两域（酿造/附魔）返回空，随 ⑤d1（C2b）到序（酿造与附魔还卡在
 * `docs/作业表.md` 阻塞区的「附魔定价 A/B」上）。
 *
 * <p><b>为什么必须先装上</b>：熔炉族 tick 每拍都要问 {@code SmeltPlanner.resultOf}，
 * 而 {@code Platform.recipes()} 未注册时**直接抛异常**——不装就是「玩家一放万能熔炉就崩服」。
 * 空实现不是占位符敷衍，是**让未到序的域安静地返回"没有"**，与主线「目标未配置」同一姿势。
 *
 * <p><b>熔炼一域的世代差</b>（查 `docs/世代API对照表.md` 得，非现猜）：
 * 1.21 的 {@code getAllRecipesFor} 返回 {@code List<RecipeHolder<T>>}（要 {@code .value()}/
 * {@code .id()}），1.20.1 直接返回 {@code List<SmeltingRecipe>}、id 走 {@code getId()}；
 * 结果栈取用 {@code getResultItem(registryAccess)}（1.19.3+ 同签名）。
 * 候选三元组的形状 {@code {配方id, 产物id, 产量}} 与 LegacyRecipeAccess 逐位一致——
 * {@code SmeltPlanner.pickStable} 的稳定选序（minecraft 命名空间优先、同空间按 id 字典序）
 * 靠这个形状，错一位就会两代选出不同配方。
 */
final class RetroRecipeAccess implements RecipeAccess {

    // ===== 熔炼域（本刀实装）=====

    @Override
    public Map<String, List<Object[]>> smeltingCandidates(Object level) {
        Map<String, List<Object[]>> out = new HashMap<>();
        if (!(level instanceof Level world)) return out;
        for (SmeltingRecipe r : world.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            ItemStack res = r.getResultItem(world.registryAccess());
            if (res.isEmpty()) continue;
            String outId = BuiltInRegistries.ITEM.getKey(res.getItem()).toString();
            String rid = r.getId().toString(); // 1.20.1：配方自带 id（1.21 走 RecipeHolder.id()）
            for (var ing : r.getIngredients()) {
                if (ing.isEmpty()) continue;
                for (ItemStack m : ing.getItems()) {
                    if (m.isEmpty()) continue;
                    String inId = BuiltInRegistries.ITEM.getKey(m.getItem()).toString();
                    out.computeIfAbsent(inId, k -> new ArrayList<>())
                            .add(new Object[]{rid, outId, res.getCount()}); // 三元组形状与 Legacy 逐位一致
                }
            }
        }
        return out;
    }

    // ===== 合成 / 余料两域（m520 C2a 实装，作者点名「合成」）：LegacyRecipeAccess 原文（m362 = CraftPlanner.resolveAll 平移），=====
    // 世代差三处（查对照表，非现猜）：①getAllRecipesFor(CRAFTING) 1.20.1 直接返 List<CraftingRecipe>（1.21 是 RecipeHolder 要 .value()/.id()）
    // ②配方 id 走 r.getId()  ③ResourceLocation.parse → new ResourceLocation(String)。其余一字未改，含 m343 同候选集槽位合并与 minecraft 命名空间优先排序。

    @Override
    public List<CraftPlanner.Plan> craftingPlans(Object level, String targetId) {
        if (!(level instanceof Level world)) return List.of();
        Item target = BuiltInRegistries.ITEM.get(new ResourceLocation(targetId));
        if (target == Items.AIR) return List.of();
        List<Map.Entry<ResourceLocation, CraftPlanner.Plan>> found = new ArrayList<>();
        for (CraftingRecipe r : world.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack out;
            try {
                out = r.getResultItem(world.registryAccess());
            } catch (Exception ex) {
                continue; // 特殊配方（烟花/染色等）取结果可能异常，跳过
            }
            if (out == null || out.isEmpty() || out.getItem() != target) continue;

            Map<String, Integer> needs = new java.util.LinkedHashMap<>();
            Map<String, Integer> remainders = new java.util.LinkedHashMap<>();
            Map<List<String>, Integer> groupCount = new java.util.LinkedHashMap<>(); // m343 同候选集槽位合并
            boolean ok = true;
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matching = ing.getItems();
                if (matching == null || matching.length == 0) { ok = false; break; }
                java.util.LinkedHashSet<String> cset = new java.util.LinkedHashSet<>(); // 保 matching 序去重
                for (ItemStack ms : matching) cset.add(BuiltInRegistries.ITEM.getKey(ms.getItem()).toString());
                groupCount.merge(List.copyOf(cset), 1, Integer::sum);
                Item pick = matching[0].getItem(); // 首选口径（显示/回退；实际计数扣料走 groups——m343）
                needs.merge(BuiltInRegistries.ITEM.getKey(pick).toString(), 1, Integer::sum);
                Item rem = pick.getCraftingRemainingItem();
                if (rem != null) remainders.merge(BuiltInRegistries.ITEM.getKey(rem).toString(), 1, Integer::sum);
            }
            if (!ok || needs.isEmpty()) continue; // 无固定材料的特殊配方不支持
            List<CraftPlanner.Group> groups = new ArrayList<>(groupCount.size());
            for (var en : groupCount.entrySet()) groups.add(new CraftPlanner.Group(en.getKey(), en.getValue()));
            found.add(Map.entry(r.getId(),
                    new CraftPlanner.Plan(needs, out.getCount(), remainders, r.getId().toString(), List.copyOf(groups))));
        }
        found.sort(java.util.Comparator
                .comparingInt((Map.Entry<ResourceLocation, CraftPlanner.Plan> e) -> "minecraft".equals(e.getKey().getNamespace()) ? 0 : 1)
                .thenComparing(e -> e.getKey().toString()));
        return found.stream().map(Map.Entry::getValue).toList();
    }

    @Override
    public String craftRemainderOf(String itemId) {
        Item rem = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId)).getCraftingRemainingItem();
        return rem != null ? BuiltInRegistries.ITEM.getKey(rem).toString() : null;
    }

    // ===== 酿造 / 附魔域：随 ⑤d1（C2b）到序，此前安静返回「没有」=====

    @Override
    public BrewPlanner.Plan brewingPlan(Object level, String target) {
        return null; // ⑤d1（1.20.1 药水走 PotionUtils + BrewingRecipeRegistry，见世代 API 对照表）
    }

    @Override
    public Object brewTargetStack(String target) {
        return null; // ⑤d1
    }

    @Override
    public EnchantPlanner.Plan enchantingPlan(Object level, String target) {
        return null; // ⑤d1（附魔定价 getAnvilCost 无 1.20.1 对位，等作者拍板 A/B——见作业表阻塞区）
    }

    @Override
    public Object enchantTargetStack(Object level, String target) {
        return null; // ⑤d1
    }

    @Override
    public Object enchantTargetName(Object level, String target) {
        return null; // ⑤d1
    }
}
