package com.sdzjz.neoforge;

import com.sdzjz.net.Net;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** m535（F1d-1）{@link Net.Impl} 的 NeoForge 实现——对位 {@code loader/FabricNet} 四口，两处世代差都在「时机」上：
 *  <p>①Fabric 的 {@code PayloadTypeRegistry.playC2S().register} 随时可调；NeoForge 的 {@link PayloadRegistrar} **只在
 *  {@link RegisterPayloadHandlersEvent} 作用域内有效**（出了事件即失效，注册直接抛）——而业务侧 {@code Sdzjz.initHooksAndNet()}
 *  在 @Mod 构造器里就把几十个 c2s/s2c 交过来了，所以本类把每笔注册**缓冲**成 {@code Consumer<PayloadRegistrar>}，事件来了逐条落。
 *  <p>②Fabric 类型注册与接收器注册是两口（{@code c2s} 与 {@code onServer} 各自登记）；NeoForge 的 {@code playToServer} 一口要
 *  类型+codec+处理器齐全——处理器这边给的是**晚绑定分派**：落地时只登记一个转发 lambda，真处理时再查 {@link #SERVER} 表，
 *  于是 {@code onServer} 在事件前后调都行，也不必为「登记了类型没登记接收器」的包造假处理器。
 *  s2c 同理转给客户端分派口 {@link #CLIENT_DISPATCH}（由客户端入口 F1d-2 装；专用服务器上恒 null，本类零客户端类引用）。
 *  <p>线程：NeoForge 处理器默认在主线程（registrar 默认 {@code HandlerThread.MAIN}），与 Fabric 1.21 play 接收器在服务端线程一致。 */
public final class NeoForgeNet implements Net.Impl {

    private static final List<Consumer<PayloadRegistrar>> PENDING = new ArrayList<>();
    private static final Map<CustomPacketPayload.Type<?>, Net.ServerHandler<?>> SERVER = new HashMap<>();
    private static volatile BiConsumer<CustomPacketPayload.Type<?>, CustomPacketPayload> CLIENT_DISPATCH; // 客户端入口装（F1d-2）
    private static boolean flushed;

    @Override
    public <T extends CustomPacketPayload> void c2s(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        enqueue(reg -> reg.playToServer(id, codec, (payload, ctx) -> dispatchServer(id, payload, ctx)));
    }

    @Override
    public <T extends CustomPacketPayload> void s2c(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        enqueue(reg -> reg.playToClient(id, codec, (payload, ctx) -> {
            BiConsumer<CustomPacketPayload.Type<?>, CustomPacketPayload> d = CLIENT_DISPATCH;
            if (d != null) d.accept(id, payload);
        }));
    }

    @Override
    public <T extends CustomPacketPayload> void onServer(CustomPacketPayload.Type<T> id, Net.ServerHandler<T> handler) {
        SERVER.put(id, handler);
    }

    @Override
    public void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void enqueue(Consumer<PayloadRegistrar> r) {
        if (flushed) throw new IllegalStateException("payload 类型必须在 RegisterPayloadHandlersEvent 之前登记（Sdzjz.initHooksAndNet 在 @Mod 构造器里调）");
        PENDING.add(r);
    }

    /** 模组总线 {@link RegisterPayloadHandlersEvent}：把构造器期缓冲的全部登记落进 registrar（网络版本 "1"，两端同模组同版本即通）。 */
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");
        for (Consumer<PayloadRegistrar> r : PENDING) r.accept(reg);
        PENDING.clear();
        flushed = true;
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void dispatchServer(CustomPacketPayload.Type<T> id, T payload, IPayloadContext ctx) {
        Net.ServerHandler<T> h = (Net.ServerHandler<T>) SERVER.get(id);
        if (h != null && ctx.player() instanceof ServerPlayer sp) h.handle(payload, sp);
    }

    /** 客户端入口（F1d-2 {@code NeoForgeClientNet}）装分派口：收到 s2c 包按类型查客户端接收器表。 */
    public static void installClientDispatch(BiConsumer<CustomPacketPayload.Type<?>, CustomPacketPayload> d) {
        CLIENT_DISPATCH = d;
    }
}
