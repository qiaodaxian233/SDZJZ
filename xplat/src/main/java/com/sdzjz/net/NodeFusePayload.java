package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：机器节点融合升阶/拆解降阶（m123：4台同阶→1台高阶，up=false 反向）。 */
public record NodeFusePayload(BlockPos pos, int index, boolean up) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodeFusePayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "node_fuse"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeFusePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NodeFusePayload::pos,
            ByteBufCodecs.INT, NodeFusePayload::index,
            ByteBufCodecs.BOOL, NodeFusePayload::up,
            NodeFusePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
