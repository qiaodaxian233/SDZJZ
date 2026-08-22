package com.sdzjz.retro;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * m447（P-C1 刀②）：数据面板 1.20.1 服务端半——BE（开屏工厂）+菜单（虚拟列表制，无网络槽位）。
 * 精简协议定性见 PanelPayloads120 类注；本文件三块可测逻辑全拆纯/半纯方法（GameTest 免真客户端）：
 * snapshot（聚合+过滤+排序+开窗）、serverTake（取物进背包+余量回账）、quickMove（背包→入仓）。
 *
 * <p>红线随迁：quickMove/onSlotClick 双端执行——触库逻辑一律服务端权威（客户端支路直接 EMPTY 空转，
 * 原版菜单同步机制兜底纠偏）；C2S 先验"菜单确实开在该面板且人在方块可及距离"再执行；取不完/
 * 塞不下余量回账本绝不落地（m130/m444 同口径）。
 */
final class DataPanel120 extends BlockEntity implements ExtendedScreenHandlerFactory {

    DataPanel120(BlockPos pos, BlockState state) {
        super(RetroBlocks.DATA_PANEL_BE, pos, state);
    }

    // ===== 开屏工厂（0.92 ExtendedScreenHandlerFactory 三方法，原文核名：writeScreenOpeningData）=====
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.sdzjz.data_panel"); // 键与 Legacy 同名同源
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new PanelMenu120(syncId, inv, worldPosition);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
    }

    /** 菜单：玩家背包 36 槽（坐标随 m448 屏定稿，先用原版惯例排布）+ 面板锚点。 */
    static final class PanelMenu120 extends AbstractContainerMenu {

        final BlockPos panelPos;

        PanelMenu120(int syncId, Inventory playerInv, BlockPos pos) {
            super(RetroBlocks.PANEL_MENU, syncId);
            this.panelPos = pos;
            for (int row = 0; row < 3; row++) // 背包 27（原版坐标惯例：8 + 列×18 / 84 + 行×18，屏 m448 对表）
                for (int col = 0; col < 9; col++)
                    addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            for (int col = 0; col < 9; col++) // 快捷栏 9
                addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }

        @Override
        public boolean stillValid(Player player) {
            return player.level().getBlockEntity(panelPos) instanceof DataPanel120
                    && player.distanceToSqr(panelPos.getX() + 0.5, panelPos.getY() + 0.5, panelPos.getZ() + 0.5) <= 64.0;
        }

        /** shift 点背包槽=整栈入仓（1.20.1 mojmap 名 quickMoveStack——quickMove 是 1.20.5 起改名，m447b CI 抓获）。返回 EMPTY 终止
         *  原版续移循环——虚拟列表制没有"下一个槽"可续。客户端支路空转（触库红线，类注）。 */
        @Override
        public ItemStack quickMoveStack(Player player, int slotIndex) {
            if (player.level().isClientSide) return ItemStack.EMPTY;
            Slot slot = slots.get(slotIndex);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack moving = slot.getItem();
            List<StorageCore120> cores = StorageCore120.connectedCores(player.level(), panelPos);
            for (StorageCore120 core : cores) {
                if (moving.isEmpty()) break;
                core.deposit(moving); // 收下即置 0（deposit 语义）；拒收=原样保留试下一核心
            }
            slot.setChanged();
            return ItemStack.EMPTY;
        }
    }

    // ===== 可测逻辑（payload 接线在 RetroBootstrap，薄包一层）=====

    /** 聚合快照：全核心 普通账本(id 求和)+精确账本(模板逐条) → id 串过滤（大小写不敏；精确件按
     *  物品 id 匹配）→ 排序（账面数降序，同数按 id 升序稳定）→ 按 scrollRow 开 9 列窗。
     *  返回 Rows（totalRows=匹配总行数=ceil(条目/9)，scrollRow 服务端钳位回传防客户端越界）。 */
    static PanelPayloads120.Rows snapshot(List<StorageCore120> cores, String query, int scrollRow) {
        String q = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        Map<String, Long> plain = new LinkedHashMap<>();
        List<PanelPayloads120.Row> all = new ArrayList<>();
        for (StorageCore120 core : cores) {
            for (Map.Entry<String, Long> e : core.storeView().entrySet())
                plain.merge(e.getKey(), e.getValue(), StorageCore120::satAdd); // m273 跨核心求和同饱和
            for (int i = 0; i < core.exactTemplates().size(); i++) {
                ItemStack tpl = core.exactTemplates().get(i);
                String id = BuiltInRegistries.ITEM.getKey(tpl.getItem()).toString();
                if (!q.isEmpty() && !id.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
                all.add(new PanelPayloads120.Row(tpl.copyWithCount(1), core.exactCount(i), true));
            }
        }
        for (Map.Entry<String, Long> e : plain.entrySet()) {
            if (!q.isEmpty() && !e.getKey().toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey()); // m443 同款防御
            if (rl == null) continue;
            Item it = BuiltInRegistries.ITEM.get(rl);
            ItemStack st = new ItemStack(it);
            if (!st.isEmpty()) all.add(new PanelPayloads120.Row(st, e.getValue(), false));
        }
        all.sort(java.util.Comparator
                .comparingLong((PanelPayloads120.Row r) -> -r.n())
                .thenComparing(r -> BuiltInRegistries.ITEM.getKey(r.display().getItem()).toString()));
        int totalRows = (all.size() + 8) / 9;
        int maxStart = Math.max(0, totalRows - PanelPayloads120.Rows.WINDOW / 9);
        int row = Math.max(0, Math.min(scrollRow, maxStart)); // 服务端钳位（客户端数字不可信）
        int from = row * 9;
        int to = Math.min(all.size(), from + PanelPayloads120.Rows.WINDOW);
        return new PanelPayloads120.Rows(totalRows, row, new ArrayList<>(all.subList(Math.min(from, all.size()), to)));
    }

    /** 取物进背包：amount 服务端钳位（1..maxStack×9=至多九栈一请求，防天量申报）；withdraw 后
     *  Inventory.add 塞不下的余量回账本绝不落地。返回实进背包件数（判官断言用）。 */
    static int serverTake(Player player, List<StorageCore120> cores, boolean exact, String id, ItemStack template, int amount) {
        ItemStack shape = exact ? template : null;
        if (exact && (shape == null || shape.isEmpty())) return 0;
        int cap = (exact ? shape.getMaxStackSize() : new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.tryParse(id) == null ? new ResourceLocation("minecraft", "air") : ResourceLocation.tryParse(id))).getMaxStackSize()) * 9;
        int want = Math.max(1, Math.min(amount, Math.max(1, cap)));
        int moved = 0;
        for (StorageCore120 core : cores) {
            while (moved < want) {
                int take = exact ? core.withdrawExact(shape, want - moved) : core.withdraw(id, want - moved);
                if (take <= 0) break;
                ItemStack out = exact ? shape.copyWithCount(take) : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id)), take);
                player.getInventory().add(out);
                int leftover = out.getCount(); // add 后余量留在 out 里（原版 Inventory.add 语义）
                if (leftover > 0) { // 背包满：余量回账本绝不落地，本次请求收工
                    ItemStack back = exact ? shape.copyWithCount(leftover) : new ItemStack(out.getItem(), leftover);
                    if (exact) core.depositExact(back); else core.deposit(back);
                    return moved + (take - leftover);
                }
                moved += take;
            }
            if (moved >= want) break;
        }
        return moved;
    }

    /** C2S 共同前验（服务端权威第一道）：菜单确实开在该面板且方块仍在。 */
    static PanelMenu120 openMenuAt(ServerPlayer player, BlockPos pos) {
        if (player.containerMenu instanceof PanelMenu120 menu && menu.panelPos.equals(pos)
                && menu.stillValid(player)) return menu;
        return null;
    }

    /** Query 包服务端处理：前验→聚合→按窗回发。 */
    static void handleQuery(PanelPayloads120.Query packet, ServerPlayer player) {
        if (openMenuAt(player, packet.pos()) == null) return; // 验不过静默丢（不可信来源不回声）
        List<StorageCore120> cores = StorageCore120.connectedCores(player.level(), packet.pos());
        Net120.toPlayer(player, snapshot(cores, packet.query(), packet.scrollRow()));
    }

    /** Take 包服务端处理：前验→取物→回发一帧新窗（客户端账面立即刷新）。 */
    static void handleTake(PanelPayloads120.Take packet, ServerPlayer player) {
        if (openMenuAt(player, packet.pos()) == null) return;
        List<StorageCore120> cores = StorageCore120.connectedCores(player.level(), packet.pos());
        serverTake(player, cores, packet.exact(), packet.id(), packet.template(), packet.amount());
        Net120.toPlayer(player, snapshot(cores, "", 0));
    }
}
