package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** m386 客户端→服务端：手持区块移除器的设置面板写包（hand 0=主手 1=副手；radius 区域半径
 *  绝对值服务端按 chunkRemoverMaxRadius 钳；mode 0=有掉落 1=无掉落；seal 0/1 封边挡水 m388）。
 *  定长四整型=天然有界（m291 口径"纯定长"）；服务端先验手上确为移除器再落 NBT，
 *  半径变更/开堵水=重扫（#zrd 同口径）。 */
public record ChunkRemoverConfigPayload(int hand, int radius, int mode, int seal) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChunkRemoverConfigPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.of("sdzjz", "chunk_remover_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkRemoverConfigPayload> CODEC = StreamCodec.tuple(
            ByteBufCodecs.INTEGER, ChunkRemoverConfigPayload::hand,
            ByteBufCodecs.INTEGER, ChunkRemoverConfigPayload::radius,
            ByteBufCodecs.INTEGER, ChunkRemoverConfigPayload::mode,
            ByteBufCodecs.INTEGER, ChunkRemoverConfigPayload::seal,
            ChunkRemoverConfigPayload::new
    );

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
