package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** m386 客户端→服务端：手持区块移除器的设置面板写包（hand 0=主手 1=副手；radius 区域半径
 *  绝对值服务端按 chunkRemoverMaxRadius 钳；mode 0=有掉落 1=无掉落）。定长三整型=天然有界
 *  （m291 口径"纯定长"）；服务端先验手上确为移除器再落 NBT，半径变更=重扫（#zrd 同口径）。 */
public record ChunkRemoverConfigPayload(int hand, int radius, int mode) implements CustomPayload {

    public static final CustomPayload.Id<ChunkRemoverConfigPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "chunk_remover_config"));

    public static final PacketCodec<RegistryByteBuf, ChunkRemoverConfigPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, ChunkRemoverConfigPayload::hand,
            PacketCodecs.INTEGER, ChunkRemoverConfigPayload::radius,
            PacketCodecs.INTEGER, ChunkRemoverConfigPayload::mode,
            ChunkRemoverConfigPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
