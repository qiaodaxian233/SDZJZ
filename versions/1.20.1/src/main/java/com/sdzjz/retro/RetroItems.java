package com.sdzjz.retro;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** m527（SB6，作者拍板「按 1.21.1 的来，做成一样」）：本世代**非机器物品**注册骨架——主线 {@code registry/ModItems} 的非机器件逐件对位，
 *  id/模型/贴图/lang 与主线同名同源，注册句照抄（普通件 {@code new Item(new Item.Properties())} 两代同签名，m440 清单）。
 *  第一件 core_module：配方表 {@code bom/bomPacked/addSmall} 三建表口都自动并入它，未注册=超大工作台 0/122 可达（m526 判官量出）。
 *  带行为的件（抓物笼 CaptureCageItem / 压缩包 CompressedPackItem / 随身仓库 / 连接器 / 升级件）随 N 线逐刀对位——注册前先量其行为类能否上白名单（第 20 闸 --candidate），
 *  不能就世代壳各写；纯 Item 的四升级件+两包框可一刀合并。 */
public final class RetroItems {

    private RetroItems() { }

    /** 主线 {@code ModItems.CORE_MODULE = reg("core_module", new Item(new Item.Properties()))}。 */
    public static Item CORE_MODULE;

    static void register() {
        CORE_MODULE = Registry.register(BuiltInRegistries.ITEM, id("core_module"), new Item(new Item.Properties()));
    }

    /** 创造栏顺序照主线 {@code ModItems.init}：核心模块排机器之前。 */
    static void acceptAll(net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries e) {
        e.accept(CORE_MODULE);
    }

    private static ResourceLocation id(String p) { return new ResourceLocation("sdzjz", p); }
}
