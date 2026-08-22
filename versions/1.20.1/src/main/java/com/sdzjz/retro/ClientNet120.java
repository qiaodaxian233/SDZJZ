package com.sdzjz.retro;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

/**
 * m448（P-C1 刀③）：客户端网络口——蓝本 xplat ClientNet（m402/m433）两口的本世代对位。
 * FabricPacket 版客户端接收器 0.92 原文明示 "called on the render thread"，故不再包
 * client.execute（蓝本包裹的存在理由是裸 buf 版接收器在网络线程——版本差记档）。
 * 本类只被 client 入口引用（fabric.mod.json "client"），专用服务端零类加载触达（m180 口径）；
 * 与 Legacy 的 client/ 分包不同，本世代客户端类同包 retro——入口隔离才是边界，分包只是组织习惯。
 */
public final class ClientNet120 {

    private ClientNet120() { }

    @FunctionalInterface
    public interface ClientHandler<T extends FabricPacket> {
        void handle(T packet);
    }

    /** S2C 接收注册（重复注册硬失败，Net120 同款口径）。 */
    public static <T extends FabricPacket> void onClient(PacketType<T> type, ClientHandler<T> handler) {
        boolean fresh = ClientPlayNetworking.registerGlobalReceiver(type,
                (packet, player, responseSender) -> handler.handle(packet));
        if (!fresh) throw new IllegalStateException(
                "[sdzjz] 客户端 payload 重复注册: " + type.getId() + " ——只许在 client 入口注册一次");
    }

    /** 发往服务端。 */
    public static void toServer(FabricPacket packet) {
        ClientPlayNetworking.send(packet);
    }
}
