package com.sdzjz.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** m433：{@link ClientNet.Impl} 的 Fabric 实现——m402 门面里的两段 Fabric 内脏原样搬来，
 *  {@code client.execute(...)} 包裹一行未改。 */
public final class FabricClientNet implements ClientNet.Impl {

    @Override
    public void toServer(CustomPacketPayload payload) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
    }

    @Override
    public <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> id, ClientNet.ClientHandler<T> handler) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                id, (payload, context) -> context.client().execute(() -> handler.handle(payload, context.client())));
    }
}
