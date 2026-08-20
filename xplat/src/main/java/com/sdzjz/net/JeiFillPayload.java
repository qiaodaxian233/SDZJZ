package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

/** 客户端→服务端：JEI 配方"+"填料（m212）。只携带配方 id——材料由服务端权威结算
 *  （仓储优先、背包兜底），不走 JEI 自带的服务端槽位搬运 → 专用服务器无需装 JEI。 */
public record JeiFillPayload(BlockPos pos, ResourceLocation recipeId, boolean max) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<JeiFillPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.of("sdzjz", "jei_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JeiFillPayload> CODEC = StreamCodec.tuple(
            BlockPos.PACKET_CODEC, JeiFillPayload::pos,
            ResourceLocation.PACKET_CODEC, JeiFillPayload::recipeId,
            ByteBufCodecs.BOOL, JeiFillPayload::max,
            JeiFillPayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
