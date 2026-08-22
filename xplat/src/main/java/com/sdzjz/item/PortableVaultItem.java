package com.sdzjz.item;

import com.sdzjz.config.SdzjzConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

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

    public PortableVaultItem(Properties settings) {
        super(settings);
    }

    // ===== 账本纯函数（gametest 直测这四个口）=====

    static CompoundTag rootOf(ItemStack s) {
        CustomData c = s.get(DataComponents.CUSTOM_DATA);
        return c != null ? c.copyTag() : new CompoundTag();
    }

    static void writeRoot(ItemStack s, CompoundTag root) {
        com.sdzjz.item.ItemData.write(s, root);
    }

    /** 入账 n 个 id（饱和加法防翻负，m273 satAdd 同口径）。返回 false=类型已满拒收新类型。 */
    public static boolean vaultAdd(ItemStack vault, String id, long n) {
        if (n <= 0) return true;
        CompoundTag root = rootOf(vault);
        CompoundTag v = root.getCompound(K_VAULT);
        boolean known = v.contains(id);
        int cap = SdzjzConfig.get().portableVaultTypeCap;
        if (!known && cap > 0 && v.getAllKeys().size() >= cap) return false; // 只闸新类型，已有类型照常并账（m270 口径）
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
        return rootOf(vault).getCompound(K_VAULT).getAllKeys().size();
    }

    /** 账本总件数（饱和）。 */
    public static long vaultTotal(ItemStack vault) {
        CompoundTag v = rootOf(vault).getCompound(K_VAULT);
        long sum = 0;
        for (String k : v.getAllKeys()) {
            long x = sum + v.getLong(k);
            sum = x < sum ? Long.MAX_VALUE : x;
        }
        return sum;
    }

    /** 整包倾倒进核心账本：long 切 ≤10 亿/笔过 deposit(int 计数) 边界，倒空即清账。 */
    public static void vaultDumpInto(ItemStack vault, com.sdzjz.block.StorageCoreBlockEntity core) {
        CompoundTag root = rootOf(vault);
        CompoundTag v = root.getCompound(K_VAULT);
        for (String id : new java.util.ArrayList<>(v.getAllKeys())) {
            long left = v.getLong(id);
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (it == net.minecraft.world.item.Items.AIR) { v.remove(id); continue; } // 卸模组遗留死键：清账不造物
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

    /** m312 账本读/写公开口（GUI handler 用；K_VAULT 键保持私有，判定唯一出口思路同 isBoundTo）。 */
    public static CompoundTag ledger(ItemStack vault) { return rootOf(vault).getCompound(K_VAULT); }

    public static void writeLedger(ItemStack vault, CompoundTag ledger) {
        CompoundTag root = rootOf(vault);
        root.put(K_VAULT, ledger);
        writeRoot(vault, root);
    }

    private static boolean magnetOn(ItemStack s) { return rootOf(s).getBoolean(K_MAG); }

    /** 可收纳判定：普通可堆叠物（组件件与 maxCount=1 的工具/终端/本仓库天然出局）。 */
    private static boolean absorbable(ItemStack s) {
        return !s.isEmpty() && s.getMaxStackSize() > 1 && s.getComponentsPatch().isEmpty();
    }

    // ===== 交互 =====

    /** 潜行右键=吸附开关；右键=报账。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) return InteractionResultHolder.success(stack);
        if (player.isShiftKeyDown()) {
            CompoundTag root = rootOf(stack);
            boolean on = !root.getBoolean(K_MAG);
            root.putBoolean(K_MAG, on);
            writeRoot(stack, root);
            bar(player, on ? "叮！吸附模式已开启——掉落物将直接收入仓库" : "吸附模式已关闭");
            return InteractionResultHolder.success(stack);
        }
        // m312：右键=开取物屏（聊天报账退役，明细进 GUI）
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (syncId, inv, pl) -> new com.sdzjz.screen.PortableVaultScreenHandler(syncId, inv),
                stack.getHoverName()));
        return InteractionResultHolder.success(stack);
    }

    /** 右键存储核心/数据面板 = 整包倾倒入仓。 */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        if (world.isClientSide) return InteractionResult.SUCCESS;
        ItemStack vault = ctx.getItemInHand();
        long total = vaultTotal(vault);
        int types = vaultTypes(vault);
        var be = world.getBlockEntity(ctx.getClickedPos());
        com.sdzjz.block.StorageCoreBlockEntity core = null;
        if (be instanceof com.sdzjz.block.StorageCoreBlockEntity c) core = c;
        else if (be instanceof com.sdzjz.block.DataPanelBlockEntity p) {
            var cores = com.sdzjz.block.StorageCoreBlockEntity.connectedCores(world, ctx.getClickedPos());
            if (!cores.isEmpty()) core = cores.iterator().next();
        }
        if (core == null) return InteractionResult.PASS;
        if (total <= 0) { bar(ctx.getPlayer(), "仓库是空的，没什么可倾倒"); return InteractionResult.SUCCESS; }
        vaultDumpInto(vault, core);
        bar(ctx.getPlayer(), "叮！已入仓 " + types + " 类 · " + fmt(total) + " 件");
        return InteractionResult.SUCCESS;
    }

    /** 背包里把物品右键点到仓库上=整叠收纳（m82 终端镶嵌同款交互）。 */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack otherStack, net.minecraft.world.inventory.Slot slot,
                             net.minecraft.world.inventory.ClickAction clickType, Player player,
                             net.minecraft.world.entity.SlotAccess cursorStackReference) {
        if (otherStack.isEmpty()) {
            // m332 专属仓位里右键（空光标）=原地开仓——仓位在背包屏没有"手持右键"路径
            if (clickType == net.minecraft.world.inventory.ClickAction.SECONDARY && slot instanceof PortableVaultSlot) {
                if (!player.level().isClientSide) player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (syncId, inv, pl) -> new com.sdzjz.screen.PortableVaultScreenHandler(syncId, inv), stack.getHoverName()));
                return true; // 双端同判吃掉点击：客户端不预测取栈，等服务端开屏包（时序待实机验证）
            }
            return false;
        }
        if (clickType != net.minecraft.world.inventory.ClickAction.SECONDARY) return false;
        if (!absorbable(otherStack)) { bar(player, "带组件/不可堆叠物品不入仓（防抹组件）"); return true; }
        String id = BuiltInRegistries.ITEM.getKey(otherStack.getItem()).toString();
        if (!vaultAdd(stack, id, otherStack.getCount())) { bar(player, "仓库类型已满（config portableVaultTypeCap）"); return true; }
        bar(player, "叮！已收纳 " + otherStack.getHoverName().getString() + " ×" + fmt(otherStack.getCount()));
        otherStack.setCount(0);
        return true;
    }

    /** 吸附：每 10t 扫身边掉落物直接入账（尊重拾取延迟；一拍一次组件写=天然批量）。 */
    @Override
    public void inventoryTick(ItemStack stack, Level world, net.minecraft.world.entity.Entity entity, int slotIdx, boolean selected) {
        if (world.isClientSide || world.getGameTime() % 10 != 0) return;
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) return;
        magnetTick(stack, player);
    }

    /** m332 抽成静态：背包内走 inventoryTick、专属仓位走 Sdzjz 服务端 tick 钩
     *  （仓位不在 PlayerInventory，原版不给它 inventoryTick）。两路互斥零双跑。 */
    public static void magnetTick(ItemStack stack, net.minecraft.server.level.ServerPlayer player) {
        if (!magnetOn(stack)) return;
        Level world = player.level();
        int r = SdzjzConfig.get().portableVaultMagnetRadius;
        if (r <= 0) return;
        CompoundTag root = rootOf(stack);
        CompoundTag v = root.getCompound(K_VAULT);
        int cap = SdzjzConfig.get().portableVaultTypeCap;
        long got = 0; int kinds = 0;
        for (ItemEntity e : world.getEntitiesOfClass(ItemEntity.class,
                AABB.ofSize(player.position(), r * 2, r * 2, r * 2), en -> !en.hasPickUpDelay() && absorbable(en.getItem()))) {
            ItemStack s = e.getItem();
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            boolean known = v.contains(id);
            if (!known && cap > 0 && v.getAllKeys().size() >= cap) continue; // 满型跳过新类型，已有类型照吸
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip,
                              net.minecraft.world.item.TooltipFlag type) {
        CompoundTag v = rootOf(stack).getCompound(K_VAULT);
        tooltip.add(Component.literal(magnetOn(stack) ? "吸附: 开（潜行右键关闭）" : "吸附: 关（潜行右键开启）")
                .withStyle(net.minecraft.ChatFormatting.AQUA));
        tooltip.add(Component.literal("账本: " + v.getAllKeys().size() + " 类 · " + fmt(vaultTotal(stack)) + " 件")
                .withStyle(net.minecraft.ChatFormatting.GRAY)); // m312：撤明细行（30 类会刷屏，用户点名）——明细进 GUI
        tooltip.add(Component.literal("右键=打开仓库 · 右键核心/面板=整包入仓 · 背包屏副手上方有专属仓位").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    private static void bar(Player p, String s) {
        if (p != null) p.displayClientMessage(Component.literal(s), true);
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
