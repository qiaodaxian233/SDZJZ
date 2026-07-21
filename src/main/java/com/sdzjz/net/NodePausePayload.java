package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：切换任意节点的 暂停/运行（m110b 单节点启停）。 */
public record NodePausePayload(BlockPos pos, int index) implements CustomPayload {

    public static final CustomPayload.Id<NodePausePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_pause"));

    public static final PacketCodec<RegistryByteBuf, NodePausePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodePausePayload::pos,
            PacketCodecs.INTEGER, NodePausePayload::index,
            NodePausePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
