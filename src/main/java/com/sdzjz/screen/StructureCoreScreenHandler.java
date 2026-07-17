package com.sdzjz.screen;

import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.registry.ModItems;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** 结构核心 GUI 逻辑：8 机器槽 + 3 升级槽 + 8 输出槽 + 玩家背包。 */
public class StructureCoreScreenHandler extends ScreenHandler {

    private final Inventory inv;
    private final StructureCoreBlockEntity core;

    // 客户端：由 ExtendedScreenHandlerType 用 (syncId, playerInv, pos) 构造
    public StructureCoreScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos));
    }

    // 服务端：由 BlockEntity.createMenu 构造
    public StructureCoreScreenHandler(int syncId, PlayerInventory playerInv, StructureCoreBlockEntity be) {
        super(ModScreenHandlers.STRUCTURE_CORE, syncId);
        this.core = be;
        this.inv = (be != null) ? be : new SimpleInventory(StructureCoreBlockEntity.SIZE);
        this.inv.onOpen(playerInv.player);

        // 机器槽 0..7（只收刷线机）
        for (int i = 0; i < StructureCoreBlockEntity.MACHINE_SLOTS; i++) {
            this.addSlot(new Slot(inv, StructureCoreBlockEntity.MACHINE_START + i, 8 + i * 18, 20) {
                @Override public boolean canInsert(ItemStack s) { return s.getItem() instanceof com.sdzjz.item.MachineItem; }
            });
        }
        // 升级槽 8..10（只收升级）
        for (int i = 0; i < StructureCoreBlockEntity.UPGRADE_SLOTS; i++) {
            this.addSlot(new Slot(inv, StructureCoreBlockEntity.UPGRADE_START + i, 8 + i * 18, 46) {
                @Override public boolean canInsert(ItemStack s) {
                    return s.isOf(ModItems.SPEED_UPGRADE) || s.isOf(ModItems.COUNT_UPGRADE) || s.isOf(ModItems.PARALLEL_UPGRADE);
                }
            });
        }
        // 输出槽 11..18（不可放入，只取出）
        for (int i = 0; i < StructureCoreBlockEntity.OUTPUT_SLOTS; i++) {
            this.addSlot(new Slot(inv, StructureCoreBlockEntity.OUTPUT_START + i, 8 + i * 18, 72) {
                @Override public boolean canInsert(ItemStack s) { return false; }
            });
        }

        // 玩家背包
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 104 + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, 8 + c * 18, 162));
    }

    private static StructureCoreBlockEntity resolve(PlayerInventory playerInv, BlockPos pos) {
        BlockEntity be = playerInv.player.getWorld().getBlockEntity(pos);
        return be instanceof StructureCoreBlockEntity s ? s : null;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (core == null) return false;
        // 0=开机 1=暂停/停止
        core.toggleRunning(id == 0);
        return true;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inv.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();
            int coreEnd = StructureCoreBlockEntity.SIZE;          // 0..18 = 核心库存
            int invEnd = coreEnd + 36;                            // 19..54 = 玩家背包
            if (index < coreEnd) {
                if (!this.insertItem(original, coreEnd, invEnd, true)) return ItemStack.EMPTY;
            } else {
                // 玩家 → 核心（只往机器槽/升级槽塞，靠 canInsert 过滤；输出槽拒收）
                if (!this.insertItem(original, 0, StructureCoreBlockEntity.OUTPUT_START, false)) return ItemStack.EMPTY;
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }
}
