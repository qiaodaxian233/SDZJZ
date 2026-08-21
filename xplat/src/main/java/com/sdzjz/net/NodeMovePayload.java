package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：把某个机器节点在画布上的新位置发给服务端保存。 */
public record NodeMovePayload(BlockPos pos, int index, int nx, int ny) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodeMovePayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "node_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeMovePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NodeMovePayload::pos,
            ByteBufCodecs.INT, NodeMovePayload::index,
            ByteBufCodecs.INT, NodeMovePayload::nx,
            ByteBufCodecs.INT, NodeMovePayload::ny,
            NodeMovePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
