package com.sdzjz.client;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 压缩包动态图标（m243）**Fabric 1.21.1 壳**：m529 起绘制体整段下沉 {@link CompressedPackIcon}（两代共用），本类只剩加载器接口
 *  {@link BuiltinItemRendererRegistry.DynamicItemRenderer} 一句转调 + 本世代的"扁平件扫光"实现（ENCHANTMENT_GLINT_OVERRIDE 组件，1.20.5+）。
 *  1.20.1 对位 {@code CompressedPackRenderer120}；F 线 Forge 壳照此一句转调。 */
public class CompressedPackRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    private final CompressedPackIcon icon;

    public CompressedPackRenderer(Item frameItem) {
        this.icon = new CompressedPackIcon(frameItem,
                inner -> inner.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE));
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vcp, int light, int overlay) {
        icon.render(stack, mode, matrices, vcp, light, overlay);
    }
}
