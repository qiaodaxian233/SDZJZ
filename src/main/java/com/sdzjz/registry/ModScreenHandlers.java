package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.screen.DataPanelScreenHandler;
import com.sdzjz.screen.TradeCenterScreenHandler;
import com.sdzjz.screen.StructureCoreScreenHandler;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;

public class ModScreenHandlers {

    public static final ScreenHandlerType<StructureCoreScreenHandler> STRUCTURE_CORE =
            Registry.register(Registries.SCREEN_HANDLER, Sdzjz.id("structure_core"),
                    new ExtendedScreenHandlerType<>(StructureCoreScreenHandler::new, BlockPos.PACKET_CODEC));

    public static final ScreenHandlerType<DataPanelScreenHandler> DATA_PANEL =
            Registry.register(Registries.SCREEN_HANDLER, Sdzjz.id("data_panel"),
                    new ExtendedScreenHandlerType<>(DataPanelScreenHandler::new, BlockPos.PACKET_CODEC));

    public static final ScreenHandlerType<SuperBenchScreenHandler> SUPER_BENCH =
            Registry.register(Registries.SCREEN_HANDLER, Sdzjz.id("super_bench"),
                    new ScreenHandlerType<>(SuperBenchScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final ScreenHandlerType<TradeCenterScreenHandler> TRADE_CENTER =
            Registry.register(Registries.SCREEN_HANDLER, Sdzjz.id("trade_center"),
                    new ExtendedScreenHandlerType<>(TradeCenterScreenHandler::new, BlockPos.PACKET_CODEC));

    public static void init() {}
}
