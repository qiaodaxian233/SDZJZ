package com.sdzjz.loader;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** m532（F1b）{@link Menus.Impl} 的 **Fabric 1.21.1 实现**：{@code type} = 原 ModScreenHandlers 的 {@code new ExtendedScreenHandlerType<>(factory, codec)} 原句；
 *  {@code open} = 把 {@link MenuData} 包成 Fabric {@code ExtendedScreenHandlerFactory}（原四 BE 直接 implements 它、TerminalItem 匿名类的三方法原文）再 {@code player.openMenu}——
 *  Fabric 的 openMenu 认这个接口才会随 OpenScreen 包发数据。NeoForge 对位 {@code NeoForgeMenus}（F1d）。 */
public final class FabricMenus implements Menus.Impl {

    @Override
    public <T extends AbstractContainerMenu, D> MenuType<T> type(Menus.Factory<T, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        return new ExtendedScreenHandlerType<>(factory::create, codec);
    }

    @Override
    public void open(Player player, MenuProvider provider) {
        if (provider instanceof MenuData<?> md) openData(player, md);
        else player.openMenu(provider);
    }

    private static <D> void openData(Player player, MenuData<D> md) {
        player.openMenu(new ExtendedScreenHandlerFactory<D>() {
            @Override public D getScreenOpeningData(ServerPlayer sp) { return md.menuData(sp); }
            @Override public Component getDisplayName() { return md.getDisplayName(); }
            @Override public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) { return md.createMenu(syncId, inv, p); }
        });
    }
}
