package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：机器节点融合升阶/拆解降阶（m123：4台同阶→1台高阶，up=false 反向）。 */
public record NodeFusePayload(BlockPos pos, int index, boolean up) implements CustomPacketPayload {

    public static final CustomPacketPayload.Id<NodeFusePayload> ID =
            new CustomPacketPayload.Id<>(ResourceLocation.of("sdzjz", "node_fuse"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeFusePayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, NodeFusePayload::pos,
            ByteBufCodecs.INTEGER, NodeFusePayload::index,
            ByteBufCodecs.BOOL, NodeFusePayload::up,
            NodeFusePayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
