package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

import java.util.List;

/** 客户端→服务端：存储终端的搜索词、滚动行、按本地化显示名匹配出的物品 id 列表（支持中文搜索）。 */
public record DataPanelViewPayload(BlockPos pos, String search, int scrollRow, List<String> matchedIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DataPanelViewPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.of("sdzjz", "panel_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DataPanelViewPayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, DataPanelViewPayload::pos,
            Bounded.string(128), DataPanelViewPayload::search, // m291 有界解码，上限对齐服务端 sanitize
            ByteBufCodecs.INTEGER, DataPanelViewPayload::scrollRow,
            Bounded.stringList(128, 256), DataPanelViewPayload::matchedIds, // m291 越界包解码期拒收
            DataPanelViewPayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
