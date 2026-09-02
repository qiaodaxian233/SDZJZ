package com.sdzjz.retro;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * m457（C2-④b）：节点四操作 C2S——对位蓝本 NodeAdd/Move/Remove/Link 四包的最小可玩集
 * （m455 稿归类）。全部定长字段零串零表（有界红线天然满足）；下标/坐标客户端只作意愿申报，
 * 服务端 openAt 前验+边界钳位后才执行（服务端权威，m447 口径）。
 *
 * <p>m506（真移植 A5a）：追加画布分组两包 NodeGroup/NodeGroupMove（含串与表，有界红线走 Net120 两口）。
 */
final class NodePayloads120 {

    private NodePayloads120() { }

    /** 放置：从玩家背包 invSlot 取机器物品挂上画布 (xc,yc)。 */
    record NodeAdd(BlockPos pos, int invSlot, int xc, int yc) implements FabricPacket {
        static final PacketType<NodeAdd> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "node_add"), NodeAdd::new);

        NodeAdd(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()); }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(invSlot);
            buf.writeVarInt(xc);
            buf.writeVarInt(yc);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 拖动：节点 index 移到 (xc,yc)。 */
    record NodeMove(BlockPos pos, int index, int xc, int yc) implements FabricPacket {
        static final PacketType<NodeMove> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "node_move"), NodeMove::new);

        NodeMove(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()); }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(index);
            buf.writeVarInt(xc);
            buf.writeVarInt(yc);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 摘回：节点 index 洗净回背包。 */
    record NodeRemove(BlockPos pos, int index) implements FabricPacket {
        static final PacketType<NodeRemove> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "node_remove"), NodeRemove::new);

        NodeRemove(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readVarInt()); }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(index);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 连/断：cut=false 连 from→to，true 断该定向对。 */
    record NodeLink(BlockPos pos, int from, int to, boolean cut) implements FabricPacket {
        static final PacketType<NodeLink> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "node_link"), NodeLink::new);

        NodeLink(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()); }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(from);
            buf.writeVarInt(to);
            buf.writeBoolean(cut);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    // ===== m506（真移植 A5a）：画布分组两包——对位主线 xplat/net NodeGroupPayload / NodeGroupMovePayload
    // （m191），语义注释原文照搬；编解码走本世代 FabricPacket 样板，有界红线走 Net120 两口（m291 对位）。=====

    /** 组名协议上限（主线 Bounded.string(64)：UI setMaxLength 24，协议留余量）。 */
    static final int GROUP_NAME_MAX = 64;
    /** 成员表协议硬顶（主线 Bounded.intList(4096)：玩法上限 maxNodes 可调，协议顶只防分配放大）。 */
    static final int GROUP_MEMBERS_MAX = 4096;

    /**
     * 客户端→服务端（m191 画布打组）：一包三义，按字段组合分派——
     * gid=-1 且 members 非空 = 建组（name 可空，服务端补"组N"）；
     * gid>=0 且 name 非空     = 重命名；
     * gid>=0 且 name 空       = 解散。
     * 语义在服务端接收器判定，客户端只按上述组合发；members 解码期有界（先验声明条数再分配）。
     */
    record NodeGroup(BlockPos pos, int gid, String name, List<Integer> members) implements FabricPacket {
        static final PacketType<NodeGroup> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "node_group"), NodeGroup::new);

        NodeGroup(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), buf.readVarInt(), Net120.readBoundedUtf(buf, GROUP_NAME_MAX), readMembers(buf)); // m291 锚点口
        }

        private static List<Integer> readMembers(FriendlyByteBuf buf) {
            int n = Net120.readBoundedCount(buf, GROUP_MEMBERS_MAX); // 分配前验（m291 有界解码红线）
            List<Integer> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(buf.readVarInt());
            return out;
        }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(gid);
            buf.writeUtf(name, GROUP_NAME_MAX);
            buf.writeVarInt(Math.min(members.size(), GROUP_MEMBERS_MAX));
            int wrote = 0;
            for (int m : members) {
                if (wrote++ >= GROUP_MEMBERS_MAX) break; // 写侧同顶：声明数与实写数恒一致（m106 哨兵成对审精神）
                buf.writeVarInt(m);
            }
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    /**
     * 客户端→服务端（m191 画布打组）：组框拖动松手后，把整组成员的位移增量一次发给服务端。
     * 不复用 NodeMove 逐成员连发——那是每包一次全量 BE 同步，大组松手=一 tick N 次
     * 全量重发瞬卡（m128F3 同款病灶）；本包服务端批量改完只 setChanged 一次。
     */
    record NodeGroupMove(BlockPos pos, int gid, int dx, int dy) implements FabricPacket {
        static final PacketType<NodeGroupMove> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "node_group_move"), NodeGroupMove::new);

        NodeGroupMove(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()); }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(gid);
            buf.writeVarInt(dx);
            buf.writeVarInt(dy);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }
}
