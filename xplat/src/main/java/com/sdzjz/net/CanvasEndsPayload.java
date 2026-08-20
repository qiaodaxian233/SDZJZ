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
 * 服务端→客户端（m89）：画布端点 + 总线库存 直发正在观看的玩家。
 * 背景：BE 的 NBT 同步链对这份数据在实机上始终不生效（诊断见 DEVLOG m88/m89），
 * 而 ScreenHandler 属性同步一直可靠——故端点数据改走同级的专用包，不再依赖 BE 同步。
 * 并行列表编码：endPos/endKind/endDim 同序；busIds/busCounts 同序。
 */
public record CanvasEndsPayload(BlockPos pos,
                                List<Long> endPos,
                                List<Integer> endKind,
                                List<String> endDim,
                                List<String> busIds,
                                List<Long> busCounts) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CanvasEndsPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.of("sdzjz", "canvas_ends"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CanvasEndsPayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, CanvasEndsPayload::pos,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_LONG), CanvasEndsPayload::endPos,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INTEGER), CanvasEndsPayload::endKind,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING), CanvasEndsPayload::endDim,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING), CanvasEndsPayload::busIds,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_LONG), CanvasEndsPayload::busCounts,
            CanvasEndsPayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
