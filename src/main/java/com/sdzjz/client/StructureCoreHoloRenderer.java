package com.sdzjz.client;

import com.sdzjz.block.StructureCoreBlockEntity;
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
 * 结构核心全息扫描环（m249，作者点名"存储核心和线都是动态的，它们不动就不好看"）：
 * 一圈青色渐变带绕方块四面，沿 Y 轴 0.08↔0.80 三角波巡扫（3s=60t 循环）+ 呼吸微胀，
 * 视觉语义="核心在逐层扫描它挂着的结构"。静态机身照走方块模型；
 * 满亮度（LightmapTextureManager.MAX_LIGHT_COORDINATE，field_32767 已核）当自发光，
 * EntityCutoutNoCull 双面可见（StorageCoreRenderer 同层同工艺）。
 */
public class StructureCoreHoloRenderer implements BlockEntityRenderer<StructureCoreBlockEntity> {

    private static final Identifier TEX = Identifier.of("sdzjz", "textures/block/holo_scan.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    public StructureCoreHoloRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(StructureCoreBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light, int overlay) {
        float time = (be.getWorld() != null ? be.getWorld().getTime() % 60L : 0L) + tickDelta; // 3s 循环
        float phase = time / 60f;
        float tri = phase < 0.5f ? phase * 2f : 2f - phase * 2f;      // 0→1→0
        float y0 = 0.08f + tri * 0.72f, y1 = y0 + 0.10f;              // 扫描带 0.10 高
        float grow = 0.015f * (float) Math.sin(Math.PI * 2 * phase); // 呼吸微胀
        float lo = -0.02f - grow, hi = 1.02f + grow;

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX));
        Matrix4f pm = matrices.peek().getPositionMatrix();
        // 四面各一片（法线朝外；NoCull 双面可见）
        quad(vc, pm, overlay, lo, y0, lo,  hi, y0, lo,  hi, y1, lo,  lo, y1, lo,  0, 0, -1); // 北
        quad(vc, pm, overlay, hi, y0, hi,  lo, y0, hi,  lo, y1, hi,  hi, y1, hi,  0, 0, 1);  // 南
        quad(vc, pm, overlay, lo, y0, hi,  lo, y0, lo,  lo, y1, lo,  lo, y1, hi,  -1, 0, 0); // 西
        quad(vc, pm, overlay, hi, y0, lo,  hi, y0, hi,  hi, y1, hi,  hi, y1, lo,  1, 0, 0);  // 东
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
