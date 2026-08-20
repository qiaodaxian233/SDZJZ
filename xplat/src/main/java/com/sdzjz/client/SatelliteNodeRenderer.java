package com.sdzjz.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sdzjz.block.SatelliteNodeBlockEntity;
import net.minecraft.client.Minecraft;
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

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * m156 卫星节点扫描动画（bbmodel 带 animation.satellite_scan 3s 循环——静态烘焙模型天生不动，
 * 动画走 BER，几何仍读 geo.json 三分组）：
 *   scan（锅+馈源+俯仰轴，626 quad）绕桅杆轴(0.5,0.5) ±35° 正弦往复扫描，周期 3s；
 *   signal（信号波 72 quad）跟随扫描旋转 + 自身枢轴缩放脉冲（0.72↔1.12，1.5s）；
 *   static（底座+塔身）不动。
 * 顶点链/独立贴图直连 RenderType 照 WirelessNodeRenderer 同款（本仓编译验证过的 API）；
 * 未逐帧还原 bbmodel 关键帧（6 动画器组树解析成本高）——做的是程序化等效扫描，
 * 要原汁关键帧下一步解 outliner+animators。geo 读取失败=只渲静态兜底，不炸。
 */
public class SatelliteNodeRenderer implements BlockEntityRenderer<SatelliteNodeBlockEntity> {
    private static final ResourceLocation GEO_ID = ResourceLocation.of("sdzjz", "models/block/satellite_node_geo.json");
    private static final ResourceLocation TEX_ATLAS = ResourceLocation.of("sdzjz", "textures/block/satellite_node_atlas.png");
    private static final ResourceLocation TEX_JOINT = ResourceLocation.of("sdzjz", "textures/block/satellite_dish_joint.png");
    private static final float SCAN_PERIOD_S = 3.0f, SIGNAL_PERIOD_S = 1.5f;

    /** [group][texture] → quad 列表；quad = float[3法线 + 4×(xyz uv)]，坐标已 /16。 */
    private static List<float[]>[][] GEO;   // [0静 1扫 2波][0 atlas 1 joint]
    private static float[] SCAN_PIVOT = {0.5f, 1f, 0.5f}, SIGNAL_PIVOT = {1.1f, 1.8f, 0.5f};
    private static boolean geoTried = false;

    public SatelliteNodeRenderer(BlockEntityRendererProvider.Context ctx) {}

    @SuppressWarnings("unchecked")
    private static void loadGeo() {
        geoTried = true;
        try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(GEO_ID).getInputStream()) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject piv = root.getAsJsonObject("pivots");
            if (piv.has("scan")) SCAN_PIVOT = vec(piv.getAsJsonArray("scan"));
            if (piv.has("signal")) SIGNAL_PIVOT = vec(piv.getAsJsonArray("signal"));
            List<float[]>[][] g = new List[3][2];
            for (int a = 0; a < 3; a++) for (int b = 0; b < 2; b++) g[a][b] = new ArrayList<>();
            for (var el : root.getAsJsonArray("quads")) {
                JsonArray q = el.getAsJsonArray();
                int gi = switch (q.get(0).getAsString()) { case "scan" -> 1; case "signal" -> 2; default -> 0; };
                int ti = q.get(1).getAsString().endsWith("dish_joint") ? 1 : 0;
                float[] f = new float[23];
                for (int k = 0; k < 3; k++) f[k] = q.get(2 + k).getAsFloat();
                for (int v = 0; v < 4; v++) {
                    int src = 5 + v * 5, dst = 3 + v * 5;
                    f[dst] = q.get(src).getAsFloat() / 16f;
                    f[dst + 1] = q.get(src + 1).getAsFloat() / 16f;
                    f[dst + 2] = q.get(src + 2).getAsFloat() / 16f;
                    f[dst + 3] = q.get(src + 3).getAsFloat() / 16f; // UV 0..16 → 0..1 直连整图
                    f[dst + 4] = q.get(src + 4).getAsFloat() / 16f;
                }
                g[gi][ti].add(f);
            }
            GEO = g;
        } catch (Exception e) {
            com.sdzjz.Sdzjz.LOGGER.warn("卫星节点 geo 读取失败，动画渲染停用: {}", e.toString());
        }
    }

    private static float[] vec(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    @Override
    public void render(SatelliteNodeBlockEntity be, float tickDelta, PoseStack matrices,
                       MultiBufferSource vcp, int light, int overlay) {
        if (GEO == null) {
            if (geoTried) return;
            loadGeo();
            if (GEO == null) return;
        }
        float t = ((be.getWorld() != null ? be.getWorld().getTime() % 1200L : 0L) + tickDelta) / 20f;
        float scanDeg = 35f * (float) Math.sin(2 * Math.PI * t / SCAN_PERIOD_S);
        float pulse = 0.92f + 0.20f * (float) Math.sin(2 * Math.PI * t / SIGNAL_PERIOD_S);
        VertexConsumer vcA = vcp.getBuffer(RenderType.getEntityCutoutNoCull(TEX_ATLAS));
        VertexConsumer vcJ = vcp.getBuffer(RenderType.getEntityCutoutNoCull(TEX_JOINT));
        // 静组
        emit(GEO[0][0], matrices.peek(), vcA, light, overlay);
        emit(GEO[0][1], matrices.peek(), vcJ, light, overlay);
        // 扫描组：绕桅杆轴 Y 往复
        matrices.push();
        matrices.translate(SCAN_PIVOT[0], 0, SCAN_PIVOT[2]);
        matrices.multiply(Axis.POSITIVE_Y.rotationDegrees(scanDeg));
        matrices.translate(-SCAN_PIVOT[0], 0, -SCAN_PIVOT[2]);
        emit(GEO[1][0], matrices.peek(), vcA, light, overlay);
        emit(GEO[1][1], matrices.peek(), vcJ, light, overlay);
        // 信号波：跟随扫描 + 自身枢轴缩放脉冲
        matrices.push();
        matrices.translate(SIGNAL_PIVOT[0], SIGNAL_PIVOT[1], SIGNAL_PIVOT[2]);
        matrices.scale(pulse, pulse, pulse);
        matrices.translate(-SIGNAL_PIVOT[0], -SIGNAL_PIVOT[1], -SIGNAL_PIVOT[2]);
        emit(GEO[2][0], matrices.peek(), vcA, light, overlay);
        emit(GEO[2][1], matrices.peek(), vcJ, light, overlay);
        matrices.pop();
        matrices.pop();
    }

    private static void emit(List<float[]> quads, PoseStack.Entry entry, VertexConsumer vc, int light, int overlay) {
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
