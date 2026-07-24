package com.sdzjz.machine;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔工厂的成本解析（m132）：给定目标串「附魔id|等级」（如 "minecraft:sharpness|5"），
 * 从附魔动态注册表（1.21 起附魔是数据驱动注册表，须经 world 取）解析出一本书的产出计划。
 * 注册表驱动=第三方模组附魔（含诅咒）天然全谱支持，零白名单。
 *
 * 成本（原版锚定，公式集中在此处便于调参）：
 * - 书 ×1、青金石 ×(3×等级)——附魔台单次最多吃 3 青金石，按等级线性放大；
 * - 经验 = 书倍率B × 等级 × 25 点，从画布核心的经验池(xpPool)扣——
 *   B = max(1, anvilCost/2)（原版铁砧的"附魔书减半"倍率：1/2/4/8 → 1/1/2/4），
 *   刷怪塔/熔炉在同一画布攒的经验直接喂附魔，闭环不引入新经济。
 *
 * 缓存按目标串（Plan 持有注册表绑定的样板栈，服务器停止时清空，见 Sdzjz 的
 * SERVER_STOPPED，与 Brew/CraftPlanner 同位）；客户端只走 targetStack/targetName
 * 无缓存（附魔注册表随存档/数据包变，不做跨世界静态缓存）。
 */
public final class EnchantPlanner {
    private EnchantPlanner() {}

    public static final String BOOK_ID = "minecraft:book";
    public static final String LAPIS_ID = "minecraft:lapis_lazuli";
    /** 每级青金石消耗。 */
    public static final int LAPIS_PER_LEVEL = 3;
    /** 经验点/单位权重（权重=书倍率×等级）。 */
    public static final int XP_PER_WEIGHT = 25;

    /**
     * needs: 物品消耗（书×1 + 青金石×3L）；xpCost: 每本书经验点（核心经验池扣）；
     * result: 目标附魔书样板栈（count=1，入库走 m130 精确账本）。
     */
    public record Plan(Map<String, Integer> needs, int xpCost, ItemStack result) {}

    private static final Map<String, Optional<Plan>> CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        CACHE.clear();
    }

    /** 返回目标附魔书的生产计划；串非法/附魔不存在/等级越界返回 null。 */
    public static Plan plan(World world, String target) {
        return CACHE.computeIfAbsent(target, t -> Optional.ofNullable(resolve(world, t))).orElse(null);
    }

    /** 目标串 → 附魔书样板栈；解析失败返回 null。客户端画徽章/选择器同用（无缓存）。 */
    public static ItemStack targetStack(World world, String target) {
        var e = parse(world, target);
        if (e == null) return null;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.addEnchantment(e.entry(), e.level()); // m101 交易所同款 API（已编译验证）
        return book;
    }

    /** 目标串 → 展示名（原版 Enchantment.getName：自带罗马数字等级与诅咒红字）。 */
    public static Text targetName(World world, String target) {
        var e = parse(world, target);
        return e == null ? null : Enchantment.getName(e.entry(), e.level());
    }

    private record Parsed(RegistryEntry<Enchantment> entry, int level) {}

    /** 解析并校验目标串：附魔在注册表 且 1 ≤ 等级 ≤ maxLevel。 */
    private static Parsed parse(World world, String target) {
        if (world == null || target == null || target.length() < 3) return null;
        int cut = target.lastIndexOf('|');
        if (cut <= 0 || cut >= target.length() - 1) return null;
        int lv;
        try {
            lv = Integer.parseInt(target.substring(cut + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        Identifier id = Identifier.tryParse(target.substring(0, cut));
        if (id == null) return null;
        var reg = world.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
        var entry = reg.getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, id));
        if (entry.isEmpty()) return null;
        Enchantment ench = entry.get().value();
        if (lv < 1 || lv > ench.getMaxLevel()) return null;
        return new Parsed(entry.get(), lv);
    }

    private static Plan resolve(World world, String target) {
        var e = parse(world, target);
        if (e == null) return null;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.addEnchantment(e.entry(), e.level());
        Map<String, Integer> needs = new HashMap<>();
        needs.put(BOOK_ID, 1);
        needs.put(LAPIS_ID, LAPIS_PER_LEVEL * e.level());
        int bMul = Math.max(1, e.entry().value().getAnvilCost() / 2);
        int xp = bMul * e.level() * XP_PER_WEIGHT;
        return new Plan(needs, xp, book);
    }
}
