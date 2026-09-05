package com.sdzjz.client;

import com.sdzjz.item.CompressedPackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * m529（N2a）压缩包动态图标**绘制体两代共用件**：主线 {@code client/CompressedPackRenderer}（m243/m284/m285）render 体原文整段搬，
 * 加载器接口 {@code BuiltinItemRendererRegistry.DynamicItemRenderer} 留各代壳（Fabric 1.21.1 / Fabric 1.20.1；F 线 Forge 壳同样一句转调）。
 * 世代差两处：内容物 id 解析走 {@link com.sdzjz.item.ItemData#itemById}；扁平件扫光的"给临时栈开流光"走构造器传入的 {@code flatSheen}
 * （1.21 = ENCHANTMENT_GLINT_OVERRIDE 组件；1.20.1 无该组件 = 给临时栈挂一个附魔标记，hasFoil 同效）。
 * <h3>宿主前提</h3>只在渲染线程调；传入的 PoseStack 已被原版 renderItem 做过 -0.5 平移（见下坐标账）；临时栈由本类现造不碰真实物品。
 *
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
public final class CompressedPackIcon {

    private final Item frameItem;
    private final java.util.function.Consumer<ItemStack> flatSheen;

    public CompressedPackIcon(Item frameItem, java.util.function.Consumer<ItemStack> flatSheen) {
        this.frameItem = frameItem;
        this.flatSheen = flatSheen;
    }

    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
                       MultiBufferSource vcp, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        matrices.pushPose();
        matrices.translate(0.5F, 0.5F, 0.5F); // 外层已 -0.5，先回中心原点再嵌套（见类注坐标账）

        ItemStack frame = new ItemStack(frameItem);
        var frameModel = ir.getModel(frame, mc.level, null, 0);
        String id = CompressedPackItem.innerId(stack);
        Item innerItem = id != null ? com.sdzjz.item.ItemData.itemById(id) : Items.AIR; // m529：原 BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))，走 m522 世代口
        boolean hasInner = innerItem != Items.AIR && !(innerItem instanceof CompressedPackItem);
        int spd = com.sdzjz.config.SdzjzConfig.get().compressedPackSpinDegPerSec;

        if (mode == ItemDisplayContext.GUI) {
            // GUI 老路（作者验过说好）：内容物/边框各走自己的 display 变换，边框前移 0.4。
            if (hasInner) {
                ItemStack inner = new ItemStack(innerItem);
                var innerModel = ir.getModel(inner, mc.level, null, 0);
                sheen(inner, innerModel); // m285 扁平件扫光
                matrices.pushPose();
                matrices.scale(0.8F, 0.8F, 0.8F);
                if (spd > 0 && innerModel.isGui3d())
                    matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spinDeg(spd)));
                ir.render(inner, mode, false, matrices, vcp, light, overlay, innerModel);
                matrices.popPose();
            }
            matrices.pushPose();
            matrices.translate(0.0F, 0.0F, 0.4F);
            ir.render(frame, mode, false, matrices, vcp, light, overlay, frameModel);
            matrices.popPose();
        } else {
            // m284 手持/掉落/展示框：此前内容物走方块 display（三人称 t=(0,2.5/16,0)·s0.375）而边框走
            // 扁平件 display（t=(0,3/16,1/16)·s0.55）——两套锚不重合=旋转的方块挂在框外（作者截图实锤）。
            // 修法：整组统一套【边框】的 display 变换（BakedModel#getTransformation 按模式取 Transformation
            // 一次 apply），内外都以 NONE 嵌套渲染=同一锚点必然居中；内容物在框平面内绕 Y 自转。
            // 【几何账】边框贴图实测 2px border→内孔半宽 0.375；内容物缩 0.5(默认)水平旋转半径
            // 0.25×√2≈0.354<0.375=旋转全程不进边框环带；边框前移 0.05 防中孔处 z-fight，无穿插无视差。
            boolean left = mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            matrices.pushPose();
            frameModel.getTransforms().getTransform(mode).apply(left, matrices);
            if (hasInner) {
                ItemStack inner = new ItemStack(innerItem);
                var innerModel = ir.getModel(inner, mc.level, null, 0);
                sheen(inner, innerModel); // m285 扁平件扫光
                matrices.pushPose();
                matrices.scale(0.5F, 0.5F, 0.5F);
                if (spd > 0 && innerModel.isGui3d())
                    matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spinDeg(spd)));
                ir.render(inner, ItemDisplayContext.NONE, false, matrices, vcp, light, overlay, innerModel);
                matrices.popPose();
            }
            matrices.pushPose();
            matrices.translate(0.0F, 0.0F, 0.05F);
            ir.render(frame, ItemDisplayContext.NONE, false, matrices, vcp, light, overlay, frameModel);
            matrices.popPose();
            matrices.popPose();
        }

        matrices.popPose();
    }

    /** m285 扁平内容物扫光（作者："旋转可以加一个扫光，给那些不能旋转的用"）：3D 方块有自转动效，扁平物品静止太素——
     *  给展示栈开流光（世代差：1.21 ENCHANTMENT_GLINT_OVERRIDE 组件 / 1.20.1 附魔标记，由壳传入），流光贴图按物品 alpha 裁形=只扫在物品像素上不糊框。
     *  展示栈是本类现造的临时栈，不碰真实物品。不喜欢一键关配置。 */
    private void sheen(ItemStack inner, net.minecraft.client.resources.model.BakedModel innerModel) {
        if (!innerModel.isGui3d() && com.sdzjz.config.SdzjzConfig.get().compressedPackFlatSheen) flatSheen.accept(inner);
    }

    /** m280 自转角：时间源 Util.getMeasuringTimeMs()（m148 在树先例）与 tick 无关恒匀速，按整圈周期取模防浮点漂移。 */
    private static float spinDeg(int spd) {
        long periodMs = Math.max(1L, 360_000L / spd); // m290 夹紧：配置 spd>360000 时整除得 0，%0=ArithmeticException 崩渲染线程
        return (net.minecraft.Util.getMillis() % periodMs) * spd / 1000.0F;
    }
}
