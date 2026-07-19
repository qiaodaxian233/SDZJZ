package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：过滤器节点配置。entry=物品id 切名单项（在则移除）；entry="" 切换 白名单↔黑名单。 */
public record NodeFilterPayload(BlockPos pos, int index, String entry) implements CustomPayload {

    public static final CustomPayload.Id<NodeFilterPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_filter"));

    public static final PacketCodec<RegistryByteBuf, NodeFilterPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeFilterPayload::pos,
            PacketCodecs.INTEGER, NodeFilterPayload::index,
            PacketCodecs.STRING, NodeFilterPayload::entry,
            NodeFilterPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
