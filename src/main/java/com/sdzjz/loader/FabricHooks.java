package com.sdzjz.loader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

/** m435：{@link Hooks.Impl} 的 Fabric 实现——m405 门面里的五段 Fabric 内脏原样搬来，
 *  含"yarn 核过 player 是公开字段"接线与 UseEntity 五参收三参包裹，一行未改。 */
public final class FabricHooks implements Hooks.Impl {

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
                (handler, server) -> h.accept(handler.player)); // yarn 核过：player 是公开字段，无 getPlayer()
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
