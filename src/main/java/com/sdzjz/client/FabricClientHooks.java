package com.sdzjz.client;

import net.minecraft.client.Minecraft;

/** m435：{@link ClientHooks.Impl} 的 Fabric 实现——m405 门面里的四段 Fabric 内脏原样搬来，
 *  m393 判空守卫一行未改。 */
public final class FabricClientHooks implements ClientHooks.Impl {

    @Override
    public void onClientTickEnd(java.util.function.Consumer<Minecraft> h) {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(h::accept);
    }

    @Override
    public void onItemTooltip(ClientHooks.Tooltip h) {
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
                (stack, tooltipContext, tooltipType, lines) -> h.append(stack, lines));
    }

    @Override
    public net.minecraft.client.KeyMapping registerKey(String translationKey, int glfwKey, String category) {
        return net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.KeyMapping(translationKey,
                        com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, glfwKey, category));
    }

    @Override
    public void onWorldDrawAfterEntities(ClientHooks.WorldDraw h) {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (ctx.matrixStack() == null || ctx.consumers() == null || ctx.camera() == null) return;
            h.draw(ctx.matrixStack(), ctx.consumers(), ctx.camera().getPosition());
        });
    }
}
