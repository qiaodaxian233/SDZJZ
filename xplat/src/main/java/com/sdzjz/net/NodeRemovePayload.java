package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：右键画布上某节点=取出该机器（返还玩家）。 */
public record NodeRemovePayload(BlockPos pos, int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodeRemovePayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.of("sdzjz", "node_remove"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeRemovePayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, NodeRemovePayload::pos,
            ByteBufCodecs.INTEGER, NodeRemovePayload::index,
            NodeRemovePayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
