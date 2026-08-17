package com.sdzjz.modern;

import com.sdzjz.machine.CraftPlanner;
import net.fabricmc.fabric.api.recipe.v1.FabricRecipeManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * m371 Modern(26.2) 配方查询实现——LegacyRecipeAccess 的 craft/smelt/remainder 三口同语义换装；
 * m373 起 brew/ench 五口经 ModernBrewAccess/ModernEnchantAccess 分域委托补齐（RecipeAccess
 * 四域全承，与 Legacy 单实现类对位）。结构与 Legacy 逐段对位（m180 刀法：算法一字不改，
 * 只换 MC 触点），口径差异全部立档：
 *
 * 【核名出处（一律有据，官方名无 yarn）】
 * - 枚举：fabric-api@26.2 给 RecipeManager 注入 FabricRecipeManager#getAllOfType（旧 listAllOfType
 *   的新世代等价口，RecipeMap.byType 兜底实现，官方 RecipeManagerMixin 原文核到）；
 * - 原料：Ingredient#items() → Stream&lt;Holder&lt;Item&gt;&gt;（NeoForge port/26.2 原版补丁上下文核到；
 *   fabric 自定义材料 CustomIngredientImpl 覆写 items()=口径与 Legacy getMatchingStacks 对齐）；
 * - 残留：Item#getCraftingRemainder() → @Nullable ItemStackTemplate（原版 CraftingRecipe 补丁
 *   上下文核到，null=无残留；template.item()/create() 见 fabric transfer-api 原文）；
 * - 产物：assemble(输入) 单参（NeoForge GlobalLootModifiersTest 熔炼链原文核到）；
 * - 注册表：BuiltInRegistries.ITEM.getOptional(Identifier)/getKey(Item)、Identifier.parse
 *   （fabric RecipeSyncImpl 等原文核到）；配方 id：holder.id().identifier()。
 *
 * 【待编译验证（CI modern-bootstrap job=真判官，红了按此修）】
 * - placementInfo().ingredients()：PlacementInfo 类与 Recipe#placementInfo() 已在 NeoForge 测试
 *   override 核到，ingredients() 读法沿 1.21.2+ 官方名——若红，备胎=display() 路
 *   （ShapedCraftingRecipeDisplay.ingredients() 已核到，成本高一档故不首选）；
 * - 合成产物走 assemble(空 CraftingInput)：shaped/shapeless 的 result 为字段、assemble 历代不看
 *   输入；特殊配方（烟花/染色等）看输入→空输入返 EMPTY 或抛异常→try/catch 跳过，与 Legacy
 *   对 getResult 的 try/catch 同语义。
 *
 * 【立档：26.2 客户端不可枚举配方】新世代配方不全量同步客户端，recipeAccess() 在客户端不是
 * RecipeManager→本实现返回空表。Legacy"客户端画布屏也调 plans"的消费面属 Phase 3 议题
 * （fabric SynchronizedRecipes 按序列化器选择性同步是候选通道），Modern 侧现无客户端消费者。
 *
 * 无状态：level 随调用传，绝不持服务端引用（Legacy 同规矩）。
 */
public final class ModernRecipeAccess implements com.sdzjz.platform.RecipeAccess {

    /** 空合成输入：shaped/shapeless 的 assemble 不看输入，特殊配方在此现形被跳过。 */
    private static final CraftingInput EMPTY_CRAFT = CraftingInput.of(0, 0, List.of());

