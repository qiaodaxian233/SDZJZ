package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：连/断一条画布连线 from→to（已存在则断开）。 */
public record NodeLinkPayload(BlockPos pos, int from, int to) implements CustomPayload {

    public static final CustomPayload.Id<NodeLinkPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_link"));

    public static final PacketCodec<RegistryByteBuf, NodeLinkPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeLinkPayload::pos,
            PacketCodecs.INTEGER, NodeLinkPayload::from,
            PacketCodecs.INTEGER, NodeLinkPayload::to,
            NodeLinkPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
