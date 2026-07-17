package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：给某节点加/取一个升级。 type 0=加速 1=数量 2=并列；add=true 加、false 取回。 */
public record NodeUpgradePayload(BlockPos pos, int index, int type, boolean add) implements CustomPayload {

    public static final CustomPayload.Id<NodeUpgradePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_upgrade"));

    public static final PacketCodec<RegistryByteBuf, NodeUpgradePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeUpgradePayload::pos,
            PacketCodecs.INTEGER, NodeUpgradePayload::index,
            PacketCodecs.INTEGER, NodeUpgradePayload::type,
            PacketCodecs.BOOLEAN, NodeUpgradePayload::add,
            NodeUpgradePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
