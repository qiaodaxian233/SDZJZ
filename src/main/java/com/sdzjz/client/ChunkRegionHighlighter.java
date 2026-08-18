package com.sdzjz.client;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.node.NodeTags;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;

/**
 * m384 选区高亮器（作者三连需求①"显示当前移除的区域"+③"技能选中"的客户端半）——
 * 手持**已绑定**的区块移除器：世界内浮现紫色能量框罩住整个 w×w 选区（全高线框+呼吸脉动），
 * r>0 时内部画分块格线；换维度/未绑定/收手即隐。纯客户端零协议：直读手上物品 NBT
 * （z 族键 NodeTags 读器，m180 铁律直连），无需任何同步。运行中的"施法"表现由服务端粒子
 * 承担（SCBE 移除器分支，同笔），画布上机器的选区不在此渲染（手持=瞄准态，开工=粒子态）。
 * 挂 WorldRenderEvents.AFTER_TRANSLUCENT（fabric-rendering-v1，pin 版分支原文核到事件与
 * matrixStack()/camera()/consumers() 三口，consumers 官方标 @Nullable 已判空）。
 * drawBox=WorldRenderer.method_22980（1.21.1 尚未搬 VertexRendering，yarn 编译级）；
 * 分块格线用退化盒（x1==x2 的 drawBox 塌成矩形框）省手搓顶点。开关随 chunkFxEnabled。
 */
public final class ChunkRegionHighlighter {

    private ChunkRegionHighlighter() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ChunkRegionHighlighter::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (!SdzjzConfig.get().chunkFxEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;
        ItemStack held = mc.player.getMainHandStack();
        if (!(held.getItem() instanceof com.sdzjz.item.ChunkRemoverItem)) held = mc.player.getOffHandStack();
        if (!(held.getItem() instanceof com.sdzjz.item.ChunkRemoverItem)) return;
        if (!NodeTags.chunkBound(held)) return;
        if (!mc.world.getRegistryKey().getValue().toString().equals(NodeTags.chunkDim(held))) return;

        int cx = NodeTags.chunkX(held), cz = NodeTags.chunkZ(held);
        int r = Math.max(0, Math.min(NodeTags.chunkRadius(held), Math.max(0, SdzjzConfig.get().chunkRemoverMaxRadius)));
        int w = 2 * r + 1;
        double x1 = (double) ((cx - r) << 4), z1 = (double) ((cz - r) << 4);
        double x2 = x1 + w * 16.0, z2 = z1 + w * 16.0;
        double y1 = mc.world.getBottomY(), y2 = mc.world.getTopY();

        MatrixStack ms = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());
        // 主框：紫色呼吸脉动（"技能选中"圈）
        float pulse = 0.55f + 0.35f * (float) Math.sin(Util.getMeasuringTimeMs() / 300.0);
        WorldRenderer.drawBox(ms, vc, x1, y1, z1, x2, y2, z2, 0.72f, 0.35f, 1.0f, pulse);
        // r>0：内部分块格线（退化盒=恒淡竖直隔板框，玩家一眼读出 3×3/5×5）
        if (r > 0) {
            for (int gi = 1; gi < w; gi++) {
                double gx = x1 + gi * 16.0, gz = z1 + gi * 16.0;
                WorldRenderer.drawBox(ms, vc, gx, y1, z1, gx, y2, z2, 0.60f, 0.30f, 0.90f, 0.25f);
                WorldRenderer.drawBox(ms, vc, x1, y1, gz, x2, y2, gz, 0.60f, 0.30f, 0.90f, 0.25f);
            }
        }
        ms.pop();
    }
}
