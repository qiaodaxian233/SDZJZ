package com.sdzjz;

import com.sdzjz.client.DataPanelScreen;
import com.sdzjz.client.DataCableRenderer;
import com.sdzjz.client.StorageCoreRenderer;
import com.sdzjz.client.StructureCoreScreen;
import com.sdzjz.client.SuperBenchScreen;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModScreenHandlers;

/** 1.21.1 世代客户端初始化（屏注册/BER/客户端接收器）。m531（F1a）起**不再是 Fabric 入口**：加载器入口在 {@code client/FabricClientEntry}
 *  （装 ClientHooks 加载器口、Fabric 专属的内建物品渲染器与模型插件，然后调本类 {@link #init()}）；本类只剩原版 API（MenuScreens/BlockEntityRenderers）与世代口。 */
public class SdzjzClient {
    /** 原 {@code onInitializeClient()} 体，去掉 ClientHooks 安装句、SatelliteNodeModel 模型插件句、两句 BuiltinItemRendererRegistry（Fabric 专属，挪 FabricClientEntry）；
     *  BlockEntityRendererRegistry.register → 原版 BlockEntityRenderers.register（Fabric 那个就是它的转调）。 */
    public static void init() {
        initGfx();
        initScreens();
        initRenderers();
        initHooksAndNet();
    }

    /** m536（F1d-2a）拆四段——语句与顺序**逐位不变**，Fabric 仍调 {@link #init()}；NeoForge 客户端入口：构造器调 initGfx / initHooksAndNet，
     *  {@code RegisterMenuScreensEvent} 期调 initScreens、{@code EntityRenderersEvent.RegisterRenderers} 期调 initRenderers
     *  （两段实参 ModScreenHandlers.X / ModBlockEntities.X 会触发注册类初始化，构造器期注册表冻着不能碰——与 m535 服务端分期同一条理）。 */
    public static void initGfx() {
        com.sdzjz.client.SciSkin.installGfx(new com.sdzjz.client.LegacySkinGfx()); // m483 卡面工艺世代口（绞杀者第六刀）：早于一切屏注册
    }

    /** 第二段：六张菜单屏（ClientHooks 第五口）。 */
    public static void initScreens() {
        // m433 ClientNet 平台口安装句 m535（F1d）挪 FabricClientEntry（加载器胶水引用不能留在业务入口，NeoForge 编不过）；仍早于下方一切客户端接收器挂接
        com.sdzjz.client.ClientHooks.registerScreen(ModScreenHandlers.STRUCTURE_CORE, StructureCoreScreen::new); // m535b：六句 MenuScreens.register 改走 ClientHooks 第五口（NeoForge 上该方法 private）
        com.sdzjz.client.ClientHooks.registerScreen(ModScreenHandlers.DATA_PANEL, DataPanelScreen::new);
        com.sdzjz.client.ClientHooks.registerScreen(ModScreenHandlers.TRADE_CENTER, com.sdzjz.client.TradeCenterScreen::new);
        com.sdzjz.client.ClientHooks.registerScreen(ModScreenHandlers.SUPER_BENCH, SuperBenchScreen::new);
        com.sdzjz.client.ClientHooks.registerScreen(ModScreenHandlers.EXTRACT_PORT, com.sdzjz.client.ExtractPortScreen::new); // m226 抽取口配置
        com.sdzjz.client.ClientHooks.registerScreen(ModScreenHandlers.PORTABLE_VAULT, com.sdzjz.client.PortableVaultScreen::new); // m312 随身仓库
    }

    /** 第三段：四个方块实体渲染器（ClientHooks 第六口）。 */
    public static void initRenderers() {
        com.sdzjz.client.ClientHooks.registerBlockEntityRenderer(ModBlockEntities.STORAGE_CORE_BE, StorageCoreRenderer::new); // 存储核心动画
        com.sdzjz.client.ClientHooks.registerBlockEntityRenderer(ModBlockEntities.DATA_CABLE_BE, DataCableRenderer::new); // 数据线能量脉冲
        com.sdzjz.client.ClientHooks.registerBlockEntityRenderer(ModBlockEntities.WIRELESS_NODE_BE, com.sdzjz.client.WirelessNodeRenderer::new); // 无线节点信号波
        com.sdzjz.client.ClientHooks.registerBlockEntityRenderer(ModBlockEntities.SATELLITE_NODE_BE, com.sdzjz.client.SatelliteNodeRenderer::new); // m156 卫星扫描动画
    }

