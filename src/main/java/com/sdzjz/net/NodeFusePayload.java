package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：机器节点融合升阶/拆解降阶（m123：4台同阶→1台高阶，up=false 反向）。 */
public record NodeFusePayload(BlockPos pos, int index, boolean up) implements CustomPayload {

    public static final CustomPayload.Id<NodeFusePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_fuse"));

    public static final PacketCodec<RegistryByteBuf, NodeFusePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeFusePayload::pos,
            PacketCodecs.INTEGER, NodeFusePayload::index,
            PacketCodecs.BOOL, NodeFusePayload::up,
            NodeFusePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
