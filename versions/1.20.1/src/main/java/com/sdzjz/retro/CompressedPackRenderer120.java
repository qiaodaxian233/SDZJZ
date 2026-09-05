package com.sdzjz.retro;

import com.sdzjz.client.CompressedPackIcon;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** m529（N2a）压缩包动态图标 **Fabric 1.20.1 壳**：绘制体走两代共用件 {@link CompressedPackIcon}，本类只剩
 *  {@code DynamicItemRenderer} 一句转调 + 本世代"扁平件扫光"实现：1.20.1 没有 ENCHANTMENT_GLINT_OVERRIDE 组件，
 *  给临时展示栈挂一个附魔标记（{@code enchant(UNBREAKING,1)}）→ {@code hasFoil()} 为真 = 原版流光同效；临时栈不碰真实物品。 */
public final class CompressedPackRenderer120 implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    private final CompressedPackIcon icon;

    public CompressedPackRenderer120(Item frameItem) {
        this.icon = new CompressedPackIcon(frameItem,
                inner -> inner.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1));
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vcp, int light, int overlay) {
        icon.render(stack, mode, matrices, vcp, light, overlay);
    }
}
