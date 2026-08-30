package com.sdzjz.retro;

import com.sdzjz.client.SciSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * m483（真移植·绞杀者第六刀）：{@link SciSkin.Gfx} 的 1.20.1 世代实现。
 * 两处世代差：①纹理 id 用构造器（{@code fromNamespaceAndPath} 是 1.20.5+ 才有的静态工厂）；
 * ②顶点写法用 {@code vertex(...).color(r,g,b,a).endVertex()}（1.21 改成了链式 addVertex/setColor）。
 *
 * <p>颜色拆分：上层传的是 ARGB 打包 int（与 SciSkinPalette 全表同口径），本世代 {@code color()}
 * 收四个 0~255 分量，故此处按 A/R/G/B 拆开——**拆法与 1.21 侧 setColor(int) 的内部口径一致**
 * （高位 A、次 R、次 G、低 B），两代颜色因此逐位相同。
 */
final class RetroSkinGfx implements SciSkin.Gfx {

    @Override
    public ResourceLocation tex(String path) {
        return new ResourceLocation("sdzjz", path); // 1.20.1 对位：构造器
    }

    @Override
    public ResourceLocation id(String s) {
        return new ResourceLocation(s); // 1.20.1 对位：构造器（parse 是 1.20.5+）
    }

    @Override
    public void quad(GuiGraphics ctx, float x1, float y1, float x2, float y2,
                     int c11, int c12, int c22, int c21) {
        com.mojang.blaze3d.vertex.VertexConsumer vc =
                ctx.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        org.joml.Matrix4f mat = ctx.pose().last().pose();
        v(vc, mat, x1, y1, c11); // 下笔顺序与 1.21 侧逐位一致（左上→左下→右下→右上）
        v(vc, mat, x1, y2, c12);
        v(vc, mat, x2, y2, c22);
        v(vc, mat, x2, y1, c21);
    }

    private static void v(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f mat,
                          float x, float y, int argb) {
        vc.vertex(mat, x, y, 0)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }
}
