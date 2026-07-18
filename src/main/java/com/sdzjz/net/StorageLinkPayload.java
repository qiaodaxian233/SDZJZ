package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：连/断一条 机器↔存储 定向连线。dir 0=机器→存储(产出) 1=存储→机器(供料)。 */
public record StorageLinkPayload(BlockPos pos, int machineIndex, long storagePos, int dir, String dim) implements CustomPayload {

    public static final CustomPayload.Id<StorageLinkPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "storage_link"));

    public static final PacketCodec<RegistryByteBuf, StorageLinkPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, StorageLinkPayload::pos,
            PacketCodecs.INTEGER, StorageLinkPayload::machineIndex,
            PacketCodecs.VAR_LONG, StorageLinkPayload::storagePos,
            PacketCodecs.INTEGER, StorageLinkPayload::dir,
            PacketCodecs.STRING, StorageLinkPayload::dim,
            StorageLinkPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
