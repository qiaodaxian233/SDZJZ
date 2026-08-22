package com.sdzjz.modern;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * m373 Modern(26.2) 酿造域适配器——LegacyRecipeAccess 的 brewResolve/brewTargetStack0 同语义
 * 换装（m180 刀法：BFS 算法一字不改，只换 MC 触点）。只做值转换，零规划算法（作者拍板 B 线
 * 范围铁律：Modern API → 本类 → BrewPlanner，绝不"部分 Planner 再回 Minecraft"）。
 *
 * 【核名出处（一律有据）】
 * - 酿造注册表取用：Level#potionBrewing()（NeoForge port/26.2 的 BrewingStandBlockEntity
 *   补丁原版上下文行核到，26.2 起注册表随 Level 走，Legacy 的 world.getBrewingRecipeRegistry 对位口）；
 * - 材料判定/一步酿造：PotionBrewing#isIngredient(ItemStack)（补丁原版行）、
 *   #mix(材料, 药水栈)（BrewingStand 补丁原版行 items.set(dest, potionBrewing.mix(ingredient, ...))，
 *   参序=Legacy craft(ing, cur) 同序，无配方时返回输入原样→同 Legacy 用"输出与输入相等"判死路）；
 * - 药水组件：DataComponents.POTION_CONTENTS + PotionContents.EMPTY + .potion()→Optional&lt;Holder&lt;Potion&gt;&gt;
 *   （PotionBrewing/BrewingStandMenu 补丁原版行核到；Legacy 的 PotionContentsComponent.DEFAULT 对位口）；
 * - 样板栈：PotionContents.createItemStack(Item, Holder&lt;Potion&gt;)、Potions.WATER/AWKWARD
 *   （fabric-api@26.2 content-registries GameTest 编译级原文）；
 * - 组件相等：ItemStack.isSameItemSameComponents（熔炉/漏斗补丁原版行；Legacy areItemsAndComponentsEqual 对位口）；
 * - Holder 取 id 串：Holder#getRegisteredName()（ServerLevel 补丁原版行；Legacy getIdAsString 对位口）；
 * - 注册表值转 Holder：Registry#wrapAsHolder（NeoForge 主源+测试编译级）；getOptional(Identifier)
 *   =m371 已被 CI 真编译验绿的在树口径。
 *
 * 【缓存归属（作者 B 线架构要求）】材料表缓存在本适配器：键=PotionBrewing 实例身份且走
 * WeakReference（datapack reload 会换新实例→身份失配自动重算；弱引用保证适配器绝不强持
 * 服务端对象——比 Legacy 的 reload 钩清拍更自愈，Legacy INGREDIENTS 静态强持到 clearCaches）。
 * Common 侧 BrewPlanner.CACHE 在 Modern 的清拍挂钩随 Phase 3 消费者接线时补（现 Modern 无生产消费者，立档）。
 */
final class ModernBrewAccess {

    private ModernBrewAccess() { }

    /** 材料缓存：随 PotionBrewing 实例身份失效（reload=新实例）；弱引用防持服务端对象。 */
    private static volatile WeakReference<PotionBrewing> cachedReg = new WeakReference<>(null);
    private static volatile List<ItemStack> cachedIngredients = null;
    /** m425 全图前驱树缓存（Legacy BREW_TREE 对位）：键=实例身份独立一对（treeReg/cachedTree），
     *  写序照 ingredients 同款「值先行键后置」——guard 命中即值已就位。 */
    private static volatile WeakReference<PotionBrewing> treeReg = new WeakReference<>(null);
    private static volatile Map<String, String[]> cachedTree = null;

