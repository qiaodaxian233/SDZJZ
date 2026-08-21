package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端（m265）：已"拖下画布"的存储端点落位（并行列表：endPos/nx/ny 同序，只发已放置项）。
 * 走 m89 同一条可靠通道（CanvasEndsPayload 的姊妹包，40t 同拍直发正在看画布的玩家）——
 * CanvasEndsPayload 的 tuple 已满 6 元装不下新列表，故独立小包；不在包里的端点=停靠在总线带。
 */
public record StorageNodeHomePayload(BlockPos pos,
                                     List<Long> endPos,
                                     List<Integer> nx,
                                     List<Integer> ny) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StorageNodeHomePayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("sdzjz", "storage_node_home"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageNodeHomePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StorageNodeHomePayload::pos,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_LONG), StorageNodeHomePayload::endPos,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT), StorageNodeHomePayload::nx,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT), StorageNodeHomePayload::ny,
            StorageNodeHomePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
