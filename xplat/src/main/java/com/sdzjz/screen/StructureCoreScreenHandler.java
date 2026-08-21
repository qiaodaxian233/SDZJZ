package com.sdzjz.screen;

import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.item.MachineItem;
import com.sdzjz.registry.ModItems;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.BlockPos;

/** 结构核心 GUI：8 机器槽(任意机器) + 3 升级槽 + 8 输出槽 + 玩家背包 + 状态同步。 */
public class StructureCoreScreenHandler extends AbstractContainerMenu {

    private final Container inv;
    private final StructureCoreBlockEntity core;
    private final ContainerData props;
    private final BlockPos blockPos;

    // 客户端
    public StructureCoreScreenHandler(int syncId, Inventory playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos), new SimpleContainerData(10));
    }

    // 服务端
    public StructureCoreScreenHandler(int syncId, Inventory playerInv, StructureCoreBlockEntity be) {
        this(syncId, playerInv, be, be != null ? be.propertyDelegate : new SimpleContainerData(10));
    }

    private StructureCoreScreenHandler(int syncId, Inventory playerInv, StructureCoreBlockEntity be, ContainerData props) {
        super(ModScreenHandlers.STRUCTURE_CORE, syncId);
        this.core = be;
        this.inv = (be != null) ? be : new SimpleContainer(StructureCoreBlockEntity.SIZE);
        this.props = props;
        this.blockPos = (be != null) ? be.getBlockPos() : null;
        this.inv.startOpen(playerInv.player);
        addDataSlots(props);
        // 画布界面：无槽位（机器=节点；机器/升级经右键方块放入；产出自动推送到连接的存储）
        if (be != null && playerInv.player instanceof net.minecraft.server.level.ServerPlayer sp)
            be.addCanvasViewer(sp); // m344 开屏挂号（客户端构造 player 非 ServerPlayerEntity，天然不进）
    }

    @Override
    public void removed(Player player) { // m344 关屏销号（断线/换屏走原版关闭链同样到这）
        super.removed(player);
        if (core != null && player instanceof net.minecraft.server.level.ServerPlayer sp)
            core.removeCanvasViewer(sp);
    }

    public BlockPos blockPos() { return blockPos; }

    private static StructureCoreBlockEntity resolve(Inventory playerInv, BlockPos pos) {
        BlockEntity be = playerInv.player.level().getBlockEntity(pos);
        return be instanceof StructureCoreBlockEntity s ? s : null;
    }

    // 供客户端 Screen 读的状态
    public boolean isRunning()   { return props.get(0) != 0; }
    public int machineCount()    { return props.get(1); }
    public int tier()            { return props.get(2); }
    public int speedLv()         { return props.get(3); }
    public int countLv()         { return props.get(4); }
    public int parallelLv()      { return props.get(5); }
    /** 经验（低15位+高位拼回，规避属性 short 截断）。 */
    public long xp()             { return ((long) props.get(7) << 15) | (props.get(6) & 0x7FFFL); }
    public long buffered()       { return ((long) props.get(9) << 15) | (props.get(8) & 0x7FFFL); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (core == null) return false;
        if (id == 2) { core.collectXp(player); return true; }
        core.toggleRunning(id == 0);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return inv.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // 无玩家背包槽，禁用 shift 快速移动
    }
}
