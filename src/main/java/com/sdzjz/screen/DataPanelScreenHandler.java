package com.sdzjz.screen;

import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** 数据面板 GUI：54 展示格（只可取出，取出即从逻辑仓储扣数）+ 玩家背包（可 shift 存入面板）。 */
public class DataPanelScreenHandler extends ScreenHandler {

    private final DataPanelBlockEntity panel;

    public DataPanelScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos));
    }

    public DataPanelScreenHandler(int syncId, PlayerInventory playerInv, DataPanelBlockEntity be) {
        super(ModScreenHandlers.DATA_PANEL, syncId);
        this.panel = be;
        Inventory display = (be != null) ? be.display : new SimpleInventory(DataPanelBlockEntity.PAGE);

        // 展示区 6×9（只取不放）
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = c + r * 9;
                this.addSlot(new Slot(display, idx, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean canInsert(ItemStack s) { return false; }
                    @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                        if (panel != null && !stack.isEmpty()) {
                            panel.withdraw(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount());
                        }
                        super.onTakeItem(player, stack);
                    }
                });
            }
        }
        // 玩家背包
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 140 + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, 8 + c * 18, 198));
    }

    private static DataPanelBlockEntity resolve(PlayerInventory playerInv, BlockPos pos) {
        BlockEntity be = playerInv.player.getWorld().getBlockEntity(pos);
        return be instanceof DataPanelBlockEntity p ? p : null;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return panel != null;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();

        if (index < DataPanelBlockEntity.PAGE) {
            // 展示格 → 玩家背包：整格取出并扣 store
            ItemStack taken = stack.copy();
            if (panel != null) panel.withdraw(Registries.ITEM.getId(taken.getItem()).toString(), taken.getCount());
            if (!this.insertItem(stack, DataPanelBlockEntity.PAGE, DataPanelBlockEntity.PAGE + 36, true)) {
                return ItemStack.EMPTY;
            }
            slot.setStack(ItemStack.EMPTY);
            return taken;
        } else {
            // 玩家背包 → 存入面板
            if (panel != null) {
                ItemStack copy = stack.copy();
                panel.deposit(copy);
                slot.setStack(ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }
}
