package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：拖动画布上的存储接口节点，保存位置。 */
public record StorageNodeMovePayload(BlockPos pos, long storagePos, int nx, int ny) implements CustomPayload {

    public static final CustomPayload.Id<StorageNodeMovePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "storage_node_move"));

    public static final PacketCodec<RegistryByteBuf, StorageNodeMovePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, StorageNodeMovePayload::pos,
            PacketCodecs.VAR_LONG, StorageNodeMovePayload::storagePos,
            PacketCodecs.INTEGER, StorageNodeMovePayload::nx,
            PacketCodecs.INTEGER, StorageNodeMovePayload::ny,
            StorageNodeMovePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
