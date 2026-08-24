package com.sdzjz.retro;

import com.sdzjz.client.StorageCoreAnimGeo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * m465：存储核心动画渲染 1.20.1 版——蓝本=xplat {@code StorageCoreRenderer}（m*系主线在用），
 * 动画语义逐位不变（core_cycle 4s：core_energy 绕 Y 匀速旋转+呼吸 1↔1.08 三角波、
 * corner_lights 呼吸 (1.04,1.08,1.04)）；几何数据 {@code StorageCoreAnimGeo} 经 xplat 白名单
 * 挂载同一份（m451 机制，仓库零副本）。与蓝本仅两处世代差：
 * ①ResourceLocation 走构造器（1.21 是静态工厂，RetroBlocks 同款口径）；
 * ②顶点发射走 1.20.1 链式 vertex(...).color(...).uv(...).overlayCoords(...).uv2(...)
 *   .normal(...).endVertex()（1.21 是单口 addVertex）——法线仍先经 JOML 手工变换+归一，
 *   与蓝本零法线护栏同律。静态机身走方块模型 JSON（本笔一并归位），此处只画动画件。
 */
public final class StorageCoreRenderer120 implements BlockEntityRenderer<StorageCore120> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("sdzjz", "textures/block/storage_core.png");

    public StorageCoreRenderer120(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(StorageCore120 be, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        float time = (be.getLevel() != null ? be.getLevel().getGameTime() % 80L : 0L) + tickDelta; // 4s=80t 循环
        float phase = (time % 40f) / 40f;                          // 2s 三角波相位
        float tri = phase < 0.5f ? phase * 2f : 2f - phase * 2f;   // 0→1→0
        VertexConsumer vc = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        // core_energy：绕方块中心(0.5,*,0.5)旋转 + 呼吸
        float coreScale = 1f + 0.08f * tri;
        matrices.pushPose();
        matrices.translate(0.5f, 0f, 0.5f);
        matrices.mulPose(Axis.YP.rotationDegrees(-time * 4.5f)); // -90°/s
        matrices.scale(coreScale, coreScale, coreScale);
        matrices.translate(-0.5f, 0f, -0.5f);
        emit(StorageCoreAnimGeo.CORE, matrices.last(), vc, light, overlay);
        matrices.popPose();

        // corner_lights：呼吸（X/Z 1.04、Y 1.08）
        matrices.pushPose();
        matrices.translate(0.5f, 0f, 0.5f);
        matrices.scale(1f + 0.04f * tri, 1f + 0.08f * tri, 1f + 0.04f * tri);
        matrices.translate(-0.5f, 0f, -0.5f);
        emit(StorageCoreAnimGeo.LIGHTS, matrices.last(), vc, light, overlay);
        matrices.popPose();
    }

    /** 逐四边形发顶点：行 = nx,ny,nz + 4×(x,y,z,u,v)（与蓝本 emit 同构，只换发射口）。 */
    private static void emit(float[][] quads, PoseStack.Pose entry, VertexConsumer vc, int light, int overlay) {
        Matrix4f pm = entry.pose();
        Matrix3f nm = entry.normal();
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (float[] q : quads) {
            nm.transform(n.set(q[0], q[1], q[2]));
            if (n.lengthSquared() > 1.0E-6f) n.normalize();
            for (int v = 0; v < 4; v++) {
                int o = 3 + v * 5;
                pm.transformPosition(p.set(q[o], q[o + 1], q[o + 2]));
                vc.vertex(p.x, p.y, p.z).color(255, 255, 255, 255).uv(q[o + 3], q[o + 4])
                        .overlayCoords(overlay).uv2(light).normal(n.x, n.y, n.z).endVertex();
            }
        }
    }
}
