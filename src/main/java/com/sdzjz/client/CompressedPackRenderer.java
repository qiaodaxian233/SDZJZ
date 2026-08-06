package com.sdzjz.client;

import com.sdzjz.item.CompressedPackItem;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

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
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light, int overlay) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        matrices.push();
        matrices.translate(0.5F, 0.5F, 0.5F); // 外层已 -0.5，先回中心原点再嵌套（见类注坐标账）

        ItemStack frame = new ItemStack(frameItem);
        var frameModel = ir.getModel(frame, mc.world, null, 0);
        String id = CompressedPackItem.innerId(stack);
        Item innerItem = id != null ? Registries.ITEM.get(Identifier.of(id)) : Items.AIR;
        boolean hasInner = innerItem != Items.AIR && !(innerItem instanceof CompressedPackItem);
        int spd = com.sdzjz.config.SdzjzConfig.get().compressedPackSpinDegPerSec;

        if (mode == ModelTransformationMode.GUI) {
            // GUI 老路（作者验过说好）：内容物/边框各走自己的 display 变换，边框前移 0.4。
            if (hasInner) {
                ItemStack inner = new ItemStack(innerItem);
                var innerModel = ir.getModel(inner, mc.world, null, 0);
                matrices.push();
                matrices.scale(0.8F, 0.8F, 0.8F);
                if (spd > 0 && innerModel.hasDepth())
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(spinDeg(spd)));
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
            boolean left = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
                    || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
            matrices.push();
            frameModel.getTransformation().getTransformation(mode).apply(left, matrices);
            if (hasInner) {
                ItemStack inner = new ItemStack(innerItem);
                var innerModel = ir.getModel(inner, mc.world, null, 0);
                matrices.push();
                matrices.scale(0.5F, 0.5F, 0.5F);
                if (spd > 0 && innerModel.hasDepth())
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(spinDeg(spd)));
                ir.renderItem(inner, ModelTransformationMode.NONE, false, matrices, vcp, light, overlay, innerModel);
                matrices.pop();
            }
            matrices.push();
            matrices.translate(0.0F, 0.0F, 0.05F);
            ir.renderItem(frame, ModelTransformationMode.NONE, false, matrices, vcp, light, overlay, frameModel);
            matrices.pop();
            matrices.pop();
        }

        matrices.pop();
    }

    /** m280 自转角：时间源 Util.getMeasuringTimeMs()（m148 在树先例）与 tick 无关恒匀速，按整圈周期取模防浮点漂移。 */
    private static float spinDeg(int spd) {
        long periodMs = 360_000L / spd;
        return (net.minecraft.util.Util.getMeasuringTimeMs() % periodMs) * spd / 1000.0F;
    }
}
