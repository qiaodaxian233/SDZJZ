package com.sdzjz.item;

import com.sdzjz.config.SdzjzConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * m311 随身仓库（作者拍板：随身 long 账本 + 吸附模式，"小说系统"风提示音）。
 *
 * 数量不存格子存**自带 long 账本**（物品 CUSTOM_DATA 组件，包换手/进箱子账目跟走）——
 * int 墙碰不到账本，边界换币：倾倒进仓储核心/数据面板时才按 ≤10 亿/笔切块 deposit。
 * 只收"普通可堆叠物"（组件件与 maxCount=1 的工具/终端天然拒收，防抹组件——精确账本同规矩）。
 *
 * 交互四式：
 *  - 潜行右键（空气）= 吸附模式开关。开=每 0.5s 吸附身边掉落物直接入账（背包永远清爽）；
 *    尊重拾取延迟（自己 Q 出去的 2 秒内不回吸）。
 *  - 右键（空气）= 聊天报账（类数/总件 + 前 8 名，K/M/B 简写）。
 *  - 背包里把物品右键点到仓库上 = 整叠收纳（m82 终端镶嵌同款交互）。
 *  - 右键 存储核心/数据面板 = **整包倾倒入仓**（离网归网一键）。
 * GUI 选取式取物挂待办（照数据面板刀法裁小屏，m312 候选）。
 */
public class PortableVaultItem extends Item {

    private static final String K_VAULT = "vlt", K_MAG = "mag";

    public PortableVaultItem(Settings settings) {
        super(settings);
    }

    // ===== 账本纯函数（gametest 直测这四个口）=====

    static NbtCompound rootOf(ItemStack s) {
        NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
        return c != null ? c.copyNbt() : new NbtCompound();
    }

    static void writeRoot(ItemStack s, NbtCompound root) {
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    /** 入账 n 个 id（饱和加法防翻负，m273 satAdd 同口径）。返回 false=类型已满拒收新类型。 */
    public static boolean vaultAdd(ItemStack vault, String id, long n) {
        if (n <= 0) return true;
        NbtCompound root = rootOf(vault);
        NbtCompound v = root.getCompound(K_VAULT);
        boolean known = v.contains(id);
        int cap = SdzjzConfig.get().portableVaultTypeCap;
        if (!known && cap > 0 && v.getKeys().size() >= cap) return false; // 只闸新类型，已有类型照常并账（m270 口径）
        long cur = v.getLong(id);
        long next = cur + n;
        if (next < cur) next = Long.MAX_VALUE; // 饱和
        v.putLong(id, next);
        root.put(K_VAULT, v);
        writeRoot(vault, root);
        return true;
    }

    /** 账本类数。 */
    public static int vaultTypes(ItemStack vault) {
        return rootOf(vault).getCompound(K_VAULT).getKeys().size();
    }

    /** 账本总件数（饱和）。 */
    public static long vaultTotal(ItemStack vault) {
        NbtCompound v = rootOf(vault).getCompound(K_VAULT);
        long sum = 0;
        for (String k : v.getKeys()) {
            long x = sum + v.getLong(k);
            sum = x < sum ? Long.MAX_VALUE : x;
        }
        return sum;
    }

    /** 整包倾倒进核心账本：long 切 ≤10 亿/笔过 deposit(int 计数) 边界，倒空即清账。 */
    public static void vaultDumpInto(ItemStack vault, com.sdzjz.block.StorageCoreBlockEntity core) {
        NbtCompound root = rootOf(vault);
        NbtCompound v = root.getCompound(K_VAULT);
        for (String id : new java.util.ArrayList<>(v.getKeys())) {
            long left = v.getLong(id);
            Item it = Registries.ITEM.get(Identifier.of(id));
            if (it == net.minecraft.item.Items.AIR) { v.remove(id); continue; } // 卸模组遗留死键：清账不造物
            while (left > 0) {
                int chunk = (int) Math.min(1_000_000_000L, left);
                core.deposit(new ItemStack(it, chunk));
                left -= chunk;
            }
            v.remove(id);
        }
        root.put(K_VAULT, v);
        writeRoot(vault, root);
    }

    private static boolean magnetOn(ItemStack s) { return rootOf(s).getBoolean(K_MAG); }

    /** 可收纳判定：普通可堆叠物（组件件与 maxCount=1 的工具/终端/本仓库天然出局）。 */
    private static boolean absorbable(ItemStack s) {
        return !s.isEmpty() && s.getMaxCount() > 1 && s.getComponentChanges().isEmpty();
    }

    // ===== 交互 =====

    /** 潜行右键=吸附开关；右键=报账。 */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        if (player.isSneaking()) {
            NbtCompound root = rootOf(stack);
            boolean on = !root.getBoolean(K_MAG);
            root.putBoolean(K_MAG, on);
            writeRoot(stack, root);
            bar(player, on ? "叮！吸附模式已开启——掉落物将直接收入仓库" : "吸附模式已关闭");
            return TypedActionResult.success(stack);
        }
        NbtCompound v = rootOf(stack).getCompound(K_VAULT);
        if (v.isEmpty()) { bar(player, "仓库空空如也（潜行右键开吸附，或把物品右键点到仓库上收纳）"); return TypedActionResult.success(stack); }
        java.util.List<String> ids = new java.util.ArrayList<>(v.getKeys());
        ids.sort((a, b) -> Long.compare(v.getLong(b), v.getLong(a)));
        player.sendMessage(Text.literal("§b【随身仓库】§7类型 " + ids.size() + " · 总件 " + fmt(vaultTotal(stack))), false);
        for (int i = 0; i < Math.min(8, ids.size()); i++) {
            String id = ids.get(i);
            String name = Registries.ITEM.get(Identifier.of(id)).getName().getString();
            player.sendMessage(Text.literal("§7  " + name + " §f×" + fmt(v.getLong(id))), false);
        }
        if (ids.size() > 8) player.sendMessage(Text.literal("§8  …共 " + ids.size() + " 类（右键存储核心/数据面板整包入仓）"), false);
        return TypedActionResult.success(stack);
    }

