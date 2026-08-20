package com.sdzjz.client;

import net.minecraft.client.MinecraftClient;

/**
 * m405 客户端事件/键位平台口——与 {@link com.sdzjz.loader.Hooks} 成对，客户端专属那半边
 * （tick 钩、tooltip 钩、键位注册、世界渲染钩）。刻意不塞进 `loader/Hooks`：
 * 专用服务端不该因为一次类加载去解析 MinecraftClient（m402 同一条边界）。
 *
 * <p>换 NeoForge 时本类换一份实现即可：Neo 侧对应 {@code ClientTickEvent}/{@code ItemTooltipEvent}/
 * {@code RegisterKeyMappingsEvent}/{@code RenderLevelStageEvent}。
 */
public final class ClientHooks {

    private ClientHooks() { }

    /** 每客户端 tick 末尾。 */
    public static void onClientTickEnd(java.util.function.Consumer<MinecraftClient> h) {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(h::accept);
    }

    /** 物品悬浮文本追加（m80 全模组水印）。 */
    @FunctionalInterface
    public interface Tooltip {
        void append(net.minecraft.item.ItemStack stack, java.util.List<net.minecraft.text.Text> lines);
    }

    public static void onItemTooltip(Tooltip h) {
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
                (stack, tooltipContext, tooltipType, lines) -> h.append(stack, lines));
    }

    /** 注册键位并返回句柄；`wasPressed()` 轮询照旧由业务侧做（原版类型，可移植）。 */
    public static net.minecraft.client.option.KeyBinding registerKey(String translationKey, int glfwKey, String category) {
        return net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.option.KeyBinding(translationKey,
                        net.minecraft.client.util.InputUtil.Type.KEYSYM, glfwKey, category));
    }

    /** 世界渲染钩：**实体画完之后**那一阶段（m393 血泪——只有这一阶段 matrixStack 与 consumers 双双非 null，
     *  详见 ChunkRegionHighlighter 类注释）。业务侧只拿到三样原版东西：矩阵栈、顶点缓冲口、相机位置。 */
    @FunctionalInterface
    public interface WorldDraw {
        void draw(net.minecraft.client.util.math.MatrixStack matrices,
                  net.minecraft.client.render.VertexConsumerProvider consumers,
                  net.minecraft.util.math.Vec3d cameraPos);
    }

    public static void onWorldDrawAfterEntities(WorldDraw h) {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (ctx.matrixStack() == null || ctx.consumers() == null || ctx.camera() == null) return;
            h.draw(ctx.matrixStack(), ctx.consumers(), ctx.camera().getPos());
        });
    }
}
