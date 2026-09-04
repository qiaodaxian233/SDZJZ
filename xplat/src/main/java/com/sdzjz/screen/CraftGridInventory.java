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

    /** m523b（热修 m523 CI 1.20.1 红）：`CraftingContainer.getItems()` 两代都是抽象方法，本类必须自己实现——
     *  m523 注释写"由 SimpleInventory 协变满足"只对 1.21.1 成立（yarn 1.21.1 `SimpleInventory.method_54454 getHeldStacks()→DefaultedList`，
     *  Mojmap `SimpleContainer.getItems()→NonNullList`），**1.20.1 的 `SimpleInventory` 没有这个方法**（yarn 1.20.1 映射核过，`items` 字段私有无 getter）。
     *  返回类型钉 `NonNullList`：1.21.1 要与父类 `SimpleContainer.getItems()` 同签名才算覆写（写 `List` 会红"返回类型不兼容"），
     *  1.20.1 靠协变满足接口的 `List`。体=`NonNullList.withSize`（两代同名 method_10213）逐格装 `getItem(i)`——**同一批栈引用**，
     *  两个消费者（上面的 fillStackedContents / 1.21 接口 default `asPositionedCraftInput`）都是即取即用只读，主线结果逐位不变；
     *  与父类 1.21.1 原实现（直接回活列表）的唯一差别是结构不再"活"（setItem 换栈不反映到已取出的列表），全库无人持有这份列表。 */
    @Override public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> getItems() {
        net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> l =
                net.minecraft.core.NonNullList.withSize(this.getContainerSize(), net.minecraft.world.item.ItemStack.EMPTY);
        for (int i = 0; i < l.size(); i++) l.set(i, this.getItem(i));
        return l;
    }

    // m523（SB2b）：原 asCraftInput() 覆写删除——1.21.1 源码核过（CraftingContainer）：接口 default
    // `asCraftInput() = asPositionedCraftInput().input()`、`asPositionedCraftInput() = CraftingInput.ofPositioned(getWidth(), getHeight(), getItems())`，
    // 而 `CraftingInput.of(w,h,items) = ofPositioned(w,h,items).input()`——与被删的覆写体逐位同义，主线行为零变化；
    // 1.20.1 没有 CraftingInput 类、接口也没有此方法（m522b 作者构建"找不到符号 CraftingInput"），删了才能上白名单。
    // （m523 原注"getHeldStacks 由 SimpleInventory 协变满足"是错的——只对 1.21.1 成立，见上方 m523b getItems 覆写。）
}
