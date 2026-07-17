package com.sdzjz;

import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.net.NodeLinkPayload;
import com.sdzjz.net.NodeMovePayload;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModBlocks;
import com.sdzjz.registry.ModItems;
import com.sdzjz.registry.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sdzjz implements ModInitializer {
    public static final String MOD_ID = "sdzjz";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SdzjzConfig.load();
        ModBlocks.init();
        ModBlockEntities.init();
        ModScreenHandlers.init();
        ModItems.init();

        // 网络：画布节点拖动位置 + 连线（C2S）
        PayloadTypeRegistry.playC2S().register(NodeMovePayload.ID, NodeMovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeLinkPayload.ID, NodeLinkPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NodeMovePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.setNodePos(payload.index(), payload.nx(), payload.ny());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(NodeLinkPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.toggleConnection(payload.from(), payload.to());
                }
            });
        });

        LOGGER.info("[生电终结者] 已加载：结构核心画布 + 机器 + 升级 + 连接系统。");
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
