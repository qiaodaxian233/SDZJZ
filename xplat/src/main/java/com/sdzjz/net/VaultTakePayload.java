package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** m312 客户端→服务端：随身仓库取物。mode 0=取一组64 / 1=拿满一格(getMaxCount,大堆叠下=2^30) / 2=取尽填背包。 */
public record VaultTakePayload(String itemId, int mode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Id<VaultTakePayload> ID =
            new CustomPacketPayload.Id<>(ResourceLocation.of("sdzjz", "vault_take"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultTakePayload> CODEC = StreamCodec.tuple(
            Bounded.string(128), VaultTakePayload::itemId, // m291 解码期有界
            ByteBufCodecs.VAR_INT, VaultTakePayload::mode,
            VaultTakePayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
