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
        if (core == null) return;
        refreshEndpoints(core, player.level(), false); // m458：快照前刷端点（40t 缓存内直返）
        pushSnapshot(player, core, packet.pos());
    }

    private static StructureCore120 coreFor(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos) {
        if (openAt(player, pos) == null) return null;
        return player.level().getBlockEntity(pos) instanceof StructureCore120 core ? core : null;
    }

    private static void pushSnapshot(net.minecraft.server.level.ServerPlayer player, StructureCore120 core, net.minecraft.core.BlockPos pos) {
        net.minecraft.nbt.CompoundTag render = new net.minecraft.nbt.CompoundTag();
        core.g.writeRenderNbt(render, null); // m477 共用图状态：lookup 句柄本世代恒 null（RetroStackCodec 忽略）
        Net120.toPlayer(player, new CanvasPayloads120.CanvasSnapshot(pos, render));
    }

    // ===== m457（④b）：节点四操作——薄包处理器（前验→可测操作核→直推新快照即时反馈）=====
    private static final int COORD_LIMIT = 100_000; // 画布坐标钳位（渲染乘 zoom 的溢出安全边）

    static void handleAdd(NodePayloads120.NodeAdd packet, net.minecraft.server.level.ServerPlayer player) {
        StructureCore120 core = coreFor(player, packet.pos());
        if (core == null) return;
        addFromSlot(core, player.getInventory(), player.getAbilities().instabuild,
                packet.invSlot(), packet.xc(), packet.yc());
        player.inventoryMenu.broadcastChanges(); // m459 修①：本菜单零槽位，服务端扣背包不广播=客户端失同步（侧栏数量不刷）
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
        player.inventoryMenu.broadcastChanges(); // m459 修①：摘回件不广播=开屏期客户端看不见
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
        one.getOrCreateTag().putInt("nx", clampCoord(xc)); // m474 键位归位：nx/ny 同蓝本（原 xc 撞 NodeTags m159 抽取累计）
        one.getOrCreateTag().putInt("ny", clampCoord(yc));
        core.addNode(one);
        if (!creative) { held.shrink(1); inv.setChanged(); }
        return true;
    }

    /** 拖动操作核（可测）：越界忽略；坐标钳位写回栈 NBT。 */
    static boolean moveNode(StructureCore120 core, int index, int xc, int yc) {
        if (index < 0 || index >= core.g.machineNodes.size()) return false;
        net.minecraft.world.item.ItemStack s = core.g.machineNodes.get(index);
        s.getOrCreateTag().putInt("nx", clampCoord(xc)); // m474 键位归位（同上）
        s.getOrCreateTag().putInt("ny", clampCoord(yc));
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

    /** 洗净节点栈（可测纯函数）：剥 nx/ny/gp 画布键；剥空即 setTag(null) 变裸（m128 语义）。
     *  m474 并剥旧键 xc/yc（史前存档摘下来的节点也要洗干净，否则带着污染键回背包）。 */
    static net.minecraft.world.item.ItemStack cleanNode(net.minecraft.world.item.ItemStack s) {
        net.minecraft.world.item.ItemStack out = s.copy();
        if (out.hasTag()) {
            out.getTag().remove("nx");
            out.getTag().remove("ny");
            out.getTag().remove("gp");
            out.getTag().remove("xc"); // m474 旧键残留一并剥（史前存档；xc 撞 NodeTags 抽取累计）
            out.getTag().remove("yc");
            if (out.getTag().isEmpty()) out.setTag(null);
        }
        return out;
    }

    private static int clampCoord(int v) {
        return Math.max(-COORD_LIMIT, Math.min(COORD_LIMIT, v));
    }

    // ===== m458（④c）：机器↔存储连线 =====

    /** 端点扫描（可测，force=真忽略缓存）：BFS 可达存储核心=端点（kind=0，蓝本口径首位）；
     *  新端点自动停靠（二元 {x,y}，m265 键口径：二元停靠/三元画布放置）；消失端点连坐清理
     *  ——storEdges 触删、停靠位同清（拆核心=断线，与 m454 摘节点簿记同精神）。 */
    static void refreshEndpoints(StructureCore120 core, net.minecraft.world.level.Level world, boolean force) {
        if (!force && core.endpointScanTick != Long.MIN_VALUE
                && world.getGameTime() - core.endpointScanTick < 40) return;
        core.endpointScanTick = world.getGameTime();
        String dim = world.dimension().location().toString();
        java.util.LinkedHashSet<Long> alive = new java.util.LinkedHashSet<>();
        for (StorageCore120 sc : StorageCore120.connectedCores(world, core.getBlockPos()))
            alive.add(sc.getBlockPos().asLong());
        core.g.storageEndpoints.clear();
        core.g.storageEndpointDims.clear();
        int dockRow = 0;
        for (long p : alive) {
            core.g.storageEndpoints.add(new long[]{p, 0});
            core.g.storageEndpointDims.add(dim);
            if (!core.g.storageNodePos.containsKey(p))
                core.g.storageNodePos.put(p, new int[]{-70, 20 + dockRow * 40}); // 自动停靠列
            dockRow++;
        }
        core.g.storageNodePos.keySet().retainAll(alive); // 消失端点停靠位同清
        for (int i = core.g.storageEdges.size() - 1; i >= 0; i--) {
            if (!alive.contains(core.g.storageEdges.get(i)[1])) {
                core.g.storageEdges.remove(i);
                core.g.storageEdgeDims.remove(i);
            }
        }
        core.setChanged();
    }

    /** 连线循环（可测）：无→产出(0)→供料(1)→断；返回新态（-1=断/0/1，-2=拒）。机器越界或
     *  端点不在场=拒（存在性校验先于执行，服务端权威）。 */
    static int storageLinkCycle(StructureCore120 core, int machine, long endpoint, String dim) {
        if (machine < 0 || machine >= core.g.machineNodes.size()) return -2;
        boolean known = false;
        for (long[] e : core.g.storageEndpoints) if (e[0] == endpoint) { known = true; break; }
        if (!known) return -2;
        for (int i = 0; i < core.g.storageEdges.size(); i++) {
            long[] e = core.g.storageEdges.get(i);
            if (e[0] == machine && e[1] == endpoint) {
                if (e[2] == 0) { e[2] = 1; core.setChanged(); return 1; } // 产出→供料
                core.g.storageEdges.remove(i); // 供料→断
                core.g.storageEdgeDims.remove(i);
                core.setChanged();
                return -1;
            }
        }
        core.g.storageEdges.add(new long[]{machine, endpoint, 0}); // 无→产出
        core.g.storageEdgeDims.add(dim);
        core.setChanged();
        return 0;
    }

    static void handleStorageLink(StoragePayloads120.StorageLink packet, net.minecraft.server.level.ServerPlayer player) {
        StructureCore120 core = coreFor(player, packet.pos());
        if (core == null) return;
        refreshEndpoints(core, player.level(), false);
        storageLinkCycle(core, packet.machine(), packet.endpoint(),
                player.level().dimension().location().toString());
        pushSnapshot(player, core, packet.pos());
    }

    static void handleStorageNodeMove(StoragePayloads120.StorageNodeMove packet, net.minecraft.server.level.ServerPlayer player) {
        StructureCore120 core = coreFor(player, packet.pos());
        if (core == null) return;
        if (core.g.storageNodePos.containsKey(packet.endpoint())) { // 三元=画布放置（m265 口径）
            core.g.storageNodePos.put(packet.endpoint(),
                    new int[]{clampCoord(packet.x()), clampCoord(packet.y()), 1});
            core.setChanged();
        }
        pushSnapshot(player, core, packet.pos());
    }
}
