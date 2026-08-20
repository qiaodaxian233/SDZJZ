package com.sdzjz.screen;

/** m201 合成网格库存：SimpleContainer 加挂原版 CraftingContainer 接口（3×3）。
 *  作用：① 终端 handler 接原版工作台接口（AbstractRecipeScreenHandler）的地基；
 *  ② EMI 的 CoercedRecipeHandler 只认"CraftingResultSlot 且其 input 为 CraftingContainer"，
 *  挂上即零插件点亮 EMI 合成填充（EMI 1.21 源码 EmiRecipeFiller L87 实锤）。
 *  持久化/监听全走 SimpleContainer 原路径，BE 落盘零变化。 */
public class CraftGridInventory extends net.minecraft.world.SimpleContainer
        implements net.minecraft.world.inventory.CraftingContainer {

    public CraftGridInventory() { super(9); }

    @Override public int getWidth() { return 3; }

    @Override public int getHeight() { return 3; }

    /** 照原版 CraftingInventory：逐格 addUnenchantedInput（映射核过 method_7404）。 */
    @Override public void provideRecipeInputs(net.minecraft.world.entity.player.StackedContents finder) {
        for (net.minecraft.world.item.ItemStack st : this.getHeldStacks()) finder.addUnenchantedInput(st);
    }

    /** 接口很可能已有同签名 default（1.21 便捷法），显式覆写两头保险；getHeldStacks 由 SimpleContainer 协变满足。 */
    @Override public net.minecraft.world.item.crafting.CraftingInput createRecipeInput() {
        return net.minecraft.world.item.crafting.CraftingInput.create(3, 3, this.getHeldStacks());
    }
}
