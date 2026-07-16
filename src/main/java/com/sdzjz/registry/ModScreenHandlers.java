package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.screen.StructureCoreScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;

public class ModScreenHandlers {

    public static final ScreenHandlerType<StructureCoreScreenHandler> STRUCTURE_CORE =
            Registry.register(Registries.SCREEN_HANDLER, Sdzjz.id("structure_core"),
                    new ExtendedScreenHandlerType<>(StructureCoreScreenHandler::new, BlockPos.PACKET_CODEC));

    public static void init() {
        // 触发静态初始化
    }
}
