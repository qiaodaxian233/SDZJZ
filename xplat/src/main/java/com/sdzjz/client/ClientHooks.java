package com.sdzjz.client;

import net.minecraft.client.Minecraft;

/**
 * m405 客户端事件/键位平台口——与 {@link com.sdzjz.loader.Hooks} 成对，客户端专属那半边
 * （tick 钩、tooltip 钩、键位注册、世界渲染钩）。刻意不塞进 `loader/Hooks`：
 * 专用服务端不该因为一次类加载去解析 Minecraft（m402 同一条边界）。
 *
 * <p><b>m435 接口化（漏斗销账收官）</b>：门面迁 xplat，Fabric 内脏（含 m393"只有实体画完
 * 之后那阶段 matrixStack 与 consumers 双双非 null"的判空守卫）原样进 client/FabricClientHooks，
 * SdzjzClient 首段安装。换 NeoForge 时给 {@link Impl} 换一份实现即可：Neo 侧对应
 * {@code ClientTickEvent}/{@code ItemTooltipEvent}/{@code RegisterKeyMappingsEvent}/{@code RenderLevelStageEvent}。
 */
public final class ClientHooks {

    private ClientHooks() { }

    /** 物品悬浮文本追加（m80 全模组水印）。 */
    @FunctionalInterface
    public interface Tooltip {
        void append(net.minecraft.world.item.ItemStack stack, java.util.List<net.minecraft.network.chat.Component> lines);
    }

    /** 世界渲染钩：**实体画完之后**那一阶段（m393 血泪——只有这一阶段 matrixStack 与 consumers 双双非 null，
     *  详见 ChunkRegionHighlighter 类注释）。业务侧只拿到三样原版东西：矩阵栈、顶点缓冲口、相机位置。 */
    @FunctionalInterface
    public interface WorldDraw {
        void draw(com.mojang.blaze3d.vertex.PoseStack matrices,
                  net.minecraft.client.renderer.MultiBufferSource consumers,
                  net.minecraft.world.phys.Vec3 cameraPos);
    }

    /** 加载器要给的六个口（m435 四口 + m535b 菜单屏注册 + m536 方块实体渲染器注册）：语义见各静态门面注释。 */
    public interface Impl {
        void onClientTickEnd(java.util.function.Consumer<Minecraft> h);
        void onItemTooltip(Tooltip h);
        net.minecraft.client.KeyMapping registerKey(String translationKey, int glfwKey, String category);
        void onWorldDrawAfterEntities(WorldDraw h);
        /** m535b（F1d-1 热修）第五口：菜单屏注册。原版 {@code MenuScreens.register} 是 private——Fabric API 用 access widener 放开了它
         *  （所以主线一直直调），NeoForge 没放开、要走 {@code RegisterMenuScreensEvent}（CI 红：has private access in MenuScreens）。
         *  Fabric 实现=原句一行；NeoForge 实现（F1d-2）=缓冲到事件里 {@code event.register(type, ctor)}。泛型与原版签名逐位一致，调用点方法引用推断不变。 */
        <M extends net.minecraft.world.inventory.AbstractContainerMenu, U extends net.minecraft.client.gui.screens.Screen & net.minecraft.client.gui.screens.inventory.MenuAccess<M>>
        void registerScreen(net.minecraft.world.inventory.MenuType<? extends M> type, net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<M, U> ctor);
        /** m536（F1d-2a）第六口：方块实体渲染器注册。原版 {@code BlockEntityRenderers.register} 两家都 public，但 NeoForge 该在
         *  {@code EntityRenderersEvent.RegisterRenderers} 期注册（时序归事件管）；Fabric 实现=原句一行。泛型与原版签名逐位一致。 */
        <T extends net.minecraft.world.level.block.entity.BlockEntity>
        void registerBlockEntityRenderer(net.minecraft.world.level.block.entity.BlockEntityType<? extends T> type,
                                         net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider<T> provider);
    }

    private static Impl impl;

    /** 客户端入口首段调（重复安装直接炸出来）。 */
    public static void install(Impl i) {
        if (impl != null) throw new IllegalStateException("ClientHooks 平台实现重复安装");
        impl = i;
    }

    private static Impl req() {
        if (impl == null) throw new IllegalStateException("ClientHooks 平台实现未安装：客户端入口须先调 ClientHooks.install(...)（Fabric=SdzjzClient.onInitializeClient 首段）");
        return impl;
    }

    /** 每客户端 tick 末尾。 */
    public static void onClientTickEnd(java.util.function.Consumer<Minecraft> h) { req().onClientTickEnd(h); }

    public static void onItemTooltip(Tooltip h) { req().onItemTooltip(h); }

    /** 注册键位并返回句柄；`wasPressed()` 轮询照旧由业务侧做（原版类型，可移植）。 */
    public static net.minecraft.client.KeyMapping registerKey(String translationKey, int glfwKey, String category) {
        return req().registerKey(translationKey, glfwKey, category);
    }

    public static void onWorldDrawAfterEntities(WorldDraw h) { req().onWorldDrawAfterEntities(h); }
    /** 菜单屏注册（m535b，见 Impl 注）。 */
    public static <M extends net.minecraft.world.inventory.AbstractContainerMenu, U extends net.minecraft.client.gui.screens.Screen & net.minecraft.client.gui.screens.inventory.MenuAccess<M>>
    void registerScreen(net.minecraft.world.inventory.MenuType<? extends M> type, net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<M, U> ctor) {
        req().registerScreen(type, ctor);
    }
    /** 方块实体渲染器注册（m536，见 Impl 注）。 */
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity>
    void registerBlockEntityRenderer(net.minecraft.world.level.block.entity.BlockEntityType<? extends T> type,
                                     net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider<T> provider) {
        req().registerBlockEntityRenderer(type, provider);
    }
}
