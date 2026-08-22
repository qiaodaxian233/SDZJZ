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

    /** 加载器要给的四个口（m435）：语义见各静态门面注释。 */
    public interface Impl {
        void onClientTickEnd(java.util.function.Consumer<Minecraft> h);
        void onItemTooltip(Tooltip h);
        net.minecraft.client.KeyMapping registerKey(String translationKey, int glfwKey, String category);
        void onWorldDrawAfterEntities(WorldDraw h);
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
}
