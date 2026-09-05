package com.sdzjz.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** m532（F1b）**菜单数据加载器口**（Net/Xfer/Env/Hooks 同族，m433~m435 样式）：带开屏数据的菜单在两加载器写法不同——
 *  Fabric：{@code new ExtendedScreenHandlerType<>(factory, codec)} + provider 实现 {@code ExtendedScreenHandlerFactory}；
 *  NeoForge：{@code IMenuTypeExtension.create((id, inv, buf) -> …)} + {@code player.openMenu(provider, buf -> …)}。
 *  业务侧只认两句：{@link #type} 建类型、{@link #open} 开屏；provider 实现 {@link MenuData}。**1.21 世代专用**（StreamCodec 1.20.5+），不上 1.20.1 白名单——
 *  1.20.1 世代壳自有其菜单注册（RetroBlocks，Fabric 1.20.1 ExtendedScreenHandlerType + PacketByteBuf）。
 *  安装点：加载器入口（FabricEntry / NeoForgeEntry），**必须早于 ModScreenHandlers 类初始化**（它在 Sdzjz.init 里）。 */
public final class Menus {

    private Menus() { }

    /** 菜单工厂：{@code (syncId, inv, data) -> handler}——本 MOD 五个带数据 handler 的三参构造器都是这形状（方法引用直接用）。 */
    @FunctionalInterface
    public interface Factory<T extends AbstractContainerMenu, D> {
        T create(int syncId, Inventory inv, D data);
    }

    public interface Impl {
        <T extends AbstractContainerMenu, D> MenuType<T> type(Factory<T, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec);
        /** 开屏：provider 若实现 {@link MenuData} 则连数据一起发；否则等价 {@code player.openMenu(provider)}。 */
        void open(Player player, MenuProvider provider);
    }

    private static Impl impl;

    public static void install(Impl i) { impl = i; }

    private static Impl req() {
        if (impl == null) throw new IllegalStateException("Menus 平台实现未安装：加载器入口须在 Sdzjz.init() 之前调 Menus.install(...)（Fabric=FabricEntry）");
        return impl;
    }

    public static <T extends AbstractContainerMenu, D> MenuType<T> type(Factory<T, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        return req().type(factory, codec);
    }

    public static void open(Player player, MenuProvider provider) { req().open(player, provider); }
}
