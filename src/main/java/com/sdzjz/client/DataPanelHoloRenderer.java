package com.sdzjz.client;

import com.sdzjz.block.DataPanelBlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 数据面板数据流（m249）：四个侧面各一条"字符雨"竖条，贴面外 0.008 左右巡游
 * （2.5s=50t 三角波，各面错相 1/4 周期），视觉语义="面板在滚动读数"。
 * 满亮度自发光 + EntityCutoutNoCull（结构核心扫描环同工艺）。
 */
public class DataPanelHoloRenderer implements BlockEntityRenderer<DataPanelBlockEntity> {

    private static final Identifier TEX = Identifier.of("sdzjz", "textures/block/holo_stream.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final float W = 0.14f, Y0 = 0.10f, Y1 = 0.90f, OFF = 0.008f;

    public DataPanelHoloRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(DataPanelBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light, int overlay) {
        float time = (be.getWorld() != null ? be.getWorld().getTime() % 50L : 0L) + tickDelta; // 2.5s
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX));
        Matrix4f pm = matrices.peek().getPositionMatrix();
        for (int f = 0; f < 4; f++) {
            float phase = ((time + f * 12.5f) % 50f) / 50f;
            float tri = phase < 0.5f ? phase * 2f : 2f - phase * 2f;   // 0→1→0
            float s0 = 0.06f + tri * (0.88f - W), s1 = s0 + W;         // 条带在面内横向位置
            switch (f) {
                case 0 -> quad(vc, pm, overlay, s0, Y0, -OFF, s1, Y0, -OFF, s1, Y1, -OFF, s0, Y1, -OFF, 0, 0, -1);          // 北
                case 1 -> quad(vc, pm, overlay, s1, Y0, 1 + OFF, s0, Y0, 1 + OFF, s0, Y1, 1 + OFF, s1, Y1, 1 + OFF, 0, 0, 1); // 南
                case 2 -> quad(vc, pm, overlay, -OFF, Y0, s1, -OFF, Y0, s0, -OFF, Y1, s0, -OFF, Y1, s1, -1, 0, 0);          // 西
                case 3 -> quad(vc, pm, overlay, 1 + OFF, Y0, s0, 1 + OFF, Y0, s1, 1 + OFF, Y1, s1, 1 + OFF, Y1, s0, 1, 0, 0); // 东
            }
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f pm, int overlay,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz) {
        Vector3f p = new Vector3f();
        pm.transformPosition(p.set(ax, ay, az)); vc.vertex(p.x, p.y, p.z, 0xFFFFFFFF, 0f, 1f, overlay, FULL, nx, ny, nz);
        pm.transformPosition(p.set(bx, by, bz)); vc.vertex(p.x, p.y, p.z, 0xFFFFFFFF, 1f, 1f, overlay, FULL, nx, ny, nz);
        pm.transformPosition(p.set(cx, cy, cz)); vc.vertex(p.x, p.y, p.z, 0xFFFFFFFF, 1f, 0f, overlay, FULL, nx, ny, nz);
        pm.transformPosition(p.set(dx, dy, dz)); vc.vertex(p.x, p.y, p.z, 0xFFFFFFFF, 0f, 0f, overlay, FULL, nx, ny, nz);
    }
}
