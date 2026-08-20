package com.sdzjz.screen;

import com.sdzjz.block.TradeCenterBlockEntity;
import com.sdzjz.registry.ModItems;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.BlockPos;

/**
 * 村民交易所界面。按钮协议（onButtonClick id）：
 *  0..6  = 就业为职业 #id（无职业合同时）
 *  10..N = 执行交易 #(id-10)
 *  40    = 治愈（折扣+1，消耗金苹果）
 */
public class TradeCenterScreenHandler extends AbstractContainerMenu {

    public static final int BTN_TRADE_BASE = 10;
    public static final int BTN_HEAL = 40;

    private final TradeCenterBlockEntity be;
    private final BlockPos blockPos;

    public TradeCenterScreenHandler(int syncId, Container playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos));
    }

    public TradeCenterScreenHandler(int syncId, Container playerInv, TradeCenterBlockEntity be) {
        super(ModScreenHandlers.TRADE_CENTER, syncId);
        this.be = be;
        this.blockPos = (be != null) ? be.getPos() : null;
        Container contract = (be != null) ? be.contractSlot : new SimpleContainer(1);

        // 合同槽（只收村民合同）
        this.addSlot(new Slot(contract, 0, 30, 40) {
            @Override public boolean canInsert(ItemStack s) { return s.isOf(ModItems.VILLAGER_CONTRACT); }
            @Override public int getMaxItemCount() { return 1; }
        });

        // 玩家背包
        int px = 99, py = 170;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, px + c * 18, py + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, px + c * 18, py + 58));
    }

    private static TradeCenterBlockEntity resolve(Container playerInv, BlockPos pos) {
        BlockEntity b = playerInv.player.getWorld().getBlockEntity(pos);
        return b instanceof TradeCenterBlockEntity t ? t : null;
    }

    public BlockPos blockPos() { return blockPos; }

    public ItemStack contract() {
        return this.slots.get(0).getStack();
    }

    @Override
    public boolean onButtonClick(Player player, int id) {
        if (be == null || player.getWorld().isClient) return false;
        if (id >= 0 && id <= 6) { be.employ(player, id); return true; }
        if (id >= BTN_TRADE_BASE && id < BTN_HEAL) { be.trade(player, id - BTN_TRADE_BASE); return true; }
        if (id == BTN_HEAL) { be.heal(player); return true; }
        return false;
    }

    @Override
    public boolean canUse(Player player) {
        return be != null;
    }

    @Override
    public ItemStack quickMove(Player player, int index) {
        ItemStack ret = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack st = slot.getStack();
            ret = st.copy();
            if (index == 0) {
                if (!this.insertItem(st, 1, 1 + 36, true)) return ItemStack.EMPTY;
            } else {
                if (!st.isOf(ModItems.VILLAGER_CONTRACT)) return ItemStack.EMPTY;
                if (!this.insertItem(st, 0, 1, false)) return ItemStack.EMPTY;
            }
            if (st.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        }
        return ret;
    }
}
