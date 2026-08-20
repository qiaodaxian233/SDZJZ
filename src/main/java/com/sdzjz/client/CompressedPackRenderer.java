package com.sdzjz.client;

import com.sdzjz.item.CompressedPackItem;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * 压缩包动态图标（m243，作者拍板"加框"）：不给每种内容物做图标——
 * 包的图标 = 内容物自己的模型（缩 0.8 居中，方块 3D 视角/扁平物品都走它自己的 display 变换）
 *          + 档位边框叠在前面（一级薰衣草框 / 二级深紫框，隐藏渲染件 *_frame 的扁平模型）。
 * 裸包 = 只画空边框（配合 tooltip"空包"）。
 *
 * 【坐标账】原版 renderItem 对 builtin 模型：先应用本模型 display 变换（我们的包模型不写 display=恒等），
 * 再 translate(-0.5,-0.5,-0.5) 才调进来——所以进来先 +0.5 平移回"renderItem 期望的中心原点"，
 * 再嵌套调 renderItem(内容物/边框)，让它们各自应用【自己的】display 变换（GUI 里方块自动 30°/225° 立体视角，
 * 手持/掉落姿态也全对），不会双重变换。
 * 【深度账】GUI：3D 方块经 gui 变换后最前伸到 ~+0.34，边框扁片本体在 ±0.03——GUI 模式把边框前移 0.4
 * （正交投影不改 XY 位置）。非 GUI（m284）：整组统一套边框 display 变换（同锚必居中），内容物缩 0.5
 * 旋转半径 0.354<内孔半宽 0.375 不进环带，边框仅前移 0.05 防中孔 z-fight。
 * 【防递归】包的内容物按构造永远是原版散件 id（m241 压缩引擎排包），这里再兜底一道 instanceof 跳过。
 */
public class CompressedPackRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    private final Item frameItem;

    public CompressedPackRenderer(Item frameItem) {
        this.frameItem = frameItem;
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
                       MultiBufferSource vcp, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        matrices.push();
        matrices.translate(0.5F, 0.5F, 0.5F); // 外层已 -0.5，先回中心原点再嵌套（见类注坐标账）

        ItemStack frame = new ItemStack(frameItem);
        var frameModel = ir.getModel(frame, mc.world, null, 0);
        String id = CompressedPackItem.innerId(stack);
        Item innerItem = id != null ? BuiltInRegistries.ITEM.get(ResourceLocation.of(id)) : Items.AIR;
        boolean hasInner = innerItem != Items.AIR && !(innerItem instanceof CompressedPackItem);
        int spd = com.sdzjz.config.SdzjzConfig.get().compressedPackSpinDegPerSec;

        if (mode == ItemDisplayContext.GUI) {
            // GUI 老路（作者验过说好）：内容物/边框各走自己的 display 变换，边框前移 0.4。
            if (hasInner) {
                ItemStack inner = new ItemStack(innerItem);
                var innerModel = ir.getModel(inner, mc.world, null, 0);
                sheen(inner, innerModel); // m285 扁平件扫光
                matrices.push();
                matrices.scale(0.8F, 0.8F, 0.8F);
                if (spd > 0 && innerModel.hasDepth())
                    matrices.multiply(com.mojang.math.Axis.POSITIVE_Y.rotationDegrees(spinDeg(spd)));
                ir.renderItem(inner, mode, false, matrices, vcp, light, overlay, innerModel);
                matrices.pop();
            }
            matrices.push();
            matrices.translate(0.0F, 0.0F, 0.4F);
            ir.renderItem(frame, mode, false, matrices, vcp, light, overlay, frameModel);
            matrices.pop();
        } else {
            // m284 手持/掉落/展示框：此前内容物走方块 display（三人称 t=(0,2.5/16,0)·s0.375）而边框走
            // 扁平件 display（t=(0,3/16,1/16)·s0.55）——两套锚不重合=旋转的方块挂在框外（作者截图实锤）。
            // 修法：整组统一套【边框】的 display 变换（BakedModel#getTransformation 按模式取 Transformation
            // 一次 apply），内外都以 NONE 嵌套渲染=同一锚点必然居中；内容物在框平面内绕 Y 自转。
            // 【几何账】边框贴图实测 2px border→内孔半宽 0.375；内容物缩 0.5(默认)水平旋转半径
            // 0.25×√2≈0.354<0.375=旋转全程不进边框环带；边框前移 0.05 防中孔处 z-fight，无穿插无视差。
            boolean left = mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            matrices.push();
            frameModel.getTransformation().getTransformation(mode).apply(left, matrices);
            if (hasInner) {
                ItemStack inner = new ItemStack(innerItem);
                var innerModel = ir.getModel(inner, mc.world, null, 0);
                sheen(inner, innerModel); // m285 扁平件扫光
                matrices.push();
                matrices.scale(0.5F, 0.5F, 0.5F);
                if (spd > 0 && innerModel.hasDepth())
                    matrices.multiply(com.mojang.math.Axis.POSITIVE_Y.rotationDegrees(spinDeg(spd)));
                ir.renderItem(inner, ItemDisplayContext.NONE, false, matrices, vcp, light, overlay, innerModel);
                matrices.pop();
            }
            matrices.push();
            matrices.translate(0.0F, 0.0F, 0.05F);
            ir.renderItem(frame, ItemDisplayContext.NONE, false, matrices, vcp, light, overlay, frameModel);
            matrices.pop();
            matrices.pop();
        }

        matrices.pop();
    }

    /** m285 扁平内容物扫光（作者："旋转可以加一个扫光，给那些不能旋转的用"）：3D 方块有自转动效，
     *  扁平物品（剑/锭/粉）静止太素——给展示栈开原版附魔流光组件（ENCHANTMENT_GLINT_OVERRIDE，
     *  1.20.5+ 数据组件，在树 CUSTOM_DATA/POTION_CONTENTS 同命名律），流光贴图按物品 alpha 裁形
     *  =天然只扫在物品像素上不糊框，GUI/手持/掉落全模式同效零自定义几何。展示栈是本渲染器现造的
     *  临时栈，不碰真实物品组件。可能与真附魔物观感混淆——包内容物按构造只会是原版散件、tooltip
     *  写明内容物，可接受；不喜欢一键关配置。 */
    private static void sheen(ItemStack inner, net.minecraft.client.resources.model.BakedModel innerModel) {
        if (!innerModel.hasDepth() && com.sdzjz.config.SdzjzConfig.get().compressedPackFlatSheen)
            inner.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
    }

    /** m280 自转角：时间源 Util.getMeasuringTimeMs()（m148 在树先例）与 tick 无关恒匀速，按整圈周期取模防浮点漂移。 */
    private static float spinDeg(int spd) {
        long periodMs = Math.max(1L, 360_000L / spd); // m290 夹紧：配置 spd>360000 时整除得 0，%0=ArithmeticException 崩渲染线程
        return (net.minecraft.Util.getMeasuringTimeMs() % periodMs) * spd / 1000.0F;
    }
}
