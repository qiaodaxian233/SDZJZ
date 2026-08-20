package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：拖动画布上的存储接口节点，保存位置。
 *  m265：dock=true 表示"收回总线"（清除画布落位回停靠栏），此时 nx/ny 忽略；
 *  dock=false 表示"放置/移动到画布"（服务端钳 ±1,000,000 后落 storageNodePos 并置放置标记）。 */
public record StorageNodeMovePayload(BlockPos pos, long storagePos, int nx, int ny, boolean dock) implements CustomPayload {

    public static final CustomPayload.Id<StorageNodeMovePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "storage_node_move"));

    public static final PacketCodec<RegistryByteBuf, StorageNodeMovePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, StorageNodeMovePayload::pos,
            PacketCodecs.VAR_LONG, StorageNodeMovePayload::storagePos,
            PacketCodecs.INTEGER, StorageNodeMovePayload::nx,
            PacketCodecs.INTEGER, StorageNodeMovePayload::ny,
            PacketCodecs.BOOL, StorageNodeMovePayload::dock,
            StorageNodeMovePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
