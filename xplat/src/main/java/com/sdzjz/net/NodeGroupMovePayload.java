package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/**
 * 客户端→服务端（m191 画布打组）：组框拖动松手后，把整组成员的位移增量一次发给服务端。
 * 不复用 NodeMovePayload 逐成员连发——那是每包一次全量 BE 同步，大组松手=一 tick N 次
 * 全量重发瞬卡（m128F3 同款病灶）；本包服务端批量改完只 sync 一次。
 */
public record NodeGroupMovePayload(BlockPos pos, int gid, int dx, int dy) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodeGroupMovePayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "node_group_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeGroupMovePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NodeGroupMovePayload::pos,
            ByteBufCodecs.INTEGER, NodeGroupMovePayload::gid,
            ByteBufCodecs.INTEGER, NodeGroupMovePayload::dx,
            ByteBufCodecs.INTEGER, NodeGroupMovePayload::dy,
            NodeGroupMovePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
