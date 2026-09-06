package com.sdzjz.neoforge;

import com.sdzjz.loader.MenuData;
import com.sdzjz.loader.Menus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/** m535（F1d-1）{@link Menus.Impl} 的 NeoForge 实现——对位 {@code loader/FabricMenus} 两口：
 *  type：{@code new ExtendedScreenHandlerType<>(factory::create, codec)} → {@code IMenuTypeExtension.create(IContainerFactory)}，
 *  客户端建菜单时从 buf 用同一个 codec 解出 D；open：Fabric 把 MenuData 包成 ExtendedScreenHandlerFactory 交给 openMenu 让它自己找
 *  codec；NeoForge 的 {@code openMenu(provider, Consumer<RegistryFriendlyByteBuf>)} 在写数据那一步拿不到菜单类型——
 *  所以 m535 给 {@link MenuData} 加了 {@code menuCodec()} 由 provider 自报（与 type 传的是同一个 codec）。 */
public final class NeoForgeMenus implements Menus.Impl {
    @Override
    public <T extends AbstractContainerMenu, D> MenuType<T> type(Menus.Factory<T, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        return IMenuTypeExtension.create((windowId, inv, buf) -> factory.create(windowId, inv, codec.decode(buf)));
    }

    @Override
    public void open(Player player, MenuProvider provider) {
        if (provider instanceof MenuData<?> md) openData(player, md);
        else player.openMenu(provider);
    }

    private static <D> void openData(Player player, MenuData<D> md) {
        if (player instanceof ServerPlayer sp) player.openMenu(md, buf -> md.menuCodec().encode(buf, md.menuData(sp)));
        else player.openMenu(md); // 客户端侧 openMenu 本就是空操作（Fabric 同）
    }
}
