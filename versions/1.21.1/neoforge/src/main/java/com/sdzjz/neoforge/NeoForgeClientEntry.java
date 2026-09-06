package com.sdzjz.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** m536（F1d-2a）NeoForge 客户端入口（只在物理客户端构造：{@code @Mod(dist = Dist.CLIENT)}），对位 Fabric 的 {@code client/FabricClientEntry}。
 *  <p>手法同 m535 服务端分期：{@code SdzjzClient.init()} 拆四段——构造器调 initGfx（世代口）与 initHooksAndNet（接收器/tooltip/tick/键位/世界渲染钩，
 *  全走口）；**initScreens 在 {@code RegisterMenuScreensEvent} 期、initRenderers 在 {@code EntityRenderersEvent.RegisterRenderers} 期被触发**
 *  ——两段实参 {@code ModScreenHandlers.X / ModBlockEntities.X} 会触发注册类初始化，构造器期注册表冻着不能碰；而事件期注册对象一定齐了，
 *  且 {@code ClientHooks.registerScreen/registerBlockEntityRenderer} 直接落进当前事件，不缓冲、与两事件先后无关。
 *  <p>本刀不含：{@code SatelliteNodeModel}（Fabric ModelLoadingPlugin → NeoForge ModelEvent 世代口）与压缩包动态图标
 *  （BuiltinItemRendererRegistry → IClientItemExtensions）——F1d-2b，开工前对文档核名。 */
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

        com.sdzjz.SdzjzClient.initHooksAndNet();
    }
}
