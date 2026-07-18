package com.sdzjz;

import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.screen.DataPanelScreenHandler;
import com.sdzjz.screen.StructureCoreScreenHandler;
import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.net.DataPanelViewPayload;
import com.sdzjz.net.NodeLinkPayload;
import com.sdzjz.net.NodeMovePayload;
import com.sdzjz.net.NodeRemovePayload;
import com.sdzjz.machine.CraftPlanner;
import com.sdzjz.net.NodeTargetPayload;
import com.sdzjz.net.NodeUpgradePayload;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModBlocks;
import com.sdzjz.registry.ModItems;
import com.sdzjz.registry.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

        // 服务器停止时清空存储核心登记表（防跨存档幽灵坐标）
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            StorageCoreBlockEntity.clearAll();
            CraftPlanner.clearCache();
        });

        // 网络：画布节点拖动位置 + 连线（C2S）
        PayloadTypeRegistry.playC2S().register(NodeMovePayload.ID, NodeMovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeLinkPayload.ID, NodeLinkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeUpgradePayload.ID, NodeUpgradePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeTargetPayload.ID, NodeTargetPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeRemovePayload.ID, NodeRemovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DataPanelViewPayload.ID, DataPanelViewPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NodeMovePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return; // 防伪造包操纵任意坐标的核心
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.setNodePos(payload.index(), payload.nx(), payload.ny());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(NodeLinkPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.toggleConnection(payload.from(), payload.to());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(NodeUpgradePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    if (payload.add()) core.addNodeUpgrade(p, payload.index(), payload.type());
                    else core.removeNodeUpgrade(p, payload.index(), payload.type());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(NodeTargetPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (payload.target().length() > 128 || !viewingCore(p, payload.pos())) return;
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.setNodeTarget(payload.index(), payload.target());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(NodeRemovePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.removeNodeAt(p, payload.index());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(DataPanelViewPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (!viewingPanel(p, payload.pos())) return; // 校验走界面而非距离——手持终端可远程开面板
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof DataPanelBlockEntity panel) {
                    panel.setView(payload.search(), payload.scrollRow(), payload.matchedIds());
                }
            });
        });

        LOGGER.info("[生电终结者] 已加载：结构核心画布 + 机器 + 升级 + 连接系统。");
    }

    /** 玩家当前打开的是不是该坐标的结构核心画布。 */
    private static boolean viewingCore(ServerPlayerEntity p, net.minecraft.util.math.BlockPos pos) {
        return p.currentScreenHandler instanceof StructureCoreScreenHandler h && pos.equals(h.blockPos());
    }

    /** 玩家当前打开的是不是该坐标的数据面板（含手持终端远程打开）。 */
    private static boolean viewingPanel(ServerPlayerEntity p, net.minecraft.util.math.BlockPos pos) {
        return p.currentScreenHandler instanceof DataPanelScreenHandler h && pos.equals(h.blockPos());
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
