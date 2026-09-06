package com.sdzjz.neoforge;

import com.sdzjz.client.ClientHooks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/** m536（F1d-2a）{@link ClientHooks.Impl} 的 NeoForge 实现——对位 {@code client/FabricClientHooks} 六口：
 *  ClientTickEvents.END_CLIENT_TICK → ClientTickEvent.Post；ItemTooltipCallback → ItemTooltipEvent（游戏总线）；
 *  KeyBindingHelper.registerKeyBinding → 先建 KeyMapping 返回句柄、{@code RegisterKeyMappingsEvent} 期再登记（Fabric 立即注册，NeoForge 只认事件期）；
 *  WorldRenderEvents.AFTER_ENTITIES → RenderLevelStageEvent 的 AFTER_ENTITIES 阶段（m393 那条"只有这一阶段矩阵与顶点口双双非 null"在这边=
 *  PoseStack 来自事件、MultiBufferSource 取渲染缓冲、相机位来自事件相机）；
 *  MenuScreens.register（Fabric access widener 放开的 private）→ {@code RegisterMenuScreensEvent.register}；
 *  BlockEntityRenderers.register → {@code EntityRenderersEvent.RegisterRenderers.registerBlockEntityRenderer}。
 *  后两口**只在对应事件期内有效**（当前事件由 {@link #withScreens}/{@link #withRenderers} 装进静态槽，业务段在里面被触发；期外调=抛，不静默）。 */
public final class NeoForgeClientHooks implements ClientHooks.Impl {

    private RegisterMenuScreensEvent screens;
    private EntityRenderersEvent.RegisterRenderers renderers;
    private final List<KeyMapping> pendingKeys = new ArrayList<>();
    private boolean keysFlushed;

    @Override
    public void onClientTickEnd(java.util.function.Consumer<Minecraft> h) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> h.accept(Minecraft.getInstance()));
    }

    @Override
    public void onItemTooltip(ClientHooks.Tooltip h) {
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> h.append(e.getItemStack(), e.getToolTip()));
    }

    @Override
    public KeyMapping registerKey(String translationKey, int glfwKey, String category) {
        KeyMapping km = new KeyMapping(translationKey, glfwKey, category);
        if (keysFlushed) throw new IllegalStateException("registerKey 必须在 RegisterKeyMappingsEvent 之前调（SdzjzClient.initHooksAndNet 在客户端入口构造器里调）");
        pendingKeys.add(km);
        return km;
    }

    @Override
    public void onWorldDrawAfterEntities(ClientHooks.WorldDraw h) {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            h.draw(e.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(), e.getCamera().getPosition());
        });
    }

    @Override
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>>
    void registerScreen(MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> ctor) {
        if (screens == null) throw new IllegalStateException("registerScreen 只能在 RegisterMenuScreensEvent 期调（NeoForgeClientEntry 在事件里触发 SdzjzClient.initScreens）");
        screens.register(type, ctor);
    }

    @Override
    public <T extends BlockEntity>
    void registerBlockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
        if (renderers == null) throw new IllegalStateException("registerBlockEntityRenderer 只能在 EntityRenderersEvent.RegisterRenderers 期调（NeoForgeClientEntry 在事件里触发 SdzjzClient.initRenderers）");
        renderers.registerBlockEntityRenderer(type, provider);
    }

    /** 事件期内触发业务段：装当前事件 → 跑 → 卸。 */
    void withScreens(RegisterMenuScreensEvent e, Runnable body) {
        screens = e;
        try { body.run(); } finally { screens = null; }
    }

    void withRenderers(EntityRenderersEvent.RegisterRenderers e, Runnable body) {
        renderers = e;
        try { body.run(); } finally { renderers = null; }
    }

    /** 构造器期 registerKey 建好的句柄在这儿登记（NeoForge 只认事件期）。 */
    void flushKeys(RegisterKeyMappingsEvent e) {
        for (KeyMapping km : pendingKeys) e.register(km);
        pendingKeys.clear();
        keysFlushed = true;
    }
}
