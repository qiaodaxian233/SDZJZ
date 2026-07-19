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
    private final BlockPos blockPos;

    public DataPanelScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos));
    }

    public DataPanelScreenHandler(int syncId, PlayerInventory playerInv, DataPanelBlockEntity be) {
        super(ModScreenHandlers.DATA_PANEL, syncId);
        this.panel = be;
        this.blockPos = (be != null) ? be.getPos() : null;
        Inventory display = (be != null) ? be.display : new SimpleInventory(DataPanelBlockEntity.PAGE);

        // 展示区 6×9（只取不放）
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = c + r * 9;
                this.addSlot(new Slot(display, idx, 99 + c * 18, 30 + r * 18) {
                    @Override public boolean canInsert(ItemStack s) { return false; }
                    @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                        if (panel != null && !stack.isEmpty()) {
                            panel.withdraw(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount());
                        }
                        // 剥掉展示用的数量 NBT，否则取出的物品与普通同类物品无法堆叠
                        stack.remove(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                        super.onTakeItem(player, stack);
                    }
                });
            }
        }
        // 玩家背包
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, 99 + c * 18, 158 + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, 99 + c * 18, 216));
    }

    private static DataPanelBlockEntity resolve(PlayerInventory playerInv, BlockPos pos) {
        BlockEntity be = playerInv.player.getWorld().getBlockEntity(pos);
        return be instanceof DataPanelBlockEntity p ? p : null;
    }

    public BlockPos blockPos() { return blockPos; }

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
            // 展示格 → 玩家背包：先试塞（干净副本，无展示 NBT），按实际塞入量扣 store。
            // 顺序绝不能反：先扣后塞时背包满/塞一半 = 物品凭空消失。
            ItemStack clean = new ItemStack(stack.getItem(), stack.getCount());
            int before = clean.getCount();
            this.insertItem(clean, DataPanelBlockEntity.PAGE, DataPanelBlockEntity.PAGE + 36, true);
            int inserted = before - clean.getCount();
            if (inserted > 0 && panel != null) {
                panel.withdraw(Registries.ITEM.getId(stack.getItem()).toString(), inserted);
            }
            slot.setStack(ItemStack.EMPTY); // 展示格下个刷新周期重建
            return ItemStack.EMPTY;
        } else {
            // 玩家背包 → 存入面板
            if (panel != null) {
                // 带组件的物品（附魔/损耗/药水/成书等）拒存：仓储按 id 记账，存入会抹掉组件——宁可不动，绝不销毁数据
                if (!stack.getComponentChanges().isEmpty()) return ItemStack.EMPTY;
                ItemStack copy = stack.copy();
                panel.deposit(copy);
                // 只按实际存入量扣：无存储核心/类型满时余量留在原槽，绝不凭空消失
                if (copy.getCount() != stack.getCount()) {
                    slot.setStack(copy.isEmpty() ? ItemStack.EMPTY : copy);
                    slot.markDirty();
                }
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }
}
