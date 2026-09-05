package com.sdzjz.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * m437 物品附加数据平台口（P-A 收口刀，docs/ItemDataAccess方案_m436.md 作者拍板"推荐"）。
 *
 * <p>全仓 CUSTOM_DATA 109 触点/22 文件收口的唯一门面——1.21 世代实现={@link ComponentItemData}
 * （组件三式原样），1.20.1 世代届时给 getNbt/setNbt 对位实现，物品数据的版本差被钉死在一个
 * Impl 文件里。四口语义沿 NodeTags 既有约定（那三口就是三式样板，现改走本门面）。
 *
 * <p><b>write 语义零改动记档</b>：m436 稿曾设计"write(空表)=清除"归一——本刀为保零行为
 * **推迟**（原生 set 空组件与 setNbt 空表在两代里同样造成"不与裸物品混堆"，语义平行，先不动）；
 * 显式清除走 {@link #clear}（对位原 remove(CUSTOM_DATA)）。归一与否另立里程碑拍板。
 */
public final class ItemData {

    private ItemData() { }

    /** 世代要给的五个口（m437）。 */
    public interface Impl {
        CompoundTag copyOf(ItemStack s);
        CompoundTag view(ItemStack s);
        void write(ItemStack s, CompoundTag n);
        boolean has(ItemStack s);
        void clear(ItemStack s);
        /** m522（SB2）：物品 id 字符串→物品（Legacy `ResourceLocation.parse` / Retro `new ResourceLocation`）。服务端可用——
         *  `SciSkin.Gfx.id`（m484）在 client 包，ScreenHandler/配方表不能引；不存在的 id 两代都回 AIR（注册表默认值）。 */
        net.minecraft.world.item.Item itemById(String id);
        /** m522（SB2）：清自定义名（Legacy `remove(DataComponents.CUSTOM_NAME)` / Retro `resetHoverName()`）。 */
        void clearCustomName(ItemStack s);
        /** m530（N2b）：设自定义名（1.21 `set(CUSTOM_NAME)` / 1.20.1 `setHoverName`）——CaptureCageItem 原句 `caged.set(DataComponents.CUSTOM_NAME, …)`。 */
        void setCustomName(ItemStack s, net.minecraft.network.chat.Component name);
        /** m522（SB2）：id 字符串→ResourceLocation（两代同 FQN，只差构造：parse / new）。非物品注册表（实体类型等）用它。 */
        net.minecraft.resources.ResourceLocation id(String s);
    }

    private static Impl impl;

    /** 加载器入口首段调（重复安装直接炸出来）。 */
    public static void install(Impl i) {
        if (impl != null) throw new IllegalStateException("ItemData 平台实现重复安装");
        impl = i;
    }

    private static Impl req() {
        if (impl == null) throw new IllegalStateException("ItemData 平台实现未安装：加载器入口须先调 ItemData.install(...)（Fabric=Sdzjz.onInitialize 首段）");
        return impl;
    }

    /** 拷贝读（写路起手口：改完必须 write 回写，否则丢写——NodeTags 原约定原句）。无数据返回新空表。 */
    public static CompoundTag copyOf(ItemStack s) { return req().copyOf(s); }

    /** 只读视图（热路径省拷贝；**只准读不准改**）。无数据返回共享空表。 */
    public static CompoundTag view(ItemStack s) { return req().view(s); }

    /** 回写（语义=原生 set，见类注释零改动记档）。 */
    public static void write(ItemStack s, CompoundTag n) { req().write(s, n); }

    /** 有无附加数据（可空 get 判空形态的收口口，后续刀用）。 */
    public static boolean has(ItemStack s) { return req().has(s); }

    /** 显式清除（对位原 remove(CUSTOM_DATA)，m128"变裸"语义）。 */
    public static void clear(ItemStack s) { req().clear(s); }
    /** m522：id→物品（服务端可用的 id 解析口）。 */
    public static net.minecraft.world.item.Item itemById(String id) { return req().itemById(id); }
    /** m522：清自定义名。 */
    public static void clearCustomName(ItemStack s) { req().clearCustomName(s); }
    public static void setCustomName(ItemStack s, net.minecraft.network.chat.Component name) { req().setCustomName(s, name); } // m530
    /** m522：id 字符串→ResourceLocation（服务端可用；client 侧同义口是 SciSkin.Gfx.id）。 */
    public static net.minecraft.resources.ResourceLocation id(String s) { return req().id(s); }
}
