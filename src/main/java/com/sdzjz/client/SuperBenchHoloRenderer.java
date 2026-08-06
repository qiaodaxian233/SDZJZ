package com.sdzjz.client;

import com.sdzjz.block.SuperBenchBlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 超大工作台悬浮全息网格（m249）：一片 0.9×0.9 的 12×12 青色网格全息悬在台面上方
 * （y≈1.14，浮沉 ±0.04，2s 正弦）缓转（22.5°/s，16s 一圈），视觉语义="合成网格待命"。
 * m250 返工：EntityTranslucentEmissive 半透明自发光、128px 细线网格贴图整片径向羽化、
 * 0.76 宽、9°/s 缓转、alpha 呼吸——通透全息片，不再是塑料格栅。
 * BE 零数据只当挂点——旧存档已放置的工作台没有 BE，重放一次即有动画（DEVLOG 已记）。
 */
public class SuperBenchHoloRenderer implements BlockEntityRenderer<SuperBenchBlockEntity> {

    private static final Identifier TEX = Identifier.of("sdzjz", "textures/block/holo_grid.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    public SuperBenchHoloRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(SuperBenchBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light, int overlay) {
        float time = (be.getWorld() != null ? be.getWorld().getTime() % 800L : 0L) + tickDelta; // 40s 转一圈（9°/s）
        float bob = 0.04f * (float) Math.sin(Math.PI * 2 * ((time % 40f) / 40f));               // 2s 浮沉
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEX));

        matrices.push();
        int col = ((int) ((0.62f + 0.18f * Math.sin(Math.PI * 2 * ((time % 60f) / 60f))) * 255) << 24) | 0xFFFFFF; // alpha 呼吸
        matrices.translate(0.5f, 1.22f + bob, 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 0.45f)); // 9°/s 缓转
        Matrix4f pm = matrices.peek().getPositionMatrix();
        float h = 0.38f;
        Vector3f p = new Vector3f();
        pm.transformPosition(p.set(-h, 0, -h)); vc.vertex(p.x, p.y, p.z, col, 0f, 0f, overlay, FULL, 0, 1, 0);
        pm.transformPosition(p.set(-h, 0,  h)); vc.vertex(p.x, p.y, p.z, col, 0f, 1f, overlay, FULL, 0, 1, 0);
        pm.transformPosition(p.set( h, 0,  h)); vc.vertex(p.x, p.y, p.z, col, 1f, 1f, overlay, FULL, 0, 1, 0);
        pm.transformPosition(p.set( h, 0, -h)); vc.vertex(p.x, p.y, p.z, col, 1f, 0f, overlay, FULL, 0, 1, 0);
        matrices.pop();
    }
}
