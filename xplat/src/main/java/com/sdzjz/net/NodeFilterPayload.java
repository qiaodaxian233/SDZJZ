package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：过滤器节点配置。entry=物品id 切名单项（在则移除）；entry="" 切换 白名单↔黑名单。 */
public record NodeFilterPayload(BlockPos pos, int index, String entry) implements CustomPacketPayload {

    public static final CustomPacketPayload.Id<NodeFilterPayload> ID =
            new CustomPacketPayload.Id<>(ResourceLocation.of("sdzjz", "node_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeFilterPayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, NodeFilterPayload::pos,
            ByteBufCodecs.INTEGER, NodeFilterPayload::index,
            Bounded.string(128), NodeFilterPayload::entry, // m291
            NodeFilterPayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
