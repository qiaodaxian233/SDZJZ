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
 * 客户端→服务端（m191 画布打组）：一包三义，按字段组合分派——
 * gid=-1 且 members 非空 = 建组（name 可空，服务端补"组N"）；
 * gid>=0 且 name 非空     = 重命名；
 * gid>=0 且 name 空       = 解散。
 * 语义在服务端接收器判定，客户端只按上述组合发；members 编码照 CanvasEndsPayload 的
 * PacketCodecs.collection 在树先例（tuple 六元先例在前，本包四元无上限之虞）。
 */
public record NodeGroupPayload(BlockPos pos, int gid, String name, List<Integer> members) implements CustomPayload {

    public static final CustomPayload.Id<NodeGroupPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_group"));

    public static final PacketCodec<RegistryByteBuf, NodeGroupPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeGroupPayload::pos,
            PacketCodecs.INTEGER, NodeGroupPayload::gid,
            PacketCodecs.STRING, NodeGroupPayload::name,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.INTEGER), NodeGroupPayload::members,
            NodeGroupPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
