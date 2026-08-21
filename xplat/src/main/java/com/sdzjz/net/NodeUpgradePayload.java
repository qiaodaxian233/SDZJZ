package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：给某节点加/取升级。 kind 0=加速 1=数量 2=并列；add=true 加、false 取回；
 *  count=本次数量（m115a：Shift+点击批量，服务端钳 1..64，逐个执行到失败即停）。 */
public record NodeUpgradePayload(BlockPos pos, int index, int kind, boolean add, int count) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodeUpgradePayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "node_upgrade"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeUpgradePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NodeUpgradePayload::pos,
            ByteBufCodecs.INT, NodeUpgradePayload::index,
            ByteBufCodecs.INT, NodeUpgradePayload::kind,
            ByteBufCodecs.BOOL, NodeUpgradePayload::add,
            ByteBufCodecs.INT, NodeUpgradePayload::count,
            NodeUpgradePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
