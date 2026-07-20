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
import com.sdzjz.net.NodeAddPayload;
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

        // m94：抓物笼捕获改走实体交互事件——抢在 entity.interact() 之前触发，
        // 否则村民（交易界面）/马（骑乘）/驯服猫狗（坐下）等自带右键交互的生物会把捕获整个截胡，
        // useOnEntity 永远轮不到执行（僵尸/骷髅这类无交互生物不受影响，两条路都通）。
        // 返回 SUCCESS 即取消原版后续处理（交易界面不弹）；PASS 时一切照旧。
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof net.minecraft.entity.LivingEntity living)) return net.minecraft.util.ActionResult.PASS;
            if (!(player.getStackInHand(hand).getItem() instanceof com.sdzjz.item.CaptureCageItem)) return net.minecraft.util.ActionResult.PASS;
            return com.sdzjz.item.CaptureCageItem.tryCapture(player, hand, living);
        });

        // 服务器停止时清空存储核心登记表（防跨存档幽灵坐标）
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            StorageCoreBlockEntity.clearAll();
            CraftPlanner.clearCache();
            com.sdzjz.machine.SmeltPlanner.clearCache();
        });

        // 网络：画布节点拖动位置 + 连线（C2S）
        PayloadTypeRegistry.playC2S().register(NodeMovePayload.ID, NodeMovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeLinkPayload.ID, NodeLinkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeUpgradePayload.ID, NodeUpgradePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeTargetPayload.ID, NodeTargetPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeRemovePayload.ID, NodeRemovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NodeAddPayload.ID, NodeAddPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.sdzjz.net.CanvasEndsPayload.ID, com.sdzjz.net.CanvasEndsPayload.CODEC); // m89
        PayloadTypeRegistry.playC2S().register(DataPanelViewPayload.ID, DataPanelViewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.sdzjz.net.StorageLinkPayload.ID, com.sdzjz.net.StorageLinkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.sdzjz.net.StorageNodeMovePayload.ID, com.sdzjz.net.StorageNodeMovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.sdzjz.net.NodeFilterPayload.ID, com.sdzjz.net.NodeFilterPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.sdzjz.net.NodeSensorPayload.ID, com.sdzjz.net.NodeSensorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.sdzjz.net.NodeSwitchPayload.ID, com.sdzjz.net.NodeSwitchPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.sdzjz.net.NodeSwitchPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.toggleSwitch(payload.index());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(com.sdzjz.net.NodeFilterPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (payload.entry().length() > 128 || !viewingCore(p, payload.pos())) return;
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.toggleFilterEntry(payload.index(), payload.entry());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(com.sdzjz.net.NodeSensorPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (payload.item().length() > 128 || !viewingCore(p, payload.pos())) return;
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.setSensorConfig(payload.index(), payload.item(), payload.threshold(), payload.less());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(com.sdzjz.net.StorageLinkPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (payload.dim().length() > 128 || !viewingCore(p, payload.pos())) return;
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.toggleStorageEdge(payload.machineIndex(), payload.storagePos(), payload.dir(), payload.dim());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(com.sdzjz.net.StorageNodeMovePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.setStorageNodePos(payload.storagePos(), payload.nx(), payload.ny());
                }
            });
        });
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
        ServerPlayNetworking.registerGlobalReceiver(NodeAddPayload.ID, (payload, context) -> { // m88 机器库侧栏
            ServerPlayerEntity p = context.player();
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (!(p.getWorld().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core)) return;
                var inv = p.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    net.minecraft.item.ItemStack st = inv.getStack(i);
                    if (st.isEmpty()) continue;
                    if (!net.minecraft.registry.Registries.ITEM.getId(st.getItem()).toString().equals(payload.itemId())) continue;
                    boolean ok = st.getItem() instanceof com.sdzjz.item.MachineItem
                            || st.getItem() instanceof com.sdzjz.item.CropFarmItem
                            || (st.getItem() instanceof com.sdzjz.item.CaptureCageItem && com.sdzjz.item.CaptureCageItem.isCaged(st))
                            || StructureCoreBlockEntity.isFilter(st) || StructureCoreBlockEntity.isSensor(st)
                            || StructureCoreBlockEntity.isSwitch(st) || StructureCoreBlockEntity.isDistributor(st);
                    if (!ok) continue;
                    core.insertMachine(st); // 内部 decrement 1 + 同步
                    inv.markDirty();
                    return;
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
