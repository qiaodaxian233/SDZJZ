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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sdzjz implements ModInitializer {
    public static final String MOD_ID = "sdzjz";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        com.sdzjz.net.Net.install(new com.sdzjz.loader.FabricNet()); // m433 平台口安装：必须早于下方一切 payload 注册/接收器挂接
        com.sdzjz.platform.Platform.initConfigDir(com.sdzjz.loader.Env.configDir()); // m365 必须第一行（早于任何 SdzjzConfig.get()）；m405 走环境口
        com.sdzjz.platform.Platform.initRecipes(new com.sdzjz.legacy.LegacyRecipeAccess()); // m362 代际引导：Legacy 配方 SPI 最早注册（Common 侧 planner 依赖）
        com.sdzjz.debug.SdzjzCommands.register(); // m177 /sdzjz profile|dumpgraph
        com.sdzjz.debug.BenchRunner.init(); // m306 一键压测（IDLE 时 tick 零开销）

        // m332 随身仓库专属仓位：仓位不在 PlayerInventory，原版 inventoryTick 轮不到——服务端 tick 钩
        // 与背包同拍（每 10t）代跑吸附；开关关或格空=每 0.5s 一次空遍历在线表，开销可忽略。
        com.sdzjz.loader.Hooks.onServerTickEnd(server -> { // m405 平台口
            for (net.minecraft.server.level.ServerLevel w : server.getAllLevels())
                com.sdzjz.block.CoreChunkLoading.reconcileTick(w); // m347 孤儿声明渐进核销（宽限/节拍/开关在里头把门）
            if (!SdzjzConfig.get().portableVaultSlot || server.overworld().getGameTime() % 10 != 0) return;
            for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                net.minecraft.world.item.ItemStack v = com.sdzjz.item.PortableVaultSlot.stackOf(sp);
                if (!v.isEmpty()) com.sdzjz.item.PortableVaultItem.magnetTick(v, sp);
            }
        });
        SdzjzConfig.load();
        ModBlocks.init();
        ModBlockEntities.init();
        ModScreenHandlers.init();
        ModItems.init();

        // m161c 跨模组直连：存储核心双账本挂上 Fabric Transfer API——Create/Modern Industrialization/
        // Tech Reborn/AE2 等一切走 fabric-transfer-api 的管道怼在存储核心任意面即可存取。
        // 注意原版漏斗不走此 API（漏斗只认 Inventory 接口），漏斗对接另开里程碑（见 DEVLOG m161）。
        // m404 提供侧（我们把自家账本暴露给别的模组）：天生属加载器层，不抽口——
        // 换 NeoForge 时这里换成能力注册，业务侧一行不动。
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> be.fabricStorage(), ModBlockEntities.STORAGE_CORE_BE);

        // m94：抓物笼捕获改走实体交互事件——抢在 entity.interact() 之前触发，
        // 否则村民（交易界面）/马（骑乘）/驯服猫狗（坐下）等自带右键交互的生物会把捕获整个截胡，
        // useOnEntity 永远轮不到执行（僵尸/骷髅这类无交互生物不受影响，两条路都通）。
        // 返回 SUCCESS 即取消原版后续处理（交易界面不弹）；PASS 时一切照旧。
        com.sdzjz.loader.Hooks.onUseEntity((player, hand, entity) -> { // m405 平台口
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return net.minecraft.world.InteractionResult.PASS;
            if (!(player.getItemInHand(hand).getItem() instanceof com.sdzjz.item.CaptureCageItem)) return net.minecraft.world.InteractionResult.PASS;
            return com.sdzjz.item.CaptureCageItem.tryCapture(player, hand, living);
        });

        // 服务器停止时清空存储核心登记表（防跨存档幽灵坐标）
        // m296 开服/维度载入重发核心无期票（声明表自举，复刻 ForcedChunkState；治"有期票不落盘=重启死锁"）
        com.sdzjz.loader.Hooks.onWorldLoad((server, world) -> com.sdzjz.block.CoreChunkLoading.restoreClaims(world));
        com.sdzjz.loader.Hooks.onPlayerDisconnect(sp -> WRITE_BUDGET.remove(sp.getUUID())); // m294 下线清预算条目
        com.sdzjz.loader.Hooks.onServerStopped(server -> {
            WRITE_BUDGET.clear(); // m294 停服清空（单机反复进出存档不留残）
            StorageCoreBlockEntity.clearAll();
            com.sdzjz.block.CoreChunkLoading.clearAll(); // m133 强加载登记表（m296 起自恢复靠声明表 PersistentState+开服重发票）
            com.sdzjz.machine.CoreScheduler.clearAll(); // m302 全服预算/饥饿名单清态
            com.sdzjz.debug.BenchRunner.reset(); // m306 压测状态机复位
            CraftPlanner.clearCache();
            com.sdzjz.machine.BrewPlanner.clearCache();
            com.sdzjz.machine.EnchantPlanner.clearCache();
            com.sdzjz.machine.SmeltPlanner.clearCache();
            com.sdzjz.legacy.LegacyRecipeAccess.clearCaches(); // m364 解析层材料缓存同拍失效
        });

        // 网络：画布节点拖动位置 + 连线（C2S）
        com.sdzjz.net.Net.c2s(NodeMovePayload.ID, NodeMovePayload.CODEC);
        com.sdzjz.net.Net.c2s(NodeLinkPayload.ID, NodeLinkPayload.CODEC);
        com.sdzjz.net.Net.c2s(NodeUpgradePayload.ID, NodeUpgradePayload.CODEC);
        com.sdzjz.net.Net.c2s(NodeTargetPayload.ID, NodeTargetPayload.CODEC);
        com.sdzjz.net.Net.c2s(NodeRemovePayload.ID, NodeRemovePayload.CODEC);
        com.sdzjz.net.Net.c2s(NodeAddPayload.ID, NodeAddPayload.CODEC);
        com.sdzjz.net.Net.c2s(com.sdzjz.net.VaultTakePayload.ID, com.sdzjz.net.VaultTakePayload.CODEC); // m312
        com.sdzjz.net.Net.s2c(com.sdzjz.net.CanvasEndsPayload.ID, com.sdzjz.net.CanvasEndsPayload.CODEC); // m89
        com.sdzjz.net.Net.s2c(com.sdzjz.net.TerminalStockPayload.ID, com.sdzjz.net.TerminalStockPayload.CODEC); // m289 终端库存摘要→配方书
        com.sdzjz.net.Net.s2c(com.sdzjz.net.StorageNodeHomePayload.ID, com.sdzjz.net.StorageNodeHomePayload.CODEC); // m265 端点画布落位（CanvasEnds 姊妹包）
        com.sdzjz.net.Net.s2c(com.sdzjz.net.CanvasSnapshotPayload.ID, com.sdzjz.net.CanvasSnapshotPayload.CODEC); // m275 观众定向渲染快照（审计第3条：取代 vanilla 全量 NBT 区块广播）
        com.sdzjz.net.Net.c2s(DataPanelViewPayload.ID, DataPanelViewPayload.CODEC);
        com.sdzjz.net.Net.c2s(com.sdzjz.net.StorageLinkPayload.ID, com.sdzjz.net.StorageLinkPayload.CODEC);
        com.sdzjz.net.Net.c2s(com.sdzjz.net.StorageNodeMovePayload.ID, com.sdzjz.net.StorageNodeMovePayload.CODEC);
        com.sdzjz.net.Net.c2s(com.sdzjz.net.NodeFilterPayload.ID, com.sdzjz.net.NodeFilterPayload.CODEC);
        com.sdzjz.net.Net.c2s(com.sdzjz.net.NodeSensorPayload.ID, com.sdzjz.net.NodeSensorPayload.CODEC);
        com.sdzjz.net.Net.c2s(com.sdzjz.net.NodeSwitchPayload.ID, com.sdzjz.net.NodeSwitchPayload.CODEC);
        com.sdzjz.net.Net.c2s(com.sdzjz.net.ChunkRemoverConfigPayload.ID, com.sdzjz.net.ChunkRemoverConfigPayload.CODEC); // m386
        com.sdzjz.net.Net.c2s(com.sdzjz.net.NodeGroupPayload.ID, com.sdzjz.net.NodeGroupPayload.CODEC); // m191 画布打组
        com.sdzjz.net.Net.c2s(com.sdzjz.net.NodeGroupMovePayload.ID, com.sdzjz.net.NodeGroupMovePayload.CODEC); // m191 组整体位移
        com.sdzjz.net.Net.onServer(com.sdzjz.net.NodeGroupPayload.ID, (payload, p) -> { // m191：一包三义按字段组合分派（建组/重命名/解散）
            p.getServer().execute(() -> {
                if (!SdzjzConfig.get().canvasGroupsEnabled) return;               // 总开关把门
                if (payload.name().length() > 64 || payload.members().size() > 512) return; // 伪造包尺寸熔断（正常组远小于此）
                if (!viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    if (payload.gid() < 0) core.createGroup(payload.members(), payload.name());
                    else if (!payload.name().isEmpty()) core.renameGroup(payload.gid(), payload.name());
                    else core.dissolveGroup(payload.gid());
                }
            });
        });
        com.sdzjz.net.Net.onServer(com.sdzjz.net.NodeGroupMovePayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!SdzjzConfig.get().canvasGroupsEnabled) return;
                if (!viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.moveGroup(payload.gid(), payload.dx(), payload.dy());
                }
            });
        });
        com.sdzjz.net.Net.onServer(com.sdzjz.net.NodeSwitchPayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.toggleSwitch(payload.index());
                }
            });
        });
        com.sdzjz.net.Net.c2s(com.sdzjz.net.NodeFusePayload.ID, com.sdzjz.net.NodeFusePayload.CODEC); // m123
        com.sdzjz.net.Net.onServer(com.sdzjz.net.NodeFusePayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.fuseNode(p, payload.index(), payload.up());
                }
            });
        });
        com.sdzjz.net.Net.c2s(com.sdzjz.net.NodePausePayload.ID, com.sdzjz.net.NodePausePayload.CODEC); // m110b
        com.sdzjz.net.Net.c2s(com.sdzjz.net.JeiFillPayload.ID, com.sdzjz.net.JeiFillPayload.CODEC); // m212
        com.sdzjz.net.Net.onServer(com.sdzjz.net.NodePausePayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.togglePause(payload.index());
                }
            });
        });
        com.sdzjz.net.Net.onServer(com.sdzjz.net.JeiFillPayload.ID, (payload, p) -> { // m212 JEI"+"填料：服务端权威取料（仓储优先、背包兜底）
            p.getServer().execute(() -> {
                if (!viewingPanel(p, payload.pos())) return; // 资格：面板开着且坐标对上（伪造包直接丢弃）
                if (p.containerMenu instanceof DataPanelScreenHandler h)
                    h.jeiFill(p, payload.recipeId(), payload.max());
            });
        });
        com.sdzjz.net.Net.onServer(com.sdzjz.net.NodeFilterPayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (payload.entry().length() > 128 || !viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.toggleFilterEntry(payload.index(), payload.entry());
                }
            });
        });
        com.sdzjz.net.Net.onServer(com.sdzjz.net.ChunkRemoverConfigPayload.ID, (payload, p) -> { // m386 手持设置面板写包：先验手上确为移除器再落 NBT，半径变更=重扫（#zrd 同口径）
            p.getServer().execute(() -> {
                var h = payload.hand() == 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND;
                var s2 = p.getItemInHand(h);
                if (!(s2.getItem() instanceof com.sdzjz.item.ChunkRemoverItem)) return;
                int capR = Math.max(0, SdzjzConfig.get().chunkRemoverMaxRadius);
                int r2 = Math.max(0, Math.min(payload.radius(), capR));
                int m2 = Math.max(0, Math.min(2, payload.mode())); // m397 三挡
                if (m2 == 2 && !SdzjzConfig.get().chunkRemoverVoidMode) m2 = 1; // 服主关了空置域=按无掉落收，节点侧不留假挡
                int w2 = payload.seal() == 1 ? 1 : 0; // m388 封边挡水
                var n2 = s2.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                boolean rCh = n2.getInt("zr") != r2;
                boolean wOn = w2 == 1 && n2.getInt("zw") == 2; // m388 开堵水=重扫补封（已挖开的边界回补石墙、灌进来的水按普通块清掉；关堵水不动游标）。m394 三态：只有"原为显式关"才算开沿
                n2.putInt("zr", r2);
                n2.putInt("zm", m2);
                n2.putInt("zw", w2 == 1 ? 1 : 2); // m394 线上仍是 0/1，落盘转三态（2=显式关；缺省 0 = 开）
                if (rCh || wOn) { // 改区域/开堵水=新工程重扫（zn 总账保留）
                    n2.putInt("zy", p.level().getMaxBuildHeight() - 1);
                    n2.putInt("zi", 0);
                    n2.putInt("zc", 0);
                    n2.remove("zf");
                    n2.remove("zq"); // m390 湿账随新工程归零
                }
                s2.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(n2));
            });
        });
        com.sdzjz.net.Net.onServer(com.sdzjz.net.NodeSensorPayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (payload.item().length() > 128 || !viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.setSensorConfig(payload.index(), payload.item(), payload.threshold(), payload.less());
                }
            });
        });
        com.sdzjz.net.Net.onServer(com.sdzjz.net.StorageLinkPayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (payload.dim().length() > 128 || !viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.toggleStorageEdge(p, payload.machineIndex(), payload.storagePos(), payload.dir(), payload.dim()); // m270 带玩家
                }
            });
        });
        com.sdzjz.net.Net.onServer(com.sdzjz.net.StorageNodeMovePayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    if (payload.dock()) core.dockStorageNode(payload.storagePos()); // m265 收回总线
                    else core.setStorageNodePos(payload.storagePos(), payload.nx(), payload.ny()); // m265 放置/移动（服务端钳幅）
                }
            });
        });
        com.sdzjz.net.Net.onServer(NodeMovePayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return; // 防伪造包操纵任意坐标的核心
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.setNodePos(payload.index(), payload.nx(), payload.ny());
                }
            });
        });
        com.sdzjz.net.Net.onServer(NodeLinkPayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.toggleConnection(p, payload.from(), payload.to()); // m270 带玩家
                }
            });
        });
        com.sdzjz.net.Net.onServer(NodeUpgradePayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    int n = Math.max(1, Math.min(64, payload.count())); // m115a 批量：服务端钳幅，逐个到失败即停
                    boolean any = false; // m128(F3)：循环走 Raw 无同步内核，结束一次 syncNow——此前 64 连发=一 tick 64 次全量 BE 同步瞬卡
                    for (int k = 0; k < n; k++) {
                        boolean ok = payload.add() ? core.addNodeUpgradeRaw(p, payload.index(), payload.kind())
                                                   : core.removeNodeUpgradeRaw(p, payload.index(), payload.kind());
                        if (!ok) break;
                        any = true;
                    }
                    if (any) core.syncNow();
                }
            });
        });
        com.sdzjz.net.Net.onServer(NodeTargetPayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (payload.target().length() > 128 || !viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.setNodeTarget(payload.index(), payload.target());
                }
            });
        });
        com.sdzjz.net.Net.onServer(com.sdzjz.net.VaultTakePayload.ID, (payload, p) -> { // m312 随身仓库取物
            p.getServer().execute(() -> {
                if (writeBudget(p) && p.containerMenu instanceof com.sdzjz.screen.PortableVaultScreenHandler h) // m329：补 m269 写包预算——16 接收器唯一漏点（取物触发背包同步回包，洪泛=放大器）
                    h.take(p, payload.itemId(), payload.mode());
            });
        });
        com.sdzjz.net.Net.onServer(NodeAddPayload.ID, (payload, p) -> { // m88 机器库侧栏
            p.getServer().execute(() -> {
                if (payload.itemId().length() > 128 || !viewingCore(p, payload.pos())) return; // m269 长度闸与其余字符串包对齐
                if (!(p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core)) return;
                var inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack st = inv.getItem(i);
                    if (st.isEmpty()) continue;
                    if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(payload.itemId())) continue;
                    boolean ok = st.getItem() instanceof com.sdzjz.item.MachineItem
                            || st.getItem() instanceof com.sdzjz.item.CropFarmItem
                            || (st.getItem() instanceof com.sdzjz.item.CaptureCageItem && com.sdzjz.item.CaptureCageItem.isCaged(st))
                            || com.sdzjz.node.NodeTags.isFilter(st) || com.sdzjz.node.NodeTags.isSensor(st)
                            || com.sdzjz.node.NodeTags.isSwitch(st) || com.sdzjz.node.NodeTags.isDistributor(st);
                    if (!ok) continue;
                    core.insertMachine(p, st); // 内部 decrement 1 + 同步；m270 带玩家
                    inv.setChanged();
                    return;
                }
            });
        });
        com.sdzjz.net.Net.onServer(NodeRemovePayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!viewingCore(p, payload.pos())) return;
                if (p.level().getBlockEntity(payload.pos()) instanceof StructureCoreBlockEntity core) {
                    core.removeNodeAt(p, payload.index());
                }
            });
        });
        com.sdzjz.net.Net.onServer(DataPanelViewPayload.ID, (payload, p) -> {
            p.getServer().execute(() -> {
                if (!viewingPanel(p, payload.pos())) return; // 校验走界面而非距离——手持终端可远程开面板
                if (p.containerMenu instanceof com.sdzjz.screen.DataPanelScreenHandler h) {
                    h.setView(payload.search(), payload.scrollRow(), payload.matchedIds()); // m292 视图归玩家自己的 handler
                }
            });
        });

        LOGGER.info("[生电终结者] 已加载：结构核心画布 + 机器 + 升级 + 连接系统。");
    }

    /** m269 每玩家每 tick C2S 写包预算（外部审计"速率限制"）：全部画布/面板写包都触发
     *  markDirty+syncToClient 全量同步，伪造包洪泛=同步风暴打卡主线程。正常 UI 交互每 tick
     *  至多几包，默认预算 40 绝不伤手感；超限静默丢弃（客户端下一次交互重发即生效）。
     *  服务端主线程内调用（各接收器 execute 里），无并发问题；m294 起下线即清、停服清空（此前"残留可忽略"的判断已过时）。 */
    private static final java.util.HashMap<java.util.UUID, long[]> WRITE_BUDGET = new java.util.HashMap<>();
    private static boolean writeBudget(ServerPlayer p) {
        int cap = SdzjzConfig.get().packetWriteBudgetPerTick;
        if (cap <= 0) return true; // 0=关闭护栏
        long tick = p.level().getGameTime();
        long[] e = WRITE_BUDGET.computeIfAbsent(p.getUUID(), k -> new long[]{Long.MIN_VALUE, 0});
        if (e[0] != tick) { e[0] = tick; e[1] = 0; }
        return ++e[1] <= cap;
    }

    /** 统一入包闸（资格+预算）：玩家当前打开的是不是该坐标的结构核心画布。全部画布类 C2S 接收器走此口。 */
    private static boolean viewingCore(ServerPlayer p, net.minecraft.core.BlockPos pos) {
        return writeBudget(p) && p.containerMenu instanceof StructureCoreScreenHandler h && pos.equals(h.blockPos());
    }

    /** 统一入包闸（资格+预算）：玩家当前打开的是不是该坐标的数据面板（含手持终端远程打开）。 */
    private static boolean viewingPanel(ServerPlayer p, net.minecraft.core.BlockPos pos) {
        return writeBudget(p) && p.containerMenu instanceof DataPanelScreenHandler h && pos.equals(h.blockPos());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
