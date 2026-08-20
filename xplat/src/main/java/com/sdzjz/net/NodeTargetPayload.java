package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：设置某个自动合成机节点的目标产物（物品 id）。 */
public record NodeTargetPayload(BlockPos pos, int index, String target) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodeTargetPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "node_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeTargetPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NodeTargetPayload::pos,
            ByteBufCodecs.INTEGER, NodeTargetPayload::index,
            Bounded.string(256), NodeTargetPayload::target, // m291 目标串(附魔/药水/交易键)给宽些
            NodeTargetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
