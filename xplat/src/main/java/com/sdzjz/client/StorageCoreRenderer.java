package com.sdzjz.client;

import com.sdzjz.block.StorageCoreBlockEntity;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 存储核心动画渲染（复刻用户 bbmodel 的 animation.core_cycle，4s 循环）：
 * - core_energy：绕 Y 匀速旋转（4s 一圈）+ 呼吸缩放 1↔1.08（2s 三角波）
 * - corner_lights：呼吸缩放 (1.04, 1.08, 1.04)（2s 三角波）
 * 静态机身走方块模型 JSON，此处只画 26 个动画件（每帧约 156 个四边形，开销可忽略）。
 */
public class StorageCoreRenderer implements BlockEntityRenderer<StorageCoreBlockEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("sdzjz", "textures/block/storage_core.png");

    public StorageCoreRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(StorageCoreBlockEntity be, float tickDelta, PoseStack matrices,
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

    /** 逐四边形发顶点：行 = nx,ny,nz + 4×(x,y,z,u,v)。 */
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
                vc.addVertex(p.x, p.y, p.z, 0xFFFFFFFF, q[o + 3], q[o + 4], overlay, light, n.x, n.y, n.z);
            }
        }
    }
}
