package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

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
                                     List<Integer> ny) implements CustomPayload {

    public static final CustomPayload.Id<StorageNodeHomePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "storage_node_home"));

    public static final PacketCodec<RegistryByteBuf, StorageNodeHomePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, StorageNodeHomePayload::pos,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.VAR_LONG), StorageNodeHomePayload::endPos,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.INTEGER), StorageNodeHomePayload::nx,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.INTEGER), StorageNodeHomePayload::ny,
            StorageNodeHomePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
