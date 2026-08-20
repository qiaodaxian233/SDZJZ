package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** m312 客户端→服务端：随身仓库取物。mode 0=取一组64 / 1=拿满一格(getMaxCount,大堆叠下=2^30) / 2=取尽填背包。 */
public record VaultTakePayload(String itemId, int mode) implements CustomPayload {

    public static final CustomPayload.Id<VaultTakePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "vault_take"));

    public static final PacketCodec<RegistryByteBuf, VaultTakePayload> CODEC = PacketCodec.tuple(
            Bounded.string(128), VaultTakePayload::itemId, // m291 解码期有界
            PacketCodecs.VAR_INT, VaultTakePayload::mode,
            VaultTakePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
