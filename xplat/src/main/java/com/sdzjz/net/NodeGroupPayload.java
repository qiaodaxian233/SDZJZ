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
 * 客户端→服务端（m191 画布打组）：一包三义，按字段组合分派——
 * gid=-1 且 members 非空 = 建组（name 可空，服务端补"组N"）；
 * gid>=0 且 name 非空     = 重命名；
 * gid>=0 且 name 空       = 解散。
 * 语义在服务端接收器判定，客户端只按上述组合发；members 编码照 CanvasEndsPayload 的
 * members 编码走 Bounded.intList（m291 起解码期有界，协议硬顶 4096）。
 */
public record NodeGroupPayload(BlockPos pos, int gid, String name, List<Integer> members) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NodeGroupPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.of("sdzjz", "node_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeGroupPayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, NodeGroupPayload::pos,
            ByteBufCodecs.INTEGER, NodeGroupPayload::gid,
            Bounded.string(64), NodeGroupPayload::name, // m291 组名(UI setMaxLength 24，协议留余量)
            Bounded.intList(4096), NodeGroupPayload::members, // m291 协议硬顶(玩法上限 maxNodes 可调，协议顶只防分配放大)
            NodeGroupPayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
