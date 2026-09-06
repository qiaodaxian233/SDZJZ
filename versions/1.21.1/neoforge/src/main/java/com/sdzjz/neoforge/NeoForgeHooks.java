package com.sdzjz.neoforge;

import com.sdzjz.loader.Hooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** m535（F1d-1）{@link Hooks.Impl} 的 NeoForge 实现——对位 {@code loader/FabricHooks} 六口，一口一事件，全挂**游戏总线**
 *  {@code NeoForge.EVENT_BUS}（随时可挂，不受注册期限制）：
 *  ServerTickEvents.END_SERVER_TICK → ServerTickEvent.Post；ServerWorldEvents.LOAD → LevelEvent.Load（只放行 ServerLevel）；
 *  ServerPlayConnectionEvents.DISCONNECT → PlayerEvent.PlayerLoggedOutEvent；SERVER_STOPPED → ServerStoppedEvent；
 *  UseEntityCallback → PlayerInteractEvent.EntityInteract（Fabric 返回非 PASS 即吃掉原版交互；这边=取消事件并设 cancellationResult，
 *  m94 抓物笼「抢在 entity.interact 之前」语义同）；CommandRegistrationCallback → RegisterCommandsEvent。 */
public final class NeoForgeHooks implements Hooks.Impl {
    @Override
    public void onServerTickEnd(java.util.function.Consumer<MinecraftServer> h) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> h.accept(e.getServer()));
    }

    @Override
    public void onWorldLoad(java.util.function.BiConsumer<MinecraftServer, ServerLevel> h) {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load e) -> {
            if (e.getLevel() instanceof ServerLevel sl) h.accept(sl.getServer(), sl);
        });
    }

    @Override
    public void onPlayerDisconnect(java.util.function.Consumer<ServerPlayer> h) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) h.accept(sp);
        });
    }

    @Override
    public void onServerStopped(java.util.function.Consumer<MinecraftServer> h) {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> h.accept(e.getServer()));
    }

    @Override
    public void onUseEntity(Hooks.UseEntity h) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) -> {
            InteractionResult r = h.test(e.getEntity(), e.getHand(), e.getTarget());
            if (r != InteractionResult.PASS) { // Fabric：非 PASS 即取消后续原版处理（交易界面不弹）
                e.setCancellationResult(r);
                e.setCanceled(true);
            }
        });
    }

    @Override
    public void onRegisterCommands(java.util.function.Consumer<com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>> h) {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> h.accept(e.getDispatcher()));
    }
}
