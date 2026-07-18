package com.sdzjz.client;

import com.sdzjz.block.StorageCoreBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
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

    private static final Identifier TEXTURE = Identifier.of("sdzjz", "textures/block/storage_core.png");

    public StorageCoreRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(StorageCoreBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        float time = (be.getWorld() != null ? be.getWorld().getTime() % 80L : 0L) + tickDelta; // 4s=80t 循环
        float phase = (time % 40f) / 40f;                          // 2s 三角波相位
        float tri = phase < 0.5f ? phase * 2f : 2f - phase * 2f;   // 0→1→0
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));

        // core_energy：绕方块中心(0.5,*,0.5)旋转 + 呼吸
        float coreScale = 1f + 0.08f * tri;
        matrices.push();
        matrices.translate(0.5f, 0f, 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-time * 4.5f)); // -90°/s
        matrices.scale(coreScale, coreScale, coreScale);
        matrices.translate(-0.5f, 0f, -0.5f);
        emit(StorageCoreAnimGeo.CORE, matrices.peek(), vc, light, overlay);
        matrices.pop();

        // corner_lights：呼吸（X/Z 1.04、Y 1.08）
        matrices.push();
        matrices.translate(0.5f, 0f, 0.5f);
        matrices.scale(1f + 0.04f * tri, 1f + 0.08f * tri, 1f + 0.04f * tri);
        matrices.translate(-0.5f, 0f, -0.5f);
        emit(StorageCoreAnimGeo.LIGHTS, matrices.peek(), vc, light, overlay);
        matrices.pop();
    }

    /** 逐四边形发顶点：行 = nx,ny,nz + 4×(x,y,z,u,v)。 */
    private static void emit(float[][] quads, MatrixStack.Entry entry, VertexConsumer vc, int light, int overlay) {
        Matrix4f pm = entry.getPositionMatrix();
        Matrix3f nm = entry.getNormalMatrix();
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (float[] q : quads) {
            nm.transform(n.set(q[0], q[1], q[2]));
            if (n.lengthSquared() > 1.0E-6f) n.normalize();
            for (int v = 0; v < 4; v++) {
                int o = 3 + v * 5;
                pm.transformPosition(p.set(q[o], q[o + 1], q[o + 2]));
                vc.vertex(p.x, p.y, p.z, 0xFFFFFFFF, q[o + 3], q[o + 4], overlay, light, n.x, n.y, n.z);
            }
        }
    }
}
