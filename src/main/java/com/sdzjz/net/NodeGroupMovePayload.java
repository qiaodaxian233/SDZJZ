package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 客户端→服务端（m191 画布打组）：组框拖动松手后，把整组成员的位移增量一次发给服务端。
 * 不复用 NodeMovePayload 逐成员连发——那是每包一次全量 BE 同步，大组松手=一 tick N 次
 * 全量重发瞬卡（m128F3 同款病灶）；本包服务端批量改完只 sync 一次。
 */
public record NodeGroupMovePayload(BlockPos pos, int gid, int dx, int dy) implements CustomPayload {

    public static final CustomPayload.Id<NodeGroupMovePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_group_move"));

    public static final PacketCodec<RegistryByteBuf, NodeGroupMovePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeGroupMovePayload::pos,
            PacketCodecs.INTEGER, NodeGroupMovePayload::gid,
            PacketCodecs.INTEGER, NodeGroupMovePayload::dx,
            PacketCodecs.INTEGER, NodeGroupMovePayload::dy,
            NodeGroupMovePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
