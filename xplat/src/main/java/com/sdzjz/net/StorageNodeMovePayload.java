package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：拖动画布上的存储接口节点，保存位置。
 *  m265：dock=true 表示"收回总线"（清除画布落位回停靠栏），此时 nx/ny 忽略；
 *  dock=false 表示"放置/移动到画布"（服务端钳 ±1,000,000 后落 storageNodePos 并置放置标记）。 */
public record StorageNodeMovePayload(BlockPos pos, long storagePos, int nx, int ny, boolean dock) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StorageNodeMovePayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "storage_node_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageNodeMovePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StorageNodeMovePayload::pos,
            ByteBufCodecs.VAR_LONG, StorageNodeMovePayload::storagePos,
            ByteBufCodecs.INT, StorageNodeMovePayload::nx,
            ByteBufCodecs.INT, StorageNodeMovePayload::ny,
            ByteBufCodecs.BOOL, StorageNodeMovePayload::dock,
            StorageNodeMovePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
