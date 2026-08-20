package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：给某节点加/取升级。 type 0=加速 1=数量 2=并列；add=true 加、false 取回；
 *  count=本次数量（m115a：Shift+点击批量，服务端钳 1..64，逐个执行到失败即停）。 */
public record NodeUpgradePayload(BlockPos pos, int index, int type, boolean add, int count) implements CustomPayload {

    public static final CustomPayload.Id<NodeUpgradePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "node_upgrade"));

    public static final PacketCodec<RegistryByteBuf, NodeUpgradePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, NodeUpgradePayload::pos,
            PacketCodecs.INTEGER, NodeUpgradePayload::index,
            PacketCodecs.INTEGER, NodeUpgradePayload::type,
            PacketCodecs.BOOL, NodeUpgradePayload::add,
            PacketCodecs.INTEGER, NodeUpgradePayload::count,
            NodeUpgradePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
