package com.sdzjz.retro;

import com.sdzjz.client.DataCableAnimGeo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * m465：数据线能量脉冲渲染 1.20.1 版——蓝本=xplat {@code DataCableRenderer}
 * （energy_flow 1.5s 循环：每条连接臂一个能量包从外端流向中心，各方向错相 0.25s，
 * 端点缩放包络淡入淡出）。几何数据 {@code DataCableAnimGeo} 经 xplat 白名单挂载同一份。
 * 与蓝本世代差同 {@code StorageCoreRenderer120} 两条（ResourceLocation 构造器/链式顶点），
 * 外加属性表对位：主线 DataCableBlock.END_PROPS/CableEnd → 本世代
 * DataCableBlock120.END_PROPS/CableEnd120（m444 对位新写，序列化名同源）。
 */
public final class DataCableRenderer120 implements BlockEntityRenderer<DataCable120> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("sdzjz", "textures/block/data_cable.png");
    /** 各方向：把"指北"的局部脉冲转到该方向的四元数（对照多部件 blockstate 旋转）。 */
    private static final Direction[] DIRS = Direction.values();

    public DataCableRenderer120(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(DataCable120 be, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof DataCableBlock120)) return;
        float time = (be.getLevel() != null ? be.getLevel().getGameTime() % 30L : 0L) + tickDelta; // 1.5s=30t
        VertexConsumer vc = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        for (int i = 0; i < DIRS.length; i++) {
            Direction d = DIRS[i];
            if (state.getValue(DataCableBlock120.END_PROPS.get(d)) == CableEnd120.NONE) continue;
            float progress = ((time + i * 5f) % 30f) / 30f;          // 各方向错相 0.25s
            float zLocal = 0.06f + progress * 0.44f;                  // 外端 → 中心
            float fade = (float) Math.sin(Math.PI * progress);        // 端点淡入淡出
            float scale = 0.35f + 0.65f * fade;                       // 对应原 0.2↔1 缩放包络

            matrices.pushPose();
            matrices.translate(0.5f, 0.5f, 0.5f);
            matrices.mulPose(rotationFor(d));
            matrices.translate(0f, 0f, zLocal - 0.5f);                // 局部脉冲中心移到臂上
            matrices.scale(scale, scale, scale);
            emit(matrices.last(), vc, light, overlay);
            matrices.popPose();
        }
    }

    private static Quaternionf rotationFor(Direction d) {
        return switch (d) {
            case NORTH -> new Quaternionf();
            case SOUTH -> Axis.YP.rotationDegrees(180f);
            case WEST  -> Axis.YP.rotationDegrees(90f);
            case EAST  -> Axis.YP.rotationDegrees(-90f);
            case UP    -> Axis.XP.rotationDegrees(90f);
            case DOWN  -> Axis.XP.rotationDegrees(-90f);
        };
    }

    private static void emit(PoseStack.Pose entry, VertexConsumer vc, int light, int overlay) {
        Matrix4f pm = entry.pose();
        Matrix3f nm = entry.normal();
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (float[] q : DataCableAnimGeo.PULSE) {
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