    /** 目标串（"药水id|p/s/l"）→ 样板栈；非法=null。与 level 无关（静态注册表），Legacy 同规矩。 */
    static ItemStack targetStack(String target) {
        if (target == null || target.length() < 3) return null;
        int cut = target.lastIndexOf('|');
        if (cut <= 0 || cut != target.length() - 2) return null;
        char f = target.charAt(cut + 1);
        Item container = f == 'p' ? Items.POTION
                : f == 's' ? Items.SPLASH_POTION
                : f == 'l' ? Items.LINGERING_POTION : null;
        if (container == null) return null;
        Identifier pid;
        try {
            pid = Identifier.parse(target.substring(0, cut)); // parse=m371 在树验绿口；非法串抛异常→null（Legacy tryParse 对位）
        } catch (Exception ex) {
            return null;
        }
        Potion pot = BuiltInRegistries.POTION.getOptional(pid).orElse(null);
        if (pot == null) return null;
        return PotionContents.createItemStack(container, BuiltInRegistries.POTION.wrapAsHolder(pot));
    }

    /** BFS 状态键=容器物品id+药水id（Legacy key() 原文平移）。 */
    private static String key(ItemStack s) {
        PotionContents pc = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        String pot = pc.potion().map(Holder::getRegisteredName).orElse("-");
        return BuiltInRegistries.ITEM.getKey(s.getItem()) + "|" + pot;
    }

    /** 全物品过一遍 isIngredient 收集酿造材料（Legacy ingredients() 平移，缓存键升实例身份）。 */
    private static List<ItemStack> ingredients(PotionBrewing reg) {
        if (cachedReg.get() == reg) {
            List<ItemStack> hit = cachedIngredients;
            if (hit != null) return hit;
        }
        List<ItemStack> list = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) {
            ItemStack s = new ItemStack(it);
            if (reg.isIngredient(s)) list.add(s);
        }
        cachedIngredients = list;
        cachedReg = new WeakReference<>(reg);
        return list;
    }

    /** m425 全图前驱树（Legacy brewTree 对位）：起点=水瓶，无目标跑满整图
     *  （BFS 循环体与旧 resolve 一字未改，仅去掉 goalKey 早停条件）。 */
    private static Map<String, String[]> tree(PotionBrewing reg) {
        if (treeReg.get() == reg) {
            Map<String, String[]> hit = cachedTree;
            if (hit != null) return hit;
        }
        List<ItemStack> ings = ingredients(reg);

        ItemStack start = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        String startKey = key(start);
        Map<String, String[]> prev = new HashMap<>();   // key → {prevKey, 材料id}；起点值=null
        Map<String, ItemStack> stacks = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        prev.put(startKey, null);
        stacks.put(startKey, start);
        queue.add(startKey);
        while (!queue.isEmpty()) {
            String curKey = queue.poll();
            ItemStack cur = stacks.get(curKey);
            for (ItemStack ing : ings) {
                ItemStack out = reg.mix(ing, cur.copy());
                if (out.isEmpty() || ItemStack.isSameItemSameComponents(out, cur)) continue; // 无此配方
                String ok = key(out);
                if (prev.containsKey(ok)) continue; // BFS 首达即最短链
                prev.put(ok, new String[]{curKey, BuiltInRegistries.ITEM.getKey(ing.getItem()).toString()});
                stacks.put(ok, out);
                queue.add(ok);
            }
        }
        Map<String, String[]> t = Collections.unmodifiableMap(prev); // 起点值=null，Map.copyOf 拒 null 值会 NPE
        cachedTree = t;                      // 值先行
        treeReg = new WeakReference<>(reg);  // 键后置
        return t;
    }

    /** 酿造计划（m425 起：查全图树+回溯；旧=逐目标早停 BFS。Legacy brewResolve 对位）。 */
    static com.sdzjz.machine.BrewPlanner.Plan resolve(Level world, String target) {
        ItemStack goal = targetStack(target);
        if (goal == null) return null;
        String goalKey = key(goal);
        PotionBrewing reg = world.potionBrewing();
        Map<String, String[]> prev = tree(reg);
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
        if (steps == 0) return null; // 目标=水瓶：没有酿造意义，不接（Legacy 同款）
        return new com.sdzjz.machine.BrewPlanner.Plan(Map.copyOf(needs), steps, goal);
    }
}
