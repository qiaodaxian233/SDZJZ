package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端（m88 机器库侧栏）：从玩家背包取 1 台指定机器放入画布。 */
public record NodeAddPayload(BlockPos pos, String itemId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Id<NodeAddPayload> ID =
            new CustomPacketPayload.Id<>(ResourceLocation.of("sdzjz", "node_add"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeAddPayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, NodeAddPayload::pos,
            Bounded.string(128), NodeAddPayload::itemId, // m291 物品 id 上限
            NodeAddPayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
