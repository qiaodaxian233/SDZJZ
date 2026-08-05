package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 客户端→服务端：JEI 配方"+"填料（m212）。只携带配方 id——材料由服务端权威结算
 *  （仓储优先、背包兜底），不走 JEI 自带的服务端槽位搬运 → 专用服务器无需装 JEI。 */
public record JeiFillPayload(BlockPos pos, Identifier recipeId, boolean max) implements CustomPayload {

    public static final CustomPayload.Id<JeiFillPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "jei_fill"));

    public static final PacketCodec<RegistryByteBuf, JeiFillPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, JeiFillPayload::pos,
            Identifier.PACKET_CODEC, JeiFillPayload::recipeId,
            PacketCodecs.BOOL, JeiFillPayload::max,
            JeiFillPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
