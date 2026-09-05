package com.sdzjz.loader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

/**
 * m405 生命周期/事件平台口（服务端侧）——多加载器路线 P1 第三刀（耦合尺第三族 events 12 + loader 15）。
 *
 * <p>与 {@link com.sdzjz.net.Net}、{@link com.sdzjz.storage.Xfer} 同一范式：业务侧只见 Minecraft 类型，
 * 一个加载器符号不见；换 NeoForge/Forge 时只需给 {@link Impl} 换一份实现（Neo 侧对应
 * {@code NeoForge.EVENT_BUS} 上的 ServerTickEvent/LevelEvent.Load/PlayerLoggedOutEvent/ServerStoppedEvent）。
 *
 * <p><b>m435 接口化（漏斗销账）</b>：门面迁 xplat，Fabric 内脏（含 m405 那句"yarn 核过 player
 * 是公开字段"的接线与 UseEntity 五参收三参）原样进 loader/FabricHooks，Sdzjz 首段安装。
 *
 * <p><b>唯一不抽的是"入口"本身</b>：{@code implements ModInitializer} 是加载器与我们之间的契约，
 * 天生一家一份——将来它就是 `versions/&lt;loader&gt;` 里那个薄薄的入口类，body 调本类挂钩。
 */
public final class Hooks {

    private Hooks() { }

    /** 右键实体（**抢在 entity.interact() 之前**，m94 抓物笼靠这个不被村民/马截胡）。
     *  返回 SUCCESS 即取消原版后续处理；PASS 一切照旧。 */
    @FunctionalInterface
    public interface UseEntity {
        net.minecraft.world.InteractionResult test(net.minecraft.world.entity.player.Player player,
                                             net.minecraft.world.InteractionHand hand,
                                             net.minecraft.world.entity.Entity entity);
    }

    /** 加载器要给的五个口（m435）：语义见各静态门面注释。 */
    public interface Impl {
        void onServerTickEnd(java.util.function.Consumer<MinecraftServer> h);
        void onWorldLoad(java.util.function.BiConsumer<MinecraftServer, ServerLevel> h);
        void onPlayerDisconnect(java.util.function.Consumer<ServerPlayer> h);
        void onServerStopped(java.util.function.Consumer<MinecraftServer> h);
        void onUseEntity(UseEntity h);
        /** m531（F1a）：命令树注册（Fabric CommandRegistrationCallback / NeoForge RegisterCommandsEvent），消费方 debug/SdzjzCommands。 */
        void onRegisterCommands(java.util.function.Consumer<com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>> h);
    }

    private static Impl impl;

    /** 加载器入口首段调（重复安装直接炸出来）。 */
    public static void install(Impl i) {
        if (impl != null) throw new IllegalStateException("Hooks 平台实现重复安装");
        impl = i;
    }

    private static Impl req() {
        if (impl == null) throw new IllegalStateException("Hooks 平台实现未安装：加载器入口须先调 Hooks.install(...)（Fabric=Sdzjz.onInitialize 首段）");
        return impl;
    }

    /** 每服务器 tick 末尾（原版逻辑跑完之后）。 */
    public static void onServerTickEnd(java.util.function.Consumer<MinecraftServer> h) { req().onServerTickEnd(h); }

    /** 某个维度载入（开服/首次进入）。 */
    public static void onWorldLoad(java.util.function.BiConsumer<MinecraftServer, ServerLevel> h) { req().onWorldLoad(h); }

    /** 玩家下线（业务侧只要玩家本身，不要网络处理器）。 */
    public static void onPlayerDisconnect(java.util.function.Consumer<ServerPlayer> h) { req().onPlayerDisconnect(h); }

    /** 服务器已停止（清全局静态态的唯一时机）。 */
    public static void onServerStopped(java.util.function.Consumer<MinecraftServer> h) { req().onServerStopped(h); }

    public static void onUseEntity(UseEntity h) { req().onUseEntity(h); }
    public static void onRegisterCommands(java.util.function.Consumer<com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>> h) { req().onRegisterCommands(h); } // m531
}
