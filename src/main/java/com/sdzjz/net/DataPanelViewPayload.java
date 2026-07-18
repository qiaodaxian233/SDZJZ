package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** 客户端→服务端：存储终端的搜索词、滚动行、按本地化显示名匹配出的物品 id 列表（支持中文搜索）。 */
public record DataPanelViewPayload(BlockPos pos, String search, int scrollRow, List<String> matchedIds) implements CustomPayload {

    public static final CustomPayload.Id<DataPanelViewPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "panel_view"));

    public static final PacketCodec<RegistryByteBuf, DataPanelViewPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, DataPanelViewPayload::pos,
            PacketCodecs.STRING, DataPanelViewPayload::search,
            PacketCodecs.INTEGER, DataPanelViewPayload::scrollRow,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), DataPanelViewPayload::matchedIds,
            DataPanelViewPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
