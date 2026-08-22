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
        StructureCore120 core = coreFor(player, packet.pos());
        if (core != null) pushSnapshot(player, core, packet.pos());
    }

    private static StructureCore120 coreFor(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos) {
        if (openAt(player, pos) == null) return null;
        return player.level().getBlockEntity(pos) instanceof StructureCore120 core ? core : null;
    }

    private static void pushSnapshot(net.minecraft.server.level.ServerPlayer player, StructureCore120 core, net.minecraft.core.BlockPos pos) {
        net.minecraft.nbt.CompoundTag render = new net.minecraft.nbt.CompoundTag();
        core.g.writeRenderNbt(render);
        Net120.toPlayer(player, new CanvasPayloads120.CanvasSnapshot(pos, render));
    }

    // ===== m457（④b）：节点四操作——薄包处理器（前验→可测操作核→直推新快照即时反馈）=====
    private static final int COORD_LIMIT = 100_000; // 画布坐标钳位（渲染乘 zoom 的溢出安全边）

    static void handleAdd(NodePayloads120.NodeAdd packet, net.minecraft.server.level.ServerPlayer player) {
        StructureCore120 core = coreFor(player, packet.pos());
        if (core == null) return;
        addFromSlot(core, player.getInventory(), player.getAbilities().instabuild,
                packet.invSlot(), packet.xc(), packet.yc());
        pushSnapshot(player, core, packet.pos());
    }

    static void handleMove(NodePayloads120.NodeMove packet, net.minecraft.server.level.ServerPlayer player) {
        StructureCore120 core = coreFor(player, packet.pos());
        if (core == null) return;
        moveNode(core, packet.index(), packet.xc(), packet.yc());
        pushSnapshot(player, core, packet.pos());
    }

    static void handleRemove(NodePayloads120.NodeRemove packet, net.minecraft.server.level.ServerPlayer player) {
        StructureCore120 core = coreFor(player, packet.pos());
        if (core == null) return;
        removeToInventory(core, player.getInventory(), packet.index());
        pushSnapshot(player, core, packet.pos());
    }

    static void handleLink(NodePayloads120.NodeLink packet, net.minecraft.server.level.ServerPlayer player) {
        StructureCore120 core = coreFor(player, packet.pos());
        if (core == null) return;
        if (packet.cut()) core.disconnect(packet.from(), packet.to());
        else core.connect(packet.from(), packet.to());
        pushSnapshot(player, core, packet.pos());
    }

    /** 放置操作核（可测）：槽内须是机器物品；节点数硬顶同快照口径；坐标钳位；生存扣 1 创造不扣。
     *  返回是否成立。 */
    static boolean addFromSlot(StructureCore120 core, net.minecraft.world.entity.player.Inventory inv,
                               boolean creative, int slot, int xc, int yc) {
        if (slot < 0 || slot >= inv.getContainerSize()) return false;
        if (core.g.machineNodes.size() >= CanvasPayloads120.MAX_NODES) return false; // 服务端同顶（m455 红线两端都设）
        net.minecraft.world.item.ItemStack held = inv.getItem(slot);
        if (held.isEmpty() || !(held.getItem() instanceof RetroMachineItems.RetroMachineItem)) return false;
        net.minecraft.world.item.ItemStack one = held.copyWithCount(1);
        one.getOrCreateTag().putInt("xc", clampCoord(xc)); // 键同源 NodeTags 谱系
        one.getOrCreateTag().putInt("yc", clampCoord(yc));
        core.addNode(one);
        if (!creative) { held.shrink(1); inv.setChanged(); }
        return true;
    }

    /** 拖动操作核（可测）：越界忽略；坐标钳位写回栈 NBT。 */
    static boolean moveNode(StructureCore120 core, int index, int xc, int yc) {
        if (index < 0 || index >= core.g.machineNodes.size()) return false;
        net.minecraft.world.item.ItemStack s = core.g.machineNodes.get(index);
        s.getOrCreateTag().putInt("xc", clampCoord(xc));
        s.getOrCreateTag().putInt("yc", clampCoord(yc));
        core.setChanged();
        return true;
    }

    /** 摘回操作核（可测）：detachNode（m454 簿记）→洗净（蓝本 returnNodeClean 谱系：剥画布键，
     *  空 tag 即变裸与新件混堆）→回背包（塞不下走原版 placeItemBackInInventory 落玩家脚下，
     *  画布域无仓可回帐——与存储域"绝不落地"域界不同，记档）。 */
    static boolean removeToInventory(StructureCore120 core, net.minecraft.world.entity.player.Inventory inv, int index) {
        net.minecraft.world.item.ItemStack s = core.detachNode(index);
        if (s.isEmpty()) return false;
        inv.placeItemBackInInventory(cleanNode(s));
        return true;
    }

    /** 洗净节点栈（可测纯函数）：剥 xc/yc/gp 画布键；剥空即 setTag(null) 变裸（m128 语义）。 */
    static net.minecraft.world.item.ItemStack cleanNode(net.minecraft.world.item.ItemStack s) {
        net.minecraft.world.item.ItemStack out = s.copy();
        if (out.hasTag()) {
            out.getTag().remove("xc");
            out.getTag().remove("yc");
            out.getTag().remove("gp");
            if (out.getTag().isEmpty()) out.setTag(null);
        }
        return out;
    }

    private static int clampCoord(int v) {
        return Math.max(-COORD_LIMIT, Math.min(COORD_LIMIT, v));
    }
}
