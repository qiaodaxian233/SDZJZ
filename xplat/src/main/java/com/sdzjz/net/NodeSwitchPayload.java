package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：切换开关节点的 开/关。 */
public record NodeSwitchPayload(BlockPos pos, int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodeSwitchPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.of("sdzjz", "node_switch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeSwitchPayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, NodeSwitchPayload::pos,
            ByteBufCodecs.INTEGER, NodeSwitchPayload::index,
            NodeSwitchPayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