    @Override
    public java.util.List<CraftPlanner.Plan> craftingPlans(Object level, String targetId) {
        Level world = (Level) level; // 代际句柄向下转型：Modern 世界即官方名 Level
        Item target = BuiltInRegistries.ITEM.getOptional(Identifier.parse(targetId)).orElse(null);
        if (target == null) return java.util.List.of();
        if (!(world.recipeAccess() instanceof FabricRecipeManager frm)) return java.util.List.of(); // 客户端：见类注释立档
        java.util.List<Map.Entry<Identifier, CraftPlanner.Plan>> found = new java.util.ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : frm.<CraftingInput, CraftingRecipe>getAllOfType(RecipeType.CRAFTING)) {
            CraftingRecipe r = holder.value();
            ItemStack out;
            try {
                out = r.assemble(EMPTY_CRAFT);
            } catch (Exception ex) {
                continue; // 特殊配方（烟花/染色等）空输入可能异常，跳过——Legacy 对 getResult 同款
            }
            if (out == null || out.isEmpty() || out.getItem() != target) continue;

            java.util.List<Ingredient> ings;
            try {
                ings = r.placementInfo().ingredients();
            } catch (Exception ex) {
                continue; // 不可摆放/无固定材料的特殊配方，跳过
            }
            Map<String, Integer> needs = new java.util.LinkedHashMap<>();
            Map<String, Integer> remainders = new java.util.LinkedHashMap<>();
            Map<java.util.List<String>, Integer> groupCount = new java.util.LinkedHashMap<>(); // m343 同候选集槽位合并
            boolean ok = true;
            for (Ingredient ing : ings) {
                if (ing.isEmpty()) continue;
                java.util.List<Holder<Item>> matching = ing.items().toList();
                if (matching.isEmpty()) { ok = false; break; }
                java.util.LinkedHashSet<String> cset = new java.util.LinkedHashSet<>(); // 保 items 序去重
                for (Holder<Item> h : matching) cset.add(BuiltInRegistries.ITEM.getKey(h.value()).toString());
                groupCount.merge(java.util.List.copyOf(cset), 1, Integer::sum);
                Item pick = matching.getFirst().value(); // 首选口径（显示/回退；实际计数扣料走 groups——m343）
                needs.merge(BuiltInRegistries.ITEM.getKey(pick).toString(), 1, Integer::sum);
                ItemStackTemplate rem = pick.getCraftingRemainder();
                if (rem != null) remainders.merge(BuiltInRegistries.ITEM.getKey(rem.item().value()).toString(), 1, Integer::sum);
            }
            if (!ok || needs.isEmpty()) continue; // 无固定材料的特殊配方不支持——Legacy 同款
            java.util.List<CraftPlanner.Group> groups = new java.util.ArrayList<>(groupCount.size());
            for (var en : groupCount.entrySet()) groups.add(new CraftPlanner.Group(en.getKey(), en.getValue()));
            found.add(Map.entry(holder.id().identifier(),
                    new CraftPlanner.Plan(needs, out.getCount(), remainders, holder.id().identifier().toString(), java.util.List.copyOf(groups))));
        }
        found.sort(java.util.Comparator
                .comparingInt((Map.Entry<Identifier, CraftPlanner.Plan> e) -> "minecraft".equals(e.getKey().getNamespace()) ? 0 : 1)
                .thenComparing(e -> e.getKey().toString()));
        return found.stream().map(Map.Entry::getValue).toList();
    }

    @Override
    public java.util.Map<String, java.util.List<Object[]>> smeltingCandidates(Object level) {
        Level world = (Level) level;
        java.util.Map<String, java.util.List<Object[]>> cands = new java.util.HashMap<>();
        if (!(world.recipeAccess() instanceof FabricRecipeManager frm)) return cands; // 客户端：见类注释立档
        for (RecipeHolder<SmeltingRecipe> holder : frm.<SingleRecipeInput, SmeltingRecipe>getAllOfType(RecipeType.SMELTING)) {
            try {
                java.util.List<Ingredient> ings = holder.value().placementInfo().ingredients();
                if (ings.isEmpty()) continue;
                java.util.List<Holder<Item>> first = ings.getFirst().items().toList();
                if (first.isEmpty()) continue;
                // 真输入 assemble：熔炼产物不随输入变，取首个候选当输入即得配方产物（NeoForge 熔炼链同款）
                ItemStack out = holder.value().assemble(new SingleRecipeInput(new ItemStack(first.getFirst().value())));
                if (out == null || out.isEmpty()) continue;
                String rid = holder.id().identifier().toString();
                String outId = BuiltInRegistries.ITEM.getKey(out.getItem()).toString();
                int outCount = out.getCount();
                for (Ingredient ing : ings) {
                    for (Holder<Item> h : ing.items().toList()) {
                        cands.computeIfAbsent(BuiltInRegistries.ITEM.getKey(h.value()).toString(), k -> new java.util.ArrayList<>())
                                .add(new Object[]{rid, outId, outCount});
                    }
                }
            } catch (Exception ignored) { }
        }
        return cands;
    }

    @Override
    public String craftRemainderOf(String itemId) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId)).orElse(null);
        if (item == null) return null;
        ItemStackTemplate rem = item.getCraftingRemainder();
        return rem != null ? BuiltInRegistries.ITEM.getKey(rem.item().value()).toString() : null;
    }

    // ===== 酿造/附魔域（m373 作者拍板 B 线收齐：分域适配器委托——m368 拆四域接口的
    //  "26.x 可分域迁移各自 ModernXxxAccess 再组合"首兑现；核名出处见各适配器类注释） =====

    @Override
    public com.sdzjz.machine.BrewPlanner.Plan brewingPlan(Object level, String target) {
        return ModernBrewAccess.resolve((Level) level, target);
    }

    @Override
    public Object brewTargetStack(String target) {
        return ModernBrewAccess.targetStack(target);
    }

    @Override
    public com.sdzjz.machine.EnchantPlanner.Plan enchantingPlan(Object level, String target) {
        return ModernEnchantAccess.resolve((Level) level, target);
    }

    @Override
    public Object enchantTargetStack(Object level, String target) {
        return ModernEnchantAccess.targetStack((Level) level, target);
    }

    @Override
    public Object enchantTargetName(Object level, String target) {
        return ModernEnchantAccess.targetName((Level) level, target);
    }
}
