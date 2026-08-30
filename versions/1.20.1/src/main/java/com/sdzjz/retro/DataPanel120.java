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
import java.util.List;

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
            com.sdzjz.storage.PanelAggregator.deposit(cores, moving); // m500 下沉：收下即置 0（deposit 语义）；拒收=原样保留试下一核心
            slot.setChanged();
            return ItemStack.EMPTY;
        }
    }

    // ===== 可测逻辑（payload 接线在 RetroBootstrap，薄包一层）=====

    /** 聚合快照：**聚合/合并/排序/过滤/开窗全走共用件**（m500 真移植 B3：与主线
     *  {@code DataPanelBlockEntity.masterEntries} + {@code DataPanelScreenHandler.repage} 同一份代码），
     *  本方法只剩本世代协议侧的**展示栈物化**（虚拟列表包按行发，主线那侧是写 54 个真槽位）。
     *  返回 Rows（totalRows=匹配总行数=ceil(条目/9)，scrollRow 服务端钳位回传防客户端越界）。
     *
     *  <p>合一带来的三处行为变化（皆为本世代**补上主线原有的加固**，见 DEVLOG m500）：
     *  ①精确条目跨核心按「物品+tag」合并（原来每核心每模板各占一行，多核心网络下同款附魔书会分行）；
     *  ②排序尾键补齐（同量同 id 时普通在前、精确件按 tag 串稳定，防同款书刷新抖动）；
     *  ③搜索词命中集通道预留（本世代暂无本地化名索引，传空集即退化为纯 id 子串匹配，行为同旧）。 */
    static PanelPayloads120.Rows snapshot(List<StorageCore120> cores, String query, int scrollRow) {
        var page = com.sdzjz.storage.PanelAggregator.page(
                com.sdzjz.storage.PanelAggregator.entriesOf(cores),
                query, java.util.Set.of(), scrollRow, PanelPayloads120.Rows.WINDOW);
        List<PanelPayloads120.Row> rows = new ArrayList<>();
        for (com.sdzjz.storage.PanelAggregator.DispEnt d : page.rows()) {
            if (d.tpl != null) { // 精确件：模板已 copyWithCount(1)，原样发（不混堆不变裸）
                rows.add(new PanelPayloads120.Row(d.tpl, d.n, true));
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(d.id); // m443 同款防御
            if (rl == null) continue;
            Item it = BuiltInRegistries.ITEM.get(rl);
            ItemStack st = new ItemStack(it);
            if (!st.isEmpty()) rows.add(new PanelPayloads120.Row(st, d.n, false));
        }
        return new PanelPayloads120.Rows(page.totalRows(), page.scrollRow(), rows);
    }

    /** 取物进背包：amount 服务端钳位（1..maxStack×9=至多九栈一请求，防天量申报）；withdraw 后
     *  Inventory.add 塞不下的余量回账本绝不落地。返回实进背包件数（判官断言用）。 */
    static int serverTake(Player player, List<StorageCore120> cores, boolean exact, String id, ItemStack template, int amount) {
        ItemStack shape; // m459 修②：两路统一成 shape（exact 保 tag/plain 裸件），早验早退——原普通路
        if (exact) {     // 深处藏 BuiltInRegistries.ITEM.get(tryParse(id)) 无空守（今日不可达因 take>0 先要账本命中，潜伏雷拆除）
            shape = template;
            if (shape == null || shape.isEmpty()) return 0;
        } else {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return 0;
            shape = new ItemStack(BuiltInRegistries.ITEM.get(rl));
            if (shape.isEmpty()) return 0; // 未注册 id 落 air 即空
        }
        int cap = Math.max(1, shape.getMaxStackSize() * 9); // 一请求至多九栈（防天量申报）——本世代协议侧的申报策略，刻意留在本处
        int want = Math.max(1, Math.min(amount, cap));
        // m501（真移植 B3b）：取与回账下沉 PanelAggregator.takeInto（与主线 m82/m100 批量取出同一份代码）。
        // 合一带来两处本世代的加固：①按堆叠上限分块取（原为一次申报全量）②余量回账本后仍非空则 player.drop 双保险。
        return (int) com.sdzjz.storage.PanelAggregator.takeInto(player, cores, exact, id, shape, want);
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
