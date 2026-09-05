package com.sdzjz.retro;

import com.sdzjz.loader.Hooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** m530（N2b）：{@link Hooks.Impl} 的 **Fabric 1.20.1 实现**——主线 {@code loader/FabricHooks}（m435）逐句原文：Fabric API 这五个事件
 *  1.20.1 与 1.21.1 同名同签名（lifecycle v1 / networking v1 / player UseEntityCallback）。本刀首个消费方=抓物笼右键实体（m94 UseEntityCallback 抢在
 *  村民交易/驯兽交互之前），其余四口先装着（画布/存储登记表消费方随 A 线到位后直接用）。装配点：RetroBootstrap（主线 Sdzjz.onInitialize 同位）。 */
public final class RetroHooks implements Hooks.Impl {

    @Override
    public void onServerTickEnd(java.util.function.Consumer<MinecraftServer> h) {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(h::accept);
    }

    @Override
    public void onWorldLoad(java.util.function.BiConsumer<MinecraftServer, ServerLevel> h) {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register(h::accept);
    }

    @Override
    public void onPlayerDisconnect(java.util.function.Consumer<ServerPlayer> h) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> h.accept(handler.player)); // yarn 核过：player 是公开字段（主线原注）
    }

    @Override
    public void onServerStopped(java.util.function.Consumer<MinecraftServer> h) {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(h::accept);
    }

    @Override
    public void onUseEntity(Hooks.UseEntity h) {
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> h.test(player, hand, entity));
    }
}
