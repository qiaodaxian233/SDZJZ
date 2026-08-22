package com.sdzjz.retro;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * m457（C2-④b）：节点四操作 C2S——对位蓝本 NodeAdd/Move/Remove/Link 四包的最小可玩集
 * （m455 稿归类）。全部定长字段零串零表（有界红线天然满足）；下标/坐标客户端只作意愿申报，
 * 服务端 openAt 前验+边界钳位后才执行（服务端权威，m447 口径）。
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
}
