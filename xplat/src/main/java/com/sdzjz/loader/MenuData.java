package com.sdzjz.loader;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

/** m532（F1b）**带开屏数据的菜单提供者**（加载器无关）：Fabric {@code ExtendedScreenHandlerFactory<D>} 与 NeoForge
 *  "{@code openMenu(provider, buf 写入)}" 的共同抽象——provider 只负责**给出**数据 {@link #menuData}，怎么发给客户端由 {@link Menus} 的加载器实现管。
 *  四方块实体（结构核心/数据面板/交易中心/数据线抽取口）与终端物品的远程面板工厂实现本接口；数据类型 D 的编解码在注册菜单类型时给（{@link Menus#type}）。 */
public interface MenuData<D> extends MenuProvider {
    /** 原 Fabric {@code getScreenOpeningData(ServerPlayer)}：开屏时随 OpenScreen 包发给客户端的数据（本 MOD 全是 BlockPos）。 */
    D menuData(ServerPlayer player);
}
