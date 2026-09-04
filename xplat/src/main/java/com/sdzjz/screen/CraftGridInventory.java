package com.sdzjz.screen;

/** m201 合成网格库存：SimpleInventory 加挂原版 RecipeInputInventory 接口（3×3）。
 *  作用：① 终端 handler 接原版工作台接口（AbstractRecipeScreenHandler）的地基；
 *  ② EMI 的 CoercedRecipeHandler 只认"CraftingResultSlot 且其 input 为 RecipeInputInventory"，
 *  挂上即零插件点亮 EMI 合成填充（EMI 1.21 源码 EmiRecipeFiller L87 实锤）。
 *  持久化/监听全走 SimpleInventory 原路径，BE 落盘零变化。 */
public class CraftGridInventory extends net.minecraft.world.SimpleContainer
        implements net.minecraft.world.inventory.CraftingContainer {

    public CraftGridInventory() { super(9); }

    @Override public int getWidth() { return 3; }

    @Override public int getHeight() { return 3; }

    /** 照原版 CraftingInventory：逐格 addUnenchantedInput（映射核过 method_7404）。 */
    @Override public void fillStackedContents(net.minecraft.world.entity.player.StackedContents finder) {
        for (net.minecraft.world.item.ItemStack st : this.getItems()) finder.accountSimpleStack(st);
    }

    // m523（SB2b）：原 asCraftInput() 覆写删除——1.21.1 源码核过（CraftingContainer）：接口 default
    // `asCraftInput() = asPositionedCraftInput().input()`、`asPositionedCraftInput() = CraftingInput.ofPositioned(getWidth(), getHeight(), getItems())`，
    // 而 `CraftingInput.of(w,h,items) = ofPositioned(w,h,items).input()`——与被删的覆写体逐位同义，主线行为零变化；
    // 1.20.1 没有 CraftingInput 类、接口也没有此方法（m522b 作者构建"找不到符号 CraftingInput"），删了才能上白名单。getHeldStacks 由 SimpleInventory 协变满足。
}