    /** 右键存储核心/数据面板 = 整包倾倒入仓。 */
    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack vault = ctx.getStack();
        long total = vaultTotal(vault);
        int types = vaultTypes(vault);
        var be = world.getBlockEntity(ctx.getBlockPos());
        com.sdzjz.block.StorageCoreBlockEntity core = null;
        if (be instanceof com.sdzjz.block.StorageCoreBlockEntity c) core = c;
        else if (be instanceof com.sdzjz.block.DataPanelBlockEntity p) {
            var cores = com.sdzjz.block.StorageCoreBlockEntity.connectedCores(world, ctx.getBlockPos());
            if (!cores.isEmpty()) core = cores.iterator().next();
        }
        if (core == null) return ActionResult.PASS;
        if (total <= 0) { bar(ctx.getPlayer(), "仓库是空的，没什么可倾倒"); return ActionResult.SUCCESS; }
        vaultDumpInto(vault, core);
        bar(ctx.getPlayer(), "叮！已入仓 " + types + " 类 · " + fmt(total) + " 件");
        return ActionResult.SUCCESS;
    }

    /** 背包里把物品右键点到仓库上=整叠收纳（m82 终端镶嵌同款交互）。 */
    @Override
    public boolean onClicked(ItemStack stack, ItemStack otherStack, net.minecraft.screen.slot.Slot slot,
                             net.minecraft.util.ClickType clickType, PlayerEntity player,
                             net.minecraft.inventory.StackReference cursorStackReference) {
        if (clickType != net.minecraft.util.ClickType.RIGHT || otherStack.isEmpty()) return false;
        if (!absorbable(otherStack)) { bar(player, "带组件/不可堆叠物品不入仓（防抹组件）"); return true; }
        String id = Registries.ITEM.getId(otherStack.getItem()).toString();
        if (!vaultAdd(stack, id, otherStack.getCount())) { bar(player, "仓库类型已满（config portableVaultTypeCap）"); return true; }
        bar(player, "叮！已收纳 " + otherStack.getName().getString() + " ×" + fmt(otherStack.getCount()));
        otherStack.setCount(0);
        return true;
    }

    /** 吸附：每 10t 扫身边掉落物直接入账（尊重拾取延迟；一拍一次组件写=天然批量）。 */
    @Override
    public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slotIdx, boolean selected) {
        if (world.isClient || world.getTime() % 10 != 0) return;
        if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)) return;
        if (!magnetOn(stack)) return;
        int r = SdzjzConfig.get().portableVaultMagnetRadius;
        if (r <= 0) return;
        NbtCompound root = rootOf(stack);
        NbtCompound v = root.getCompound(K_VAULT);
        int cap = SdzjzConfig.get().portableVaultTypeCap;
        long got = 0; int kinds = 0;
        for (ItemEntity e : world.getEntitiesByClass(ItemEntity.class,
                Box.of(player.getPos(), r * 2, r * 2, r * 2), en -> !en.cannotPickup() && absorbable(en.getStack()))) {
            ItemStack s = e.getStack();
            String id = Registries.ITEM.getId(s.getItem()).toString();
            boolean known = v.contains(id);
            if (!known && cap > 0 && v.getKeys().size() >= cap) continue; // 满型跳过新类型，已有类型照吸
            long cur = v.getLong(id);
            long next = cur + s.getCount();
            if (next < cur) next = Long.MAX_VALUE;
            v.putLong(id, next);
            got += s.getCount(); if (!known) kinds++;
            e.discard();
        }
        if (got > 0) {
            root.put(K_VAULT, v);
            writeRoot(stack, root);
            bar(player, "叮！吸附收纳 ×" + fmt(got) + (kinds > 0 ? "（新入 " + kinds + " 类）" : ""));
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, java.util.List<Text> tooltip,
                              net.minecraft.item.tooltip.TooltipType type) {
        NbtCompound v = rootOf(stack).getCompound(K_VAULT);
        tooltip.add(Text.literal(magnetOn(stack) ? "吸附: 开（潜行右键关闭）" : "吸附: 关（潜行右键开启）")
                .formatted(net.minecraft.util.Formatting.AQUA));
        tooltip.add(Text.literal("账本: " + v.getKeys().size() + " 类 · " + fmt(vaultTotal(stack)) + " 件")
                .formatted(net.minecraft.util.Formatting.GRAY));
        java.util.List<String> ids = new java.util.ArrayList<>(v.getKeys());
        ids.sort((a, b) -> Long.compare(v.getLong(b), v.getLong(a)));
        for (int i = 0; i < Math.min(3, ids.size()); i++) {
            String id = ids.get(i);
            tooltip.add(Text.literal("  " + Registries.ITEM.get(Identifier.of(id)).getName().getString()
                    + " ×" + fmt(v.getLong(id))).formatted(net.minecraft.util.Formatting.DARK_GRAY));
        }
        tooltip.add(Text.literal("右键存储核心/数据面板=整包入仓").formatted(net.minecraft.util.Formatting.DARK_GRAY));
    }

    private static void bar(PlayerEntity p, String s) {
        if (p != null) p.sendMessage(Text.literal(s), true);
    }

    private static String fmt(long n) { // m232 口径 K/M/B/T
        if (n >= 1_000_000_000_000L) return one(n / 1.0e12) + "T";
        if (n >= 1_000_000_000L) return one(n / 1.0e9) + "B";
        if (n >= 1_000_000L) return one(n / 1.0e6) + "M";
        if (n >= 10_000L) return one(n / 1.0e3) + "K";
        return String.valueOf(n);
    }

    private static String one(double x) {
        return x >= 100 ? String.valueOf((long) x)
                : String.valueOf(Math.round(x * 10) / 10.0).replaceAll("\\.0$", "");
    }
}
