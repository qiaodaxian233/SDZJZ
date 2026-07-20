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
                                List<Long> busCounts) implements CustomPayload {

    public static final CustomPayload.Id<CanvasEndsPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "canvas_ends"));

    public static final PacketCodec<RegistryByteBuf, CanvasEndsPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, CanvasEndsPayload::pos,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.VAR_LONG), CanvasEndsPayload::endPos,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.INTEGER), CanvasEndsPayload::endKind,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING), CanvasEndsPayload::endDim,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING), CanvasEndsPayload::busIds,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.VAR_LONG), CanvasEndsPayload::busCounts,
            CanvasEndsPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
