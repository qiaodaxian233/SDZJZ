package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：切换开关节点的 开/关。 */
public record NodeSwitchPayload(BlockPos pos, int index) implements CustomPayload {

    public static final CustomPayload.Id<NodeSwitchPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_switch"));

    public static final PacketCodec<RegistryByteBuf, NodeSwitchPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeSwitchPayload::pos,
            PacketCodecs.INTEGER, NodeSwitchPayload::index,
            NodeSwitchPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
