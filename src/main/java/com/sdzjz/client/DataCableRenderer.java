package com.sdzjz.client;

import com.sdzjz.block.DataCableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 数据线能量脉冲（复刻用户 bbmodel 的 animation.energy_flow，1.5s 循环）：
 * 原动画为 5 组脉冲沿缆依次亮起 = 能量包沿线流动；连接式线缆按方向适配为
 * 「每条连接臂上一个能量包从外端流向中心」，端点用缩放包络淡入淡出。
 */
public class DataCableRenderer implements BlockEntityRenderer<DataCableBlockEntity> {

    private static final Identifier TEXTURE = Identifier.of("sdzjz", "textures/block/data_cable.png");
    /** 各方向：把"指北"的局部脉冲转到该方向的四元数（对照多部件 blockstate 旋转）。 */
    private static final Direction[] DIRS = Direction.values();

    public DataCableRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(DataCableBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = be.getCachedState();
        if (!(state.getBlock() instanceof com.sdzjz.block.DataCableBlock)) return;
        float time = (be.getWorld() != null ? be.getWorld().getTime() % 30L : 0L) + tickDelta; // 1.5s=30t
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));

        for (int i = 0; i < DIRS.length; i++) {
            Direction d = DIRS[i];
            BooleanProperty prop = net.minecraft.block.ConnectingBlock.FACING_PROPERTIES.get(d);
            if (!state.get(prop)) continue;
            float progress = ((time + i * 5f) % 30f) / 30f;          // 各方向错相 0.25s
            float zLocal = 0.06f + progress * 0.44f;                  // 外端 → 中心
            float fade = (float) Math.sin(Math.PI * progress);        // 端点淡入淡出
            float scale = 0.35f + 0.65f * fade;                       // 对应原 0.2↔1 缩放包络

            matrices.push();
            matrices.translate(0.5f, 0.5f, 0.5f);
            matrices.multiply(rotationFor(d));
            matrices.translate(0f, 0f, zLocal - 0.5f);                // 局部脉冲中心移到臂上
            matrices.scale(scale, scale, scale);
            emit(matrices.peek(), vc, light, overlay);
            matrices.pop();
        }
    }

    private static Quaternionf rotationFor(Direction d) {
        return switch (d) {
            case NORTH -> new Quaternionf();
            case SOUTH -> RotationAxis.POSITIVE_Y.rotationDegrees(180f);
            case WEST  -> RotationAxis.POSITIVE_Y.rotationDegrees(90f);
            case EAST  -> RotationAxis.POSITIVE_Y.rotationDegrees(-90f);
            case UP    -> RotationAxis.POSITIVE_X.rotationDegrees(90f);
            case DOWN  -> RotationAxis.POSITIVE_X.rotationDegrees(-90f);
        };
    }

    private static void emit(MatrixStack.Entry entry, VertexConsumer vc, int light, int overlay) {
        Matrix4f pm = entry.getPositionMatrix();
        Matrix3f nm = entry.getNormalMatrix();
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (float[] q : DataCableAnimGeo.PULSE) {
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
