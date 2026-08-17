package com.sdzjz.modern;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * m373 Modern(26.2) 附魔域适配器——LegacyRecipeAccess 的 parse/enchResolve/enchTargetStack0/
 * enchTargetName0 同语义换装（m180 刀法）。无状态零缓存：附魔注册表随存档/数据包变（Legacy
 * 同规矩），只做值转换零规划算法——成本公式的常量（书×1/青金石×3L/经验=书倍率×等级×25）
 * 全部引 Common 的 EnchantPlanner 唯一源，语义逐位同 Legacy。
 *
 * 【核名出处（一律有据）】
 * - 注册表链：level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)（NeoForge port/26.2
 *   GlobalLootModifiersTest 编译级原文）；lookup.get(ResourceKey)→Optional（TreeGrower/MushroomBlock
 *   补丁原版上下文行 .get(featureKey).orElse(null) 同形）；
 * - 键构造：ResourceKey.create(Registries.ENCHANTMENT, Identifier)（NeoForge 测试编译级原文）；
 * - 等级上限：Enchantment#getMaxLevel()（EnchantmentHelper 补丁原版行 + NeoForge 测试
 *   enchantment.value().getMaxLevel() 编译级）；
 * - 上书：ItemStack#enchant(Holder&lt;Enchantment&gt;, int)（EnchantCommand 补丁原版行
 *   item.enchant(enchantmentHolder, level)；Legacy addEnchantment 对位口）；
 * - 展示名：Enchantment.getFullname(Holder, int)→Component（EnchantmentScreen 补丁被删原版行
 *   =原版源码原文；Legacy Enchantment.getName 对位口，自带罗马数字与诅咒红字）。
 *
 * 【待编译验证（CI modern-bootstrap job=真判官）】
 * - Enchantment#getAnvilCost()：两先例库补丁均不覆盖该行且零消费——1.21 官方名同名 helper +
 *   26.2 的 Enchantment.definition(...) 静态工厂第 6 参仍为 anvilCost（NeoForge 测试编译级）
 *   =记录形状未变，高置信保留；若红换 definition().anvilCost()（definition() 访问器已由
 *   fabric-api EnchantmentUtil 编译级实锤）。
 */
final class ModernEnchantAccess {

    private ModernEnchantAccess() { }

    private record Parsed(Holder<Enchantment> entry, int level) { }

    /** 解析并校验目标串（"附魔id|等级"）：附魔在注册表 且 1 ≤ 等级 ≤ maxLevel（Legacy parse 平移）。 */
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
        Identifier id;
        try {
            id = Identifier.parse(target.substring(0, cut)); // parse=m371 在树验绿口；非法串抛异常→null（Legacy tryParse 对位）
        } catch (Exception ex) {
            return null;
        }
        var reg = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var entry = reg.get(ResourceKey.create(Registries.ENCHANTMENT, id));
        if (entry.isEmpty()) return null;
        Enchantment ench = entry.get().value();
        if (lv < 1 || lv > ench.getMaxLevel()) return null;
        return new Parsed(entry.get(), lv);
    }

    /** 目标样板书；非法=null（Legacy enchTargetStack0 平移）。 */
    static ItemStack targetStack(Level world, String target) {
        Parsed e = parse(world, target);
        if (e == null) return null;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(e.entry(), e.level());
        return book;
    }

    /** 展示名（罗马数字等级/诅咒红字）；非法=null（Legacy enchTargetName0 平移）。 */
    static Component targetName(Level world, String target) {
        Parsed e = parse(world, target);
        return e == null ? null : Enchantment.getFullname(e.entry(), e.level());
    }

    /** 一本书的产出计划（Legacy enchResolve 平移，成本常量引 EnchantPlanner 唯一源）。 */
    static com.sdzjz.machine.EnchantPlanner.Plan resolve(Level world, String target) {
        Parsed e = parse(world, target);
        if (e == null) return null;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(e.entry(), e.level());
        Map<String, Integer> needs = new HashMap<>();
        needs.put(com.sdzjz.machine.EnchantPlanner.BOOK_ID, 1);
        needs.put(com.sdzjz.machine.EnchantPlanner.LAPIS_ID, com.sdzjz.machine.EnchantPlanner.LAPIS_PER_LEVEL * e.level());
        int bMul = Math.max(1, e.entry().value().getAnvilCost() / 2);
        int xp = bMul * e.level() * com.sdzjz.machine.EnchantPlanner.XP_PER_WEIGHT;
        return new com.sdzjz.machine.EnchantPlanner.Plan(needs, xp, book);
    }
}
