package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：设置某个自动合成机节点的目标产物（物品 id）。 */
public record NodeTargetPayload(BlockPos pos, int index, String target) implements CustomPayload {

    public static final CustomPayload.Id<NodeTargetPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_target"));

    public static final PacketCodec<RegistryByteBuf, NodeTargetPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeTargetPayload::pos,
            PacketCodecs.INTEGER, NodeTargetPayload::index,
            PacketCodecs.STRING, NodeTargetPayload::target,
            NodeTargetPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
