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
    private final BlockPos blockPos;

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
        this.blockPos = (be != null) ? be.getPos() : null;
        this.inv.onOpen(playerInv.player);
        addProperties(props);
        // 画布界面：无槽位（机器=节点；机器/升级经右键方块放入；产出自动推送到连接的存储）
    }

    public BlockPos blockPos() { return blockPos; }

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
        return ItemStack.EMPTY; // 无玩家背包槽，禁用 shift 快速移动
    }
}
