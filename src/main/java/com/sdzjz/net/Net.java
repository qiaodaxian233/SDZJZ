package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * m402 网络平台口（通用/服务端侧）——多加载器路线 P1 第一刀。
 *
 * <p><b>为什么有这个类</b>：m401 的加载器耦合尺量出 networking 一族 <b>124 用点散在 9 个文件</b>，
 * 是换 Forge/NeoForge 时最大的一块。现在全部收进本类与 {@link com.sdzjz.client.ClientNet} 两个漏斗：
 * 业务侧只见 Minecraft 类型（{@link CustomPayload}/{@link ServerPlayerEntity}/MinecraftClient），
 * <b>一个 Fabric 符号都不见</b>；换加载器时只需给这两个类各写一份实现，业务代码零改动。
 *
 * <p><b>刻意的边界</b>：客户端专属的口（发往服务端、客户端接收器）在 {@code client/ClientNet}，
 * 不放本类——专用服务端绝不该因为一次类加载就去解析 MinecraftClient（m180 精神的加载期版本）。
 *
 * <p>行为与 m402 之前逐位一致：注册顺序不变、Fabric 的服务端接收器本就在服务端线程上跑，
 * 故本类不额外调度；客户端侧 {@code ClientNet.onClient} 保留原有 {@code client.execute(...)} 包裹。
 */
public final class Net {

    private Net() { }

    /** 收：服务端接收器（业务侧只拿到包与发包玩家，看不见任何加载器类型）。 */
    @FunctionalInterface
    public interface ServerHandler<T extends CustomPayload> {
        void handle(T payload, ServerPlayerEntity player);
    }

    /** 注册 C2S 包型（编解码器登记，必须双端同序注册）。 */
    public static <T extends CustomPayload> void c2s(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(id, codec);
    }

    /** 注册 S2C 包型。 */
    public static <T extends CustomPayload> void s2c(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(id, codec);
    }

    /** 挂服务端接收器。回调在服务端线程上执行（Fabric 保证），业务侧照旧可直接摸世界。 */
    public static <T extends CustomPayload> void onServer(CustomPayload.Id<T> id, ServerHandler<T> handler) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                id, (payload, context) -> handler.handle(payload, context.player()));
    }

    /** 发：服务端→指定玩家。 */
    public static void toPlayer(ServerPlayerEntity player, CustomPayload payload) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
}
