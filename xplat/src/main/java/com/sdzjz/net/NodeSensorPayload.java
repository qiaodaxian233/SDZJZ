package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：传感器节点配置。item 空串=不改监测物品；threshold 阈值；less true=低于放行。 */
public record NodeSensorPayload(BlockPos pos, int index, String item, long threshold, boolean less) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodeSensorPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "node_sensor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeSensorPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NodeSensorPayload::pos,
            ByteBufCodecs.INTEGER, NodeSensorPayload::index,
            Bounded.string(128), NodeSensorPayload::item, // m291
            ByteBufCodecs.VAR_LONG, NodeSensorPayload::threshold,
            ByteBufCodecs.BOOL, NodeSensorPayload::less,
            NodeSensorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
