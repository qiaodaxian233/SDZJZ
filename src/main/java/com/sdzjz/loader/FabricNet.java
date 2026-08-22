package com.sdzjz.loader;

import com.sdzjz.net.Net;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** m433：{@link Net.Impl} 的 Fabric 实现——m402 门面里的四段 Fabric 内脏原样搬来，一行未改。 */
public final class FabricNet implements Net.Impl {

    @Override
    public <T extends CustomPacketPayload> void c2s(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(id, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void s2c(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(id, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void onServer(CustomPacketPayload.Type<T> id, Net.ServerHandler<T> handler) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                id, (payload, context) -> handler.handle(payload, context.player()));
    }

    @Override
    public void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
}