    /** 第四段：客户端接收器 + tooltip/tick/键位/世界渲染钩（全走口，构造器期可调：lambda 体里的引用运行期才求值）。 */
    public static void initHooksAndNet() {
        // m277 三块动画改原生贴图帧动画（.png.mcmeta，docs/tools_block_anim.py 生成）——m249/m250 全息 BER 三件套退役
        // m243 压缩包动态图标两句（Fabric BuiltinItemRendererRegistry）m531 挪 FabricClientEntry；NeoForge 对位 IClientItemExtensions（F1d）
        // m89：画布端点直发包 → 静态缓存（画布优先读缓存，BE 数据后备）
        com.sdzjz.client.ClientNet.onClient(com.sdzjz.net.CanvasEndsPayload.ID,
                (payload, client) -> com.sdzjz.client.StructureCoreScreen.applyEndsPayload(payload));
        com.sdzjz.client.ClientNet.onClient(com.sdzjz.net.StorageNodeHomePayload.ID, // m265 端点画布落位
                (payload, client) -> com.sdzjz.client.StructureCoreScreen.applyHomesPayload(payload));
        // m275：观众定向渲染快照 → 写回客户端 BE 渲染字段（画布屏 be() 读法零改动）
        com.sdzjz.client.ClientNet.onClient(com.sdzjz.net.CanvasSnapshotPayload.ID,
                (payload, client) -> {
                    var w = client.level;
                    if (w != null && w.getBlockEntity(payload.pos()) instanceof com.sdzjz.block.StructureCoreBlockEntity be)
                        be.applyRenderSnapshot(payload.nbt(), w.registryAccess());
                });
        // m289：终端库存摘要 → 灌进正开着的终端 handler 并催书重算"可合成"
        com.sdzjz.client.ClientNet.onClient(com.sdzjz.net.TerminalStockPayload.ID,
                (payload, client) -> {
                    var pl = client.player;
                    if (pl != null && pl.containerMenu instanceof com.sdzjz.screen.DataPanelScreenHandler h
                            && h.containerId == payload.syncId()) {
                        h.applyStock(payload.ids(), payload.counts(), payload.truncated()); // m298
                        if (client.screen instanceof com.sdzjz.client.DataPanelScreen ds)
                            ds.onStockSync();
                    }
                });
        // m80：全模组物品 tooltip 水印
        com.sdzjz.client.ClientHooks.onItemTooltip((stack, lines) -> { // m405 平台口
            if ("sdzjz".equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()))
                lines.add(net.minecraft.network.chat.Component.literal("DY：乔大仙").withStyle(net.minecraft.ChatFormatting.GOLD));
        });
        // m320：Sodium"仅动画可见纹理"优化会冻结纯 GUI 物品动画精灵（方块精灵靠世界渲染保活不受累）
        // ——每客户端 tick 给四件动画物品精灵标活跃；未装 Sodium 垫片自动熔断零开销。
        com.sdzjz.client.ClientHooks.onClientTickEnd(com.sdzjz.client.SodiumSpriteKicker::tick); // m405 平台口
        // m384 选区高亮：手持已绑定移除器=世界内紫色能量框罩住选区（"技能选中"圈）
        com.sdzjz.client.ChunkRegionHighlighter.register();
        // m386 手持设置面板快捷键（默认 R 可改键位）：手持移除器时开屏，否则无事
        var chunkCfgKey = com.sdzjz.client.ClientHooks.registerKey("key.sdzjz.chunk_config",
                org.lwjgl.glfw.GLFW.GLFW_KEY_R, "category.sdzjz"); // m405 平台口
        com.sdzjz.client.ClientHooks.onClientTickEnd(mc -> {
            while (chunkCfgKey.consumeClick()) {
                if (mc.player == null || mc.screen != null) continue;
                int handK = mc.player.getMainHandItem().getItem() instanceof com.sdzjz.item.ChunkRemoverItem ? 0
                        : mc.player.getOffhandItem().getItem() instanceof com.sdzjz.item.ChunkRemoverItem ? 1 : -1;
                if (handK >= 0) mc.setScreen(new com.sdzjz.client.ChunkRemoverConfigScreen(handK));
            }
        });
        // m538 作者视频评论「贴图太高清/和原版差太多/有些跳脱」：内置可选资源包「像素风」（物品 32×、方块 16×，docs/tools_pixelate.py 由默认高清贴图生成，
        // 第 23 闸防漂移）；默认仍是作者高清原图，玩家在「资源包」里一键切。要改默认启用：Fabric 壳 NORMAL→DEFAULT_ENABLED / NeoForge alwaysActive 或 Position，一处一词。
        com.sdzjz.client.ClientHooks.registerBuiltinResourcePack("pixel", "生电终结者 · 像素风（物品 32× / 方块 16×，贴近原版）");
        Sdzjz.LOGGER.info("[生电终结者] 客户端已加载：结构核心画布 + 超大工作台 GUI 已注册。");
    }
}
