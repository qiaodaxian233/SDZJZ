package com.sdzjz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * m402 网络平台口（客户端侧）——与 {@link com.sdzjz.net.Net} 成对，见那边的类注释。
 * 客户端专属两口：发往服务端、挂客户端接收器。业务侧（四个屏 + JEI 插件）只见
 * {@link CustomPacketPayload}/{@link Minecraft}，一个 Fabric 符号都不见。
 *
 * <p>{@link #onClient} 内部保留原有的 {@code client.execute(...)} 包裹：接收器体照旧在客户端主线程上跑，
 * 行为与 m402 之前逐位一致。
 */
public final class ClientNet {

    private ClientNet() { }

    /** 收：客户端接收器（业务侧只拿到包与客户端实例）。 */
    @FunctionalInterface
    public interface ClientHandler<T extends CustomPacketPayload> {
        void handle(T payload, Minecraft client);
    }

    /** 发：客户端→服务端。 */
    public static void toServer(CustomPacketPayload payload) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
    }

    /** 挂客户端接收器（回调经 client.execute 落到主线程）。 */
    public static <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> id, ClientHandler<T> handler) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                id, (payload, context) -> context.client().execute(() -> handler.handle(payload, context.client())));
    }
}
