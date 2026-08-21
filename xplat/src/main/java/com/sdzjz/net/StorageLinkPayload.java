package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：连/断一条 机器↔存储 定向连线。dir 0=机器→存储(产出) 1=存储→机器(供料)。 */
public record StorageLinkPayload(BlockPos pos, int machineIndex, long storagePos, int dir, String dim) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StorageLinkPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "storage_link"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageLinkPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StorageLinkPayload::pos,
            ByteBufCodecs.INT, StorageLinkPayload::machineIndex,
            ByteBufCodecs.VAR_LONG, StorageLinkPayload::storagePos,
            ByteBufCodecs.INT, StorageLinkPayload::dir,
            Bounded.string(256), StorageLinkPayload::dim, // m291 维度 id
            StorageLinkPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
