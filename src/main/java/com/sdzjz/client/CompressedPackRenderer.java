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
 * 【深度账】GUI 里 3D 方块经 gui 变换后最前伸到 ~+0.34，边框扁片本体在 ±0.03——GUI 模式把边框
 * 前移 0.4（正交投影不改 XY 位置），其余模式 0.03 防 z-fight 即可。
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

        String id = CompressedPackItem.innerId(stack);
        if (id != null) {
            Item innerItem = Registries.ITEM.get(Identifier.of(id));
            if (innerItem != Items.AIR && !(innerItem instanceof CompressedPackItem)) {
                ItemStack inner = new ItemStack(innerItem);
                var innerModel = ir.getModel(inner, mc.world, null, 0); // BakedModel，先取出来问 hasDepth
                matrices.push();
                matrices.scale(0.8F, 0.8F, 0.8F); // 内容物缩一圈，给边框留视觉呼吸
                // m280 内容物自转（作者：\"可以让中间的方块旋转吗\"）：只转 3D 方块模型（hasDepth），
                // 扁平物品绕 Y 转会有侧棱瞬间隐形的观感，保持静止；边框永远不转。
                // 旋转叠在 renderItem 的 display 变换之外=GUI 里绕屏幕竖轴展台式旋转，手持绕物品自身竖轴，
                // 姿态语义各模式天然正确。时间源 Util.getMeasuringTimeMs()（m148 在树先例）与 tick 无关恒匀速，
                // 按周期取模防长时运行浮点精度漂移。GUI 深度账复核：0.8×gui0.625 方块水平半对角≈0.354<边框前移0.4，不穿插。
                int spd = com.sdzjz.config.SdzjzConfig.get().compressedPackSpinDegPerSec;
                if (spd > 0 && innerModel.hasDepth()) {
                    long periodMs = 360_000L / spd; // 一整圈毫秒数
                    float deg = (net.minecraft.util.Util.getMeasuringTimeMs() % periodMs) * spd / 1000.0F;
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(deg));
                }
                ir.renderItem(inner, mode, false, matrices, vcp, light, overlay, innerModel);
                matrices.pop();
            }
        }

        ItemStack frame = new ItemStack(frameItem);
        matrices.push();
        matrices.translate(0.0F, 0.0F, mode == ModelTransformationMode.GUI ? 0.4F : 0.03F);
        ir.renderItem(frame, mode, false, matrices, vcp, light, overlay,
                ir.getModel(frame, mc.world, null, 0));
        matrices.pop();

        matrices.pop();
    }
}
