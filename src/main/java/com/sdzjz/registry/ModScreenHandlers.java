package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.screen.DataPanelScreenHandler;
import com.sdzjz.screen.ExtractPortScreenHandler;
import com.sdzjz.screen.TradeCenterScreenHandler;
import com.sdzjz.screen.StructureCoreScreenHandler;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.BlockPos;

public class ModScreenHandlers {

    public static final MenuType<StructureCoreScreenHandler> STRUCTURE_CORE =
            Registry.register(BuiltInRegistries.SCREEN_HANDLER, Sdzjz.id("structure_core"),
                    new ExtendedScreenHandlerType<>(StructureCoreScreenHandler::new, BlockPos.STREAM_CODEC));

    public static final MenuType<DataPanelScreenHandler> DATA_PANEL =
            Registry.register(BuiltInRegistries.SCREEN_HANDLER, Sdzjz.id("data_panel"),
                    new ExtendedScreenHandlerType<>(DataPanelScreenHandler::new, BlockPos.STREAM_CODEC));

    public static final MenuType<com.sdzjz.screen.PortableVaultScreenHandler> PORTABLE_VAULT = // m312 随身仓库取物屏（数据在手上包组件里，无需扩展开屏数据）
            Registry.register(BuiltInRegistries.SCREEN_HANDLER, Sdzjz.id("portable_vault"),
                    new MenuType<>(com.sdzjz.screen.PortableVaultScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final MenuType<SuperBenchScreenHandler> SUPER_BENCH =
            Registry.register(BuiltInRegistries.SCREEN_HANDLER, Sdzjz.id("super_bench"),
                    new MenuType<>(SuperBenchScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final MenuType<TradeCenterScreenHandler> TRADE_CENTER =
            Registry.register(BuiltInRegistries.SCREEN_HANDLER, Sdzjz.id("trade_center"),
                    new ExtendedScreenHandlerType<>(TradeCenterScreenHandler::new, BlockPos.STREAM_CODEC));

    public static final MenuType<ExtractPortScreenHandler> EXTRACT_PORT = // m226 数据线抽取口配置
            Registry.register(BuiltInRegistries.SCREEN_HANDLER, Sdzjz.id("extract_port"),
                    new ExtendedScreenHandlerType<>(ExtractPortScreenHandler::new, BlockPos.STREAM_CODEC));

    public static void init() {}
}
