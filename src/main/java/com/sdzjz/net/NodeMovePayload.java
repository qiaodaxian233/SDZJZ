package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：把某个机器节点在画布上的新位置发给服务端保存。 */
public record NodeMovePayload(BlockPos pos, int index, int nx, int ny) implements CustomPayload {

    public static final CustomPayload.Id<NodeMovePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_move"));

    public static final PacketCodec<RegistryByteBuf, NodeMovePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeMovePayload::pos,
            PacketCodecs.INTEGER, NodeMovePayload::index,
            PacketCodecs.INTEGER, NodeMovePayload::nx,
            PacketCodecs.INTEGER, NodeMovePayload::ny,
            NodeMovePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
