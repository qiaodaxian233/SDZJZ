package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端（m88 机器库侧栏）：从玩家背包取 1 台指定机器放入画布。 */
public record NodeAddPayload(BlockPos pos, String itemId) implements CustomPayload {

    public static final CustomPayload.Id<NodeAddPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_add"));

    public static final PacketCodec<RegistryByteBuf, NodeAddPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeAddPayload::pos,
            PacketCodecs.STRING, NodeAddPayload::itemId,
            NodeAddPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
