package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：右键画布上某节点=取出该机器（返还玩家）。 */
public record NodeRemovePayload(BlockPos pos, int index) implements CustomPayload {

    public static final CustomPayload.Id<NodeRemovePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_remove"));

    public static final PacketCodec<RegistryByteBuf, NodeRemovePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeRemovePayload::pos,
            PacketCodecs.INTEGER, NodeRemovePayload::index,
            NodeRemovePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
