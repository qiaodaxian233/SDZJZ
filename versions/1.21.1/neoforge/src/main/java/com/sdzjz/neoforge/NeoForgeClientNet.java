package com.sdzjz.neoforge;

import com.sdzjz.client.ClientNet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/** m536（F1d-2a）{@link ClientNet.Impl} 的 NeoForge 实现——对位 {@code client/FabricClientNet} 两口：
 *  ClientPlayNetworking.send → PacketDistributor.sendToServer；registerGlobalReceiver → 本类接收器表，
 *  由 {@link NeoForgeNet} 的 s2c 转发 lambda（那边只认 CLIENT_DISPATCH 口，零客户端类引用）经 {@link #dispatch} 查表调用。
 *  线程：NeoForge 处理器默认主线程，与 Fabric 版 {@code client.execute(...)} 同义，不再二次投递。 */
public final class NeoForgeClientNet implements ClientNet.Impl {

    private static final Map<CustomPacketPayload.Type<?>, ClientNet.ClientHandler<?>> HANDLERS = new HashMap<>();

    @Override
    public void toServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> id, ClientNet.ClientHandler<T> handler) {
        HANDLERS.put(id, handler);
    }

    @SuppressWarnings("unchecked")
    static void dispatch(CustomPacketPayload.Type<?> id, CustomPacketPayload payload) {
        ClientNet.ClientHandler<CustomPacketPayload> h = (ClientNet.ClientHandler<CustomPacketPayload>) HANDLERS.get(id);
        if (h != null) h.handle(payload, Minecraft.getInstance());
    }
}
