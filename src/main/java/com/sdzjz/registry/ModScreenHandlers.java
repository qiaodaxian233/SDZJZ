package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.screen.DataPanelScreenHandler;
import com.sdzjz.screen.ExtractPortScreenHandler;
import com.sdzjz.screen.TradeCenterScreenHandler;
import com.sdzjz.screen.StructureCoreScreenHandler;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.BlockPos;

/** m532（F1b）：四个带开屏数据的菜单类型从 Fabric `new ExtendedScreenHandlerType<>(Handler::new, CODEC)` 改走 `Menus.type(...)` 加载器口（Fabric 实现原句）；
 *  两个无数据的仍是原版 `new MenuType<>(...)`。**前提**：Menus 已装（FabricEntry 在 Sdzjz.init 前装）。 */
public class ModScreenHandlers {

    public static final MenuType<StructureCoreScreenHandler> STRUCTURE_CORE =
            Registry.register(BuiltInRegistries.MENU, Sdzjz.id("structure_core"),
                    com.sdzjz.loader.Menus.type(StructureCoreScreenHandler::new, BlockPos.STREAM_CODEC));

    public static final MenuType<DataPanelScreenHandler> DATA_PANEL =
            Registry.register(BuiltInRegistries.MENU, Sdzjz.id("data_panel"),
                    com.sdzjz.loader.Menus.type(DataPanelScreenHandler::new, BlockPos.STREAM_CODEC));

    public static final MenuType<com.sdzjz.screen.PortableVaultScreenHandler> PORTABLE_VAULT = // m312 随身仓库取物屏（数据在手上包组件里，无需扩展开屏数据）
            Registry.register(BuiltInRegistries.MENU, Sdzjz.id("portable_vault"),
                    new MenuType<>(com.sdzjz.screen.PortableVaultScreenHandler::new, FeatureFlags.VANILLA_SET));

    public static final MenuType<SuperBenchScreenHandler> SUPER_BENCH =
            Registry.register(BuiltInRegistries.MENU, Sdzjz.id("super_bench"),
                    new MenuType<>(SuperBenchScreenHandler::new, FeatureFlags.VANILLA_SET));
    static { SuperBenchScreenHandler.installType(SUPER_BENCH); } // m523（SB2b）菜单类型安装口：handler 挂 1.20.1 白名单后不能再引本类（1.20.1 只编白名单子集，registry 包不可见——m522b 血案），注册完当场装

    public static final MenuType<TradeCenterScreenHandler> TRADE_CENTER =
            Registry.register(BuiltInRegistries.MENU, Sdzjz.id("trade_center"),
                    com.sdzjz.loader.Menus.type(TradeCenterScreenHandler::new, BlockPos.STREAM_CODEC));

    public static final MenuType<ExtractPortScreenHandler> EXTRACT_PORT = // m226 数据线抽取口配置
            Registry.register(BuiltInRegistries.MENU, Sdzjz.id("extract_port"),
                    com.sdzjz.loader.Menus.type(ExtractPortScreenHandler::new, BlockPos.STREAM_CODEC));

    public static void init() {}
}
