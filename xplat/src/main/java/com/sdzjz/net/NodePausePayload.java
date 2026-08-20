package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：切换任意节点的 暂停/运行（m110b 单节点启停）。 */
public record NodePausePayload(BlockPos pos, int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodePausePayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "node_pause"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodePausePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NodePausePayload::pos,
            ByteBufCodecs.INTEGER, NodePausePayload::index,
            NodePausePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
