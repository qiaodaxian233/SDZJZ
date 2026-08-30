package com.sdzjz.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * m483（真移植·绞杀者第六刀）：{@link SciSkin.Gfx} 的 1.21 世代实现——两处**原句照搬**
 * 自合一前的 SciSkin（m180 家法：方法体一字不改，回归风险按构造为零）。
 * 顶点四角着色的参数顺序 (c11,c12,c22,c21) 对应左上→左下→右下→右上，与原 vGrad/hGrad
 * 的下笔顺序逐位一致（顺序错了会画出扭曲的渐变，故此处不做"整理"）。
 */
public final class LegacySkinGfx implements SciSkin.Gfx {

    @Override
    public ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath("sdzjz", path); // 原句照搬
    }

    @Override
    public ResourceLocation id(String s) {
        return ResourceLocation.parse(s); // 原句照搬（主线 drawNode 原文用法）
    }

    @Override
    public void quad(GuiGraphics ctx, float x1, float y1, float x2, float y2,
                     int c11, int c12, int c22, int c21) {
        com.mojang.blaze3d.vertex.VertexConsumer vc =
                ctx.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        org.joml.Matrix4f mat = ctx.pose().last().pose();
        vc.addVertex(mat, x1, y1, 0).setColor(c11); // 原句照搬（下笔顺序不动）
        vc.addVertex(mat, x1, y2, 0).setColor(c12);
        vc.addVertex(mat, x2, y2, 0).setColor(c22);
        vc.addVertex(mat, x2, y1, 0).setColor(c21);
    }
}
