package com.sdzjz.retro;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * m446（P-C1 刀①）：1.20.1 网络地基——语义蓝本=xplat {@code Net}（m402/m433）四口的本世代对位。
 * 1.20.1 无 CustomPacketPayload/StreamCodec 体系（m436 账），对位走 fabric-api 0.92 的
 * FabricPacket/PacketType（签名经 1.20.1 分支原文实证：PacketType.create(Identifier,Function)、
 * registerGlobalReceiver(PacketType,PlayPacketHandler(T,player,sender))、send(player,packet)）。
 *
 * <p><b>与蓝本的口数差（行内指认）</b>：蓝本 c2s/s2c 两口是 payload 编解码器注册——FabricPacket
 * 体系里编解码随包类自带（write+构造器），无独立注册步，故本类只余 onServer/toPlayer 两口；
 * 客户端两口（发往服务端/S2C 接收）随刀③ RetroClientBootstrap 落 ClientNet120，本类绝不摸
 * 客户端类型（m180 精神的加载期版本，蓝本同注）。
 *
 * <p>Fabric 0.92 服务端接收器本就在服务端线程上跑（原文 javadoc "called on the server thread"），
 * 不额外调度——与蓝本行为口径一致。
 */
public final class Net120 {

    private Net120() { }

    /** 收：服务端接收器（业务侧只见包与发包玩家，蓝本 ServerHandler 同形）。 */
    @FunctionalInterface
    public interface ServerHandler<T extends FabricPacket> {
        void handle(T packet, ServerPlayer player);
    }

    /** C2S 接收注册。重复注册=硬失败——registerGlobalReceiver 对已注册类型返 false 不抛（0.92
     *  原文实证），这里抬成异常带修法指引（m99 静默无效教训的注册期版本，m433 同款口径）。 */
    public static <T extends FabricPacket> void onServer(PacketType<T> type, ServerHandler<T> handler) {
        boolean fresh = ServerPlayNetworking.registerGlobalReceiver(type,
                (packet, player, responseSender) -> handler.handle(packet, player));
        if (!fresh) throw new IllegalStateException(
                "[sdzjz] payload 重复注册: " + type.getId() + " ——同一 PacketType 只许在 bootstrap 注册一次，查重复的 onServer 调用点");
    }

    /** 发给单个玩家（蓝本 toPlayer 同形；包类型随 packet.getType() 自带）。 */
    public static void toPlayer(ServerPlayer player, FabricPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }

    // ===== m291 有界解码红线对位（1.20.1 无 Bounded StreamCodec，红线落在包类的 buf 构造器里）=====
    // 口径：C2S 含串/表的包必须走下面两口——串=readUtf(max) 超长解码期自抛；表=先验声明条数再分配。

    /** 有界读串：直用原版 readUtf(max)（超长在解码期抛 DecoderException，不分配后再裁）。
     *  独立成口是给包类一个统一可 grep 的红线锚点——裸 readUtf() 无上限版禁用于 C2S。 */
    public static String readBoundedUtf(FriendlyByteBuf buf, int maxLength) {
        return buf.readUtf(maxLength);
    }

    /** 有界读计数：**分配前**拒掉恶意声明的超长表（蓝本 m291 Bounded 列表 Codec 的解码期语义）。
     *  用法：int n = readBoundedCount(buf, 上限); 然后循环 n 次逐条读。 */
    public static int readBoundedCount(FriendlyByteBuf buf, int maxCount) {
        int n = buf.readVarInt();
        if (n < 0 || n > maxCount) throw new DecoderException(
                "[sdzjz] 有界解码拒收：声明条数 " + n + " 超上限 " + maxCount);
        return n;
    }
}
