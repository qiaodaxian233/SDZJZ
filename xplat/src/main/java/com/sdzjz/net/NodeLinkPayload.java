package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：连/断一条画布连线 from→to（已存在则断开）。 */
public record NodeLinkPayload(BlockPos pos, int from, int to) implements CustomPacketPayload {

    public static final CustomPacketPayload.Id<NodeLinkPayload> ID =
            new CustomPacketPayload.Id<>(ResourceLocation.of("sdzjz", "node_link"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeLinkPayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, NodeLinkPayload::pos,
            ByteBufCodecs.INTEGER, NodeLinkPayload::from,
            ByteBufCodecs.INTEGER, NodeLinkPayload::to,
            NodeLinkPayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
