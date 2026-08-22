package com.sdzjz.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * m402 网络平台口（通用/服务端侧）——多加载器路线 P1 第一刀。
 *
 * <p><b>为什么有这个类</b>：m401 的加载器耦合尺量出 networking 一族 <b>124 用点散在 9 个文件</b>，
 * 是换 Forge/NeoForge 时最大的一块。现在全部收进本类与 {@link com.sdzjz.client.ClientNet} 两个漏斗：
 * 业务侧只见 Minecraft 类型（{@link CustomPacketPayload}/{@link ServerPlayer}/MinecraftClient），
 * <b>一个 Fabric 符号都不见</b>。
 *
 * <p><b>m433 接口化（漏斗销账第一名）</b>：本门面自 loader 层迁 xplat，Fabric 内脏抽成
 * {@link Impl}——加载器入口<b>最先</b>调 {@link #install}（Fabric＝Sdzjz.onInitialize 首行，
 * 早于一切 payload 注册），业务侧四个静态口签名逐字未动、调用点零改。换加载器＝多写一个
 * {@code Impl}，本类与业务代码一行不动。未安装即用→显式硬失败（不静默，m99 精神）。
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
    public interface ServerHandler<T extends CustomPacketPayload> {
        void handle(T payload, ServerPlayer player);
    }

    /** 加载器要给的四个口（m433）：与下方四个静态门面一一对应，语义见各门面注释。 */
    public interface Impl {
        <T extends CustomPacketPayload> void c2s(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);
        <T extends CustomPacketPayload> void s2c(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);
        <T extends CustomPacketPayload> void onServer(CustomPacketPayload.Type<T> id, ServerHandler<T> handler);
        void toPlayer(ServerPlayer player, CustomPacketPayload payload);
    }

    private static Impl impl;

    /** 加载器入口最先调（重复安装=装载配置出错，直接炸出来）。 */
    public static void install(Impl i) {
        if (impl != null) throw new IllegalStateException("Net 平台实现重复安装");
        impl = i;
    }

    private static Impl req() {
        if (impl == null) throw new IllegalStateException("Net 平台实现未安装：加载器入口须最先调 Net.install(...)（Fabric=Sdzjz.onInitialize 首行）");
        return impl;
    }

    /** 注册 C2S 包型（编解码器登记，必须双端同序注册）。 */
    public static <T extends CustomPacketPayload> void c2s(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        req().c2s(id, codec);
    }

    /** 注册 S2C 包型。 */
    public static <T extends CustomPacketPayload> void s2c(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        req().s2c(id, codec);
    }

    /** 挂服务端接收器。回调在服务端线程上执行（加载器实现保证），业务侧照旧可直接摸世界。 */
    public static <T extends CustomPacketPayload> void onServer(CustomPacketPayload.Type<T> id, ServerHandler<T> handler) {
        req().onServer(id, handler);
    }

    /** 发：服务端→指定玩家。 */
    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        req().toPlayer(player, payload);
    }
}
