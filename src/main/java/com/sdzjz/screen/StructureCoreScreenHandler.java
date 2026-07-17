package com.sdzjz.screen;

import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.item.MachineItem;
import com.sdzjz.registry.ModItems;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** 结构核心 GUI：8 机器槽(任意机器) + 3 升级槽 + 8 输出槽 + 玩家背包 + 状态同步。 */
public class StructureCoreScreenHandler extends ScreenHandler {

    private final Inventory inv;
    private final StructureCoreBlockEntity core;
    private final PropertyDelegate props;

    // 客户端
    public StructureCoreScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos), new ArrayPropertyDelegate(6));
    }

    // 服务端
    public StructureCoreScreenHandler(int syncId, PlayerInventory playerInv, StructureCoreBlockEntity be) {
        this(syncId, playerInv, be, be != null ? be.propertyDelegate : new ArrayPropertyDelegate(6));
    }

    private StructureCoreScreenHandler(int syncId, PlayerInventory playerInv, StructureCoreBlockEntity be, PropertyDelegate props) {
        super(ModScreenHandlers.STRUCTURE_CORE, syncId);
        this.core = be;
        this.inv = (be != null) ? be : new SimpleInventory(StructureCoreBlockEntity.SIZE);
        this.props = props;
        this.inv.onOpen(playerInv.player);
        addProperties(props);

        // 机器槽 0..7（2×4）
        for (int i = 0; i < StructureCoreBlockEntity.MACHINE_SLOTS; i++) {
            int x = 24 + (i % 4) * 20, y = 42 + (i / 4) * 20;
            this.addSlot(new Slot(inv, StructureCoreBlockEntity.MACHINE_START + i, x, y) {
                @Override public boolean canInsert(ItemStack s) { return s.getItem() instanceof MachineItem || s.getItem() instanceof com.sdzjz.item.CaptureCageItem; }
            });
        }
        // 升级槽 8..10
        for (int i = 0; i < StructureCoreBlockEntity.UPGRADE_SLOTS; i++) {
            this.addSlot(new Slot(inv, StructureCoreBlockEntity.UPGRADE_START + i, 24 + i * 20, 98) {
                @Override public boolean canInsert(ItemStack s) {
                    return s.isOf(ModItems.SPEED_UPGRADE) || s.isOf(ModItems.COUNT_UPGRADE) || s.isOf(ModItems.PARALLEL_UPGRADE);
                }
            });
        }
        // 输出槽 11..18（2×4，只取）
        for (int i = 0; i < StructureCoreBlockEntity.OUTPUT_SLOTS; i++) {
            int x = 190 + (i % 4) * 20, y = 42 + (i / 4) * 20;
            this.addSlot(new Slot(inv, StructureCoreBlockEntity.OUTPUT_START + i, x, y) {
                @Override public boolean canInsert(ItemStack s) { return false; }
            });
        }
        // 玩家背包（底部居中）
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, 99 + c * 18, 176 + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, 99 + c * 18, 232));
    }

    private static StructureCoreBlockEntity resolve(PlayerInventory playerInv, BlockPos pos) {
        BlockEntity be = playerInv.player.getWorld().getBlockEntity(pos);
        return be instanceof StructureCoreBlockEntity s ? s : null;
    }

    // 供客户端 Screen 读的状态
    public boolean isRunning()   { return props.get(0) != 0; }
    public int machineCount()    { return props.get(1); }
    public int tier()            { return props.get(2); }
    public int speedLv()         { return props.get(3); }
    public int countLv()         { return props.get(4); }
    public int parallelLv()      { return props.get(5); }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (core == null) return false;
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
            int coreEnd = StructureCoreBlockEntity.SIZE;
            int invEnd = coreEnd + 36;
            if (index < coreEnd) {
                if (!this.insertItem(original, coreEnd, invEnd, true)) return ItemStack.EMPTY;
            } else {
                if (!this.insertItem(original, 0, StructureCoreBlockEntity.OUTPUT_START, false)) return ItemStack.EMPTY;
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }
}
