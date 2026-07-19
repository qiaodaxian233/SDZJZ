package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：传感器节点配置。item 空串=不改监测物品；threshold 阈值；less true=低于放行。 */
public record NodeSensorPayload(BlockPos pos, int index, String item, long threshold, boolean less) implements CustomPayload {

    public static final CustomPayload.Id<NodeSensorPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_sensor"));

    public static final PacketCodec<RegistryByteBuf, NodeSensorPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeSensorPayload::pos,
            PacketCodecs.INTEGER, NodeSensorPayload::index,
            PacketCodecs.STRING, NodeSensorPayload::item,
            PacketCodecs.VAR_LONG, NodeSensorPayload::threshold,
            PacketCodecs.BOOL, NodeSensorPayload::less,
            NodeSensorPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
