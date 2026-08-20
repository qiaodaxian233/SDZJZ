package com.sdzjz.legacy;

import com.sdzjz.machine.CraftPlanner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.Map;

/**
 * m362 Legacy(≤1.21.11) 配方查询实现——CraftPlanner.resolveAll 原文平移（m180 刀法：方法体
 * 一字未改，仅 world 形参由不透明句柄向下转型）。无状态：level 随调用传，服务端/客户端
 * 各用各的配方视图（客户端画布屏也走 plans，故绝不持服务端引用）。
 */
public final class LegacyRecipeAccess implements com.sdzjz.platform.RecipeAccess {

    @Override
    public java.util.List<CraftPlanner.Plan> craftingPlans(Object level, String targetId) {
        Level world = (Level) level; // 代际句柄向下转型：Legacy 世界即原版 Level
        Item target = BuiltInRegistries.ITEM.get(ResourceLocation.of(targetId));
        if (target == Items.AIR) return java.util.List.of();
        java.util.List<Map.Entry<ResourceLocation, CraftPlanner.Plan>> found = new java.util.ArrayList<>();
        for (RecipeHolder<CraftingRecipe> entry : world.getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
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
                for (ItemStack ms : matching) cset.add(BuiltInRegistries.ITEM.getId(ms.getItem()).toString());
                groupCount.merge(java.util.List.copyOf(cset), 1, Integer::sum);
                Item pick = matching[0].getItem(); // 首选口径（显示/回退；实际计数扣料走 groups——m343）
                needs.merge(BuiltInRegistries.ITEM.getId(pick).toString(), 1, Integer::sum);
                Item rem = pick.getRecipeRemainder();
                if (rem != null) remainders.merge(BuiltInRegistries.ITEM.getId(rem).toString(), 1, Integer::sum);
            }
            if (!ok || needs.isEmpty()) continue; // 无固定材料的特殊配方不支持
            java.util.List<CraftPlanner.Group> groups = new java.util.ArrayList<>(groupCount.size());
            for (var en : groupCount.entrySet()) groups.add(new CraftPlanner.Group(en.getKey(), en.getValue()));
            found.add(Map.entry(entry.id(),
                    new CraftPlanner.Plan(needs, out.getCount(), remainders, entry.id().toString(), java.util.List.copyOf(groups))));
        }
        found.sort(java.util.Comparator
                .comparingInt((Map.Entry<ResourceLocation, CraftPlanner.Plan> e) -> "minecraft".equals(e.getKey().getNamespace()) ? 0 : 1)
                .thenComparing(e -> e.getKey().toString()));
        return found.stream().map(Map.Entry::getValue).toList();
    }

    @Override
    public java.util.Map<String, java.util.List<Object[]>> smeltingCandidates(Object level) {
        Level world = (Level) level;
        java.util.Map<String, java.util.List<Object[]>> cands = new java.util.HashMap<>();
        for (RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe> e
                : world.getRecipeManager().listAllOfType(RecipeType.SMELTING)) {
            try {
                ItemStack out = e.value().getResult(world.getRegistryManager());
                if (out == null || out.isEmpty()) continue;
                ResourceLocation rid = e.id();
                String outId = BuiltInRegistries.ITEM.getId(out.getItem()).toString();
                int outCount = out.getCount();
                for (Ingredient ing : e.value().getIngredients()) {
                    for (ItemStack s : ing.getMatchingStacks()) {
                        cands.computeIfAbsent(BuiltInRegistries.ITEM.getId(s.getItem()).toString(), k -> new java.util.ArrayList<>())
                                .add(new Object[]{rid.toString(), outId, outCount});
                    }
                }
            } catch (Exception ignored) { }
        }
        return cands;
    }

    @Override
    public String craftRemainderOf(String itemId) {
        Item rem = BuiltInRegistries.ITEM.get(ResourceLocation.of(itemId)).getRecipeRemainder();
        return rem != null ? BuiltInRegistries.ITEM.getId(rem).toString() : null;
    }

    // ===== m364 酿造/附魔解析层（BrewPlanner/EnchantPlanner 原文平移，m180 刀法） =====

    private static volatile List<ItemStack> INGREDIENTS; // 有效酿造材料缓存（各 count=1，随解析层迁入）

    /** m364 datapack reload 失效口（代际引导端 Sdzjz 的 reload 钩调用，与四 planner clearCache 同拍）。 */
    public static void clearCaches() { INGREDIENTS = null; }

    @Override
    public com.sdzjz.machine.BrewPlanner.Plan brewingPlan(Object level, String target) { return brewResolve((Level) level, target); }

    @Override
    public Object brewTargetStack(String target) { return brewTargetStack0(target); }

    @Override
    public com.sdzjz.machine.EnchantPlanner.Plan enchantingPlan(Object level, String target) { return enchResolve((Level) level, target); }

    @Override
    public Object enchantTargetStack(Object level, String target) { return enchTargetStack0((Level) level, target); }

    @Override
    public Object enchantTargetName(Object level, String target) { return enchTargetName0((Level) level, target); }

    private static ItemStack brewTargetStack0(String target) {
        if (target == null || target.length() < 3) return null;
        int cut = target.lastIndexOf('|');
        if (cut <= 0 || cut != target.length() - 2) return null;
        char f = target.charAt(cut + 1);
        Item container = f == 'p' ? Items.POTION
                : f == 's' ? Items.SPLASH_POTION
                : f == 'l' ? Items.LINGERING_POTION : null;
        if (container == null) return null;
        ResourceLocation pid = ResourceLocation.tryParse(target.substring(0, cut));
        if (pid == null) return null;
        var entry = BuiltInRegistries.POTION.getEntry(pid);
        if (entry.isEmpty()) return null;
        return PotionContents.createStack(container, entry.get());
    }

    private static String key(ItemStack s) {
        PotionContents pc = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.DEFAULT);
        String pot = pc.potion().map(e -> e.getIdAsString()).orElse("-");
        return BuiltInRegistries.ITEM.getId(s.getItem()) + "|" + pot;
    }

    private static List<ItemStack> ingredients(Level world) {
        List<ItemStack> list = INGREDIENTS;
        if (list != null) return list;
        var reg = world.getBrewingRecipeRegistry();
        list = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) {
            ItemStack s = new ItemStack(it);
            if (reg.isValidIngredient(s)) list.add(s);
        }
        INGREDIENTS = list;
        return list;
    }

    private static com.sdzjz.machine.BrewPlanner.Plan brewResolve(Level world, String target) {
        ItemStack goal = brewTargetStack0(target);
        if (goal == null) return null;
        String goalKey = key(goal);
        var reg = world.getBrewingRecipeRegistry();
        List<ItemStack> ings = ingredients(world);

        ItemStack start = PotionContents.createStack(Items.POTION, Potions.WATER);
        String startKey = key(start);
        Map<String, String[]> prev = new HashMap<>();   // key → {prevKey, 材料id}；起点值=null
        Map<String, ItemStack> stacks = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        prev.put(startKey, null);
        stacks.put(startKey, start);
        queue.add(startKey);
        while (!queue.isEmpty() && !prev.containsKey(goalKey)) {
            String curKey = queue.poll();
            ItemStack cur = stacks.get(curKey);
            for (ItemStack ing : ings) {
                ItemStack out = reg.craft(ing, cur.copy());
                if (out.isEmpty() || ItemStack.areItemsAndComponentsEqual(out, cur)) continue; // 无此配方
                String ok = key(out);
                if (prev.containsKey(ok)) continue; // BFS 首达即最短链
                prev.put(ok, new String[]{curKey, BuiltInRegistries.ITEM.getId(ing.getItem()).toString()});
                stacks.put(ok, out);
                queue.add(ok);
            }
        }
        if (!prev.containsKey(goalKey)) return null; // 不可达（如平凡药水串错/模组卸载）

        Map<String, Integer> needs = new HashMap<>();
        needs.put("minecraft:glass_bottle", com.sdzjz.machine.BrewPlanner.BOTTLES_PER_BATCH);
        int steps = 0;
        String walk = goalKey;
        while (true) {
            String[] p = prev.get(walk);
            if (p == null) break;
            needs.merge(p[1], 1, Integer::sum);
            steps++;
            walk = p[0];
        }
        if (steps == 0) return null; // 目标=水瓶：没有酿造意义，不接
        return new com.sdzjz.machine.BrewPlanner.Plan(Map.copyOf(needs), steps, goal);
    }

    private record Parsed(Holder<Enchantment> entry, int level) {}

    /** 解析并校验目标串：附魔在注册表 且 1 ≤ 等级 ≤ maxLevel。 */
    private static Parsed parse(Level world, String target) {
        if (world == null || target == null || target.length() < 3) return null;
        int cut = target.lastIndexOf('|');
        if (cut <= 0 || cut >= target.length() - 1) return null;
        int lv;
        try {
            lv = Integer.parseInt(target.substring(cut + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(target.substring(0, cut));
        if (id == null) return null;
        var reg = world.getRegistryManager().getWrapperOrThrow(BuiltInRegistries.ENCHANTMENT);
        var entry = reg.getOptional(ResourceKey.of(BuiltInRegistries.ENCHANTMENT, id));
        if (entry.isEmpty()) return null;
        Enchantment ench = entry.get().value();
        if (lv < 1 || lv > ench.getMaxLevel()) return null;
        return new Parsed(entry.get(), lv);
    }

    private static ItemStack enchTargetStack0(Level world, String target) {
        var e = parse(world, target);
        if (e == null) return null;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.addEnchantment(e.entry(), e.level()); // m101 交易所同款 API（已编译验证）
        return book;
    }

    private static Component enchTargetName0(Level world, String target) {
        var e = parse(world, target);
        return e == null ? null : Enchantment.getName(e.entry(), e.level());
    }

    private static com.sdzjz.machine.EnchantPlanner.Plan enchResolve(Level world, String target) {
        var e = parse(world, target);
        if (e == null) return null;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.addEnchantment(e.entry(), e.level());
        Map<String, Integer> needs = new HashMap<>();
        needs.put(com.sdzjz.machine.EnchantPlanner.BOOK_ID, 1);
        needs.put(com.sdzjz.machine.EnchantPlanner.LAPIS_ID, com.sdzjz.machine.EnchantPlanner.LAPIS_PER_LEVEL * e.level());
        int bMul = Math.max(1, e.entry().value().getAnvilCost() / 2);
        int xp = bMul * e.level() * com.sdzjz.machine.EnchantPlanner.XP_PER_WEIGHT;
        return new com.sdzjz.machine.EnchantPlanner.Plan(needs, xp, book);
    }

}
