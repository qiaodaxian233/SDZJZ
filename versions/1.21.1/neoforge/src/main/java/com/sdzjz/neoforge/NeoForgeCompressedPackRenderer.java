package com.sdzjz.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sdzjz.client.CompressedPackIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** m537（F1d-2b）压缩包动态图标的 **NeoForge 1.21.1 壳**：对位 Fabric 的 {@code client/CompressedPackRenderer}（BuiltinItemRendererRegistry.DynamicItemRenderer 一句转调），
 *  这边是 {@link BlockEntityWithoutLevelRenderer} 子类覆写 {@code renderByItem}（六参与 Fabric 的 render 逐位同序），绘制体仍是两代共用的 {@link CompressedPackIcon}；
 *  扫光实现同 Fabric 壳（ENCHANTMENT_GLINT_OVERRIDE 组件，1.20.5+）。挂法=RegisterClientExtensionsEvent.registerItem(IClientItemExtensions.getCustomRenderer)，
 *  模型 JSON 已是 {@code builtin/entity}（两家共用同一份）。父类两个构造实参本类不用（renderByItem 整个覆写），事件期即便取到 null 也无妨。 */
public final class NeoForgeCompressedPackRenderer extends BlockEntityWithoutLevelRenderer {
    private final CompressedPackIcon icon;

    public NeoForgeCompressedPackRenderer(Item frameItem) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.icon = new CompressedPackIcon(frameItem,
                inner -> inner.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vcp, int light, int overlay) {
        icon.render(stack, mode, matrices, vcp, light, overlay);
    }
}
