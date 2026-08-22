package com.sdzjz.retro;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * m456（C2-④a）：画布菜单——蓝本 StructureCoreScreenHandler（92 行）同构精简：零槽位纯开屏载体
 * （画布全部交互走 payload，蓝本同性），stillValid=方块在+可及距离（快照 C2S 前验共用）。
 */
final class StructureCoreMenu120 extends AbstractContainerMenu {

    final BlockPos corePos;

    StructureCoreMenu120(int syncId, BlockPos pos) {
        super(RetroBlocks.CANVAS_MENU, syncId);
        this.corePos = pos;
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int slotIndex) {
        return net.minecraft.world.item.ItemStack.EMPTY; // 零槽位（1.20.1 名 quickMoveStack，m447b 教训）
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(corePos) instanceof StructureCore120
                && player.distanceToSqr(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5) <= 64.0;
    }

    /** C2S 共同前验（m447 openMenuAt 同款口径）：菜单确实开在该核心。 */
    static StructureCoreMenu120 openAt(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        if (player.containerMenu instanceof StructureCoreMenu120 m && m.corePos.equals(pos) && m.stillValid(player)) return m;
        return null;
    }

    /** CanvasQuery 服务端处理：前验→整包快照回发（验不过静默丢，不可信来源不回声）。 */
    static void handleQuery(CanvasPayloads120.CanvasQuery packet, net.minecraft.server.level.ServerPlayer player) {
        if (openAt(player, packet.pos()) == null) return;
        if (!(player.level().getBlockEntity(packet.pos()) instanceof StructureCore120 core)) return;
        net.minecraft.nbt.CompoundTag render = new net.minecraft.nbt.CompoundTag();
        core.g.writeRenderNbt(render);
        Net120.toPlayer(player, new CanvasPayloads120.CanvasSnapshot(packet.pos(), render));
    }
}
