package com.sdzjz.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** m536（F1d-2a）NeoForge 客户端入口（只在物理客户端构造：{@code @Mod(dist = Dist.CLIENT)}），对位 Fabric 的 {@code client/FabricClientEntry}。
 *  <p>手法同 m535 服务端分期：{@code SdzjzClient.init()} 拆四段——构造器调 initGfx（世代口）与 initHooksAndNet（接收器/tooltip/tick/键位/世界渲染钩，
 *  全走口）；**initScreens 在 {@code RegisterMenuScreensEvent} 期、initRenderers 在 {@code EntityRenderersEvent.RegisterRenderers} 期被触发**
 *  ——两段实参 {@code ModScreenHandlers.X / ModBlockEntities.X} 会触发注册类初始化，构造器期注册表冻着不能碰；而事件期注册对象一定齐了，
 *  且 {@code ClientHooks.registerScreen/registerBlockEntityRenderer} 直接落进当前事件，不缓冲、与两事件先后无关。
 *  <p>m537（F1d-2b）补两件：①卫星节点自定义模型——Fabric 用 ModelLoadingPlugin 在**加载期**把文件模型 {@code sdzjz:block/satellite_node} 换成
 *  {@code SatelliteNodeModel}（UnbakedModel）；NeoForge 没有加载期替换口，改在 {@link ModelEvent.ModifyBakingResult}（**烘后**）用同一份
 *  {@code SatelliteNodeModel.loadShell().bake(...)} 自烘一个替换掉 blockstate 那一键 {@code sdzjz:satellite_node#}（物品模型是独立的 item/generated 不动）；
 *  bake 只吃 textureGetter（baker/state 形参本体不碰，传 null / X0_Y0）。②压缩包动态图标：BuiltinItemRendererRegistry → {@link RegisterClientExtensionsEvent}
 *  的 {@link IClientItemExtensions#getCustomRenderer()}，渲染器壳 {@link NeoForgeCompressedPackRenderer}。 */
@Mod(value = NeoForgeEntry.MODID, dist = Dist.CLIENT)
public final class NeoForgeClientEntry {
    public NeoForgeClientEntry(IEventBus modBus, ModContainer modContainer) {
        NeoForgeClientHooks hooks = new NeoForgeClientHooks();
        com.sdzjz.client.ClientHooks.install(hooks); // m435 平台口
        com.sdzjz.client.ClientNet.install(new NeoForgeClientNet()); // m433 平台口
        NeoForgeNet.installClientDispatch(NeoForgeClientNet::dispatch); // s2c 包 → 客户端接收器表（NeoForgeNet 零客户端类引用，靶点由这里装）

        com.sdzjz.SdzjzClient.initGfx();

        modBus.addListener((RegisterMenuScreensEvent e) -> hooks.withScreens(e, com.sdzjz.SdzjzClient::initScreens));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> hooks.withRenderers(e, com.sdzjz.SdzjzClient::initRenderers));
        modBus.addListener((RegisterKeyMappingsEvent e) -> hooks.flushKeys(e));
        modBus.addListener(NeoForgeClientEntry::onModifyBakingResult); // m537：卫星节点模型替换（Fabric 侧=FabricSatelliteModel 加载期拦截）
        modBus.addListener(NeoForgeClientEntry::onRegisterClientExtensions); // m537：压缩包×2 动态图标（Fabric 侧=FabricClientEntry 两句 BuiltinItemRendererRegistry）

        com.sdzjz.SdzjzClient.initHooksAndNet();
    }

    private static final ModelResourceLocation SATELLITE_BLOCK = new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(NeoForgeEntry.MODID, "satellite_node"), ""); // blockstate 唯一变体 ""

    /** 烘后替换：只换 blockstate 那一键；geo 读失败=保留原（JSON cube_all）不炸游戏，与 Fabric 壳同律。 */
    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        UnbakedModel shell = com.sdzjz.client.SatelliteNodeModel.loadShell();
        if (shell == null) return;
        BakedModel baked = shell.bake(null, event.getTextureGetter(), BlockModelRotation.X0_Y0); // SatelliteNodeModel.bake 只用 textureGetter
        if (baked != null) event.getModels().put(SATELLITE_BLOCK, baked);
    }

    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private final BlockEntityWithoutLevelRenderer r = new NeoForgeCompressedPackRenderer(com.sdzjz.registry.ModItems.COMPRESSED_PACK_FRAME);
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return r; }
        }, com.sdzjz.registry.ModItems.COMPRESSED_PACK);
        event.registerItem(new IClientItemExtensions() {
            private final BlockEntityWithoutLevelRenderer r = new NeoForgeCompressedPackRenderer(com.sdzjz.registry.ModItems.SUPER_COMPRESSED_PACK_FRAME);
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return r; }
        }, com.sdzjz.registry.ModItems.SUPER_COMPRESSED_PACK);
    }
}
