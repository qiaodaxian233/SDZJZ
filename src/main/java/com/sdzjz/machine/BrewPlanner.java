package com.sdzjz.machine;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 酿造塔的配方解析（m131b）：给定目标药水串「药水id|形态」，从原版酿造注册表
 * （BrewingRecipeRegistry）出发做 BFS——起点=水瓶，边=有效酿造材料的一步 craft()，
 * 终点=目标药水栈。天然覆盖 延长(redstone)/强化(glowstone)/喷溅(gunpowder)/
 * 滞留(dragon_breath) 全链，且第三方模组照原版路子注册的药水配方一并支持。
 * 解析结果按目标串缓存（服务器停止时清空，见 Sdzjz 的 SERVER_STOPPED，与 CraftPlanner 同位）。
 *
 * 形态码：p=普通药水 s=喷溅 l=滞留。延长/强化是独立的药水注册项
 * （minecraft:long_swiftness / minecraft:strong_swiftness），选择器里直接列出即可。
 */
public final class BrewPlanner {
    private BrewPlanner() {}

    /** 燃料：原版 1 烈焰粉 = 20 次酿造操作（一次操作=一批 3 瓶走一步）。 */
    public static final String FUEL_ID = "minecraft:blaze_powder";
    public static final int OPS_PER_FUEL = 20;
    /** 原版酿造台一批 3 瓶。 */
    public static final int BOTTLES_PER_BATCH = 3;

    /**
     * needs: 每批(3 瓶)消耗 物品id→数量（含玻璃瓶×3 与各步材料各×1，不含燃料——
     * 燃料按 steps 在 tick 里聚合结算，材料本身含烈焰粉的力量药水两账并存不混）；
     * steps: 酿造步数；result: 目标药水样板栈（count=1，带 POTION_CONTENTS，
     * 入库走 m130 精确账本）。
     */
    public record Plan(Map<String, Integer> needs, int steps, ItemStack result) {}

    private static final Map<String, Optional<Plan>> CACHE = new ConcurrentHashMap<>();
    private static volatile List<ItemStack> INGREDIENTS; // 有效酿造材料缓存（各 count=1）

    public static void clearCache() {
        CACHE.clear();
        INGREDIENTS = null;
    }

    /**
     * 目标串 → 展示/校验用样板栈；解析失败返回 null。纯注册表实现，客户端画徽章同用。
     * 目标串格式："minecraft:strong_swiftness|s"。
     */
    public static ItemStack targetStack(String target) {
        if (target == null || target.length() < 3) return null;
        int cut = target.lastIndexOf('|');
        if (cut <= 0 || cut != target.length() - 2) return null;
        char f = target.charAt(cut + 1);
        Item container = f == 'p' ? Items.POTION
                : f == 's' ? Items.SPLASH_POTION
                : f == 'l' ? Items.LINGERING_POTION : null;
        if (container == null) return null;
        Identifier pid = Identifier.tryParse(target.substring(0, cut));
        if (pid == null) return null;
        var entry = Registries.POTION.getEntry(pid);
        if (entry.isEmpty()) return null;
        return PotionContentsComponent.createStack(container, entry.get());
    }

    /** 返回目标药水的酿造计划；串非法/不可达返回 null。 */
    public static Plan plan(World world, String target) {
        return CACHE.computeIfAbsent(target, t -> Optional.ofNullable(resolve(world, t))).orElse(null);
    }

    /** 全物品过一遍 isValidIngredient 收集酿造材料（一次性，注册表不会中途变）。 */
    private static List<ItemStack> ingredients(World world) {
        List<ItemStack> list = INGREDIENTS;
        if (list != null) return list;
        var reg = world.getBrewingRecipeRegistry();
        list = new ArrayList<>();
        for (Item it : Registries.ITEM) {
            ItemStack s = new ItemStack(it);
            if (reg.isValidIngredient(s)) list.add(s);
        }
        INGREDIENTS = list;
        return list;
    }

    /** BFS 状态键=容器物品id+药水id（原版酿造产物必为注册药水，自定义效果不在酿造图里）。 */
    private static String key(ItemStack s) {
        PotionContentsComponent pc = s.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
        String pot = pc.potion().map(e -> e.getIdAsString()).orElse("-");
        return Registries.ITEM.getId(s.getItem()) + "|" + pot;
    }

    private static Plan resolve(World world, String target) {
        ItemStack goal = targetStack(target);
        if (goal == null) return null;
        String goalKey = key(goal);
        var reg = world.getBrewingRecipeRegistry();
        List<ItemStack> ings = ingredients(world);

        ItemStack start = PotionContentsComponent.createStack(Items.POTION, Potions.WATER);
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
                prev.put(ok, new String[]{curKey, Registries.ITEM.getId(ing.getItem()).toString()});
                stacks.put(ok, out);
                queue.add(ok);
            }
        }
        if (!prev.containsKey(goalKey)) return null; // 不可达（如平凡药水串错/模组卸载）

        Map<String, Integer> needs = new HashMap<>();
        needs.put("minecraft:glass_bottle", BOTTLES_PER_BATCH);
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
        return new Plan(Map.copyOf(needs), steps, goal);
    }
}
