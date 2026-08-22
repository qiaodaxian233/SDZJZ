package com.sdzjz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * m402 网络平台口（客户端侧）——与 {@link com.sdzjz.net.Net} 成对，见那边的类注释。
 * 客户端专属两口：发往服务端、挂客户端接收器。业务侧（四个屏 + JEI 插件）只见
 * {@link CustomPacketPayload}/{@link Minecraft}，一个 Fabric 符号都不见。
 *
 * <p><b>m433 接口化（漏斗销账第二名）</b>：门面迁 xplat，Fabric 内脏抽成 {@link Impl}，
 * 客户端入口最先调 {@link #install}（Fabric＝SdzjzClient.onInitializeClient 首行）。
 * 业务侧两个静态口签名逐字未动、调用点零改；未安装即用→显式硬失败。
 *
 * <p>{@link #onClient} 的加载器实现保留原有 {@code client.execute(...)} 包裹：接收器体照旧
 * 在客户端主线程上跑，行为与 m402 之前逐位一致。
 */
public final class ClientNet {

    private ClientNet() { }

    /** 收：客户端接收器（业务侧只拿到包与客户端实例）。 */
    @FunctionalInterface
    public interface ClientHandler<T extends CustomPacketPayload> {
        void handle(T payload, Minecraft client);
    }

    /** 加载器要给的两个口（m433）。 */
    public interface Impl {
        void toServer(CustomPacketPayload payload);
        <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> id, ClientHandler<T> handler);
    }

    private static Impl impl;

    /** 客户端入口最先调（重复安装直接炸出来）。 */
    public static void install(Impl i) {
        if (impl != null) throw new IllegalStateException("ClientNet 平台实现重复安装");
        impl = i;
    }

    private static Impl req() {
        if (impl == null) throw new IllegalStateException("ClientNet 平台实现未安装：客户端入口须最先调 ClientNet.install(...)（Fabric=SdzjzClient.onInitializeClient 首行）");
        return impl;
    }

    /** 发：客户端→服务端。 */
    public static void toServer(CustomPacketPayload payload) {
        req().toServer(payload);
    }

    /** 挂客户端接收器（回调落客户端主线程，由加载器实现保证）。 */
    public static <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> id, ClientHandler<T> handler) {
        req().onClient(id, handler);
    }
}
