package com.sdzjz.retro;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** m527（SB6，作者拍板「按 1.21.1 的来，做成一样」）：本世代**非机器物品**注册骨架——主线 {@code registry/ModItems} 的非机器件逐件对位，
 *  id/模型/贴图/lang 与主线同名同源，注册句照抄（普通件 {@code new Item(new Item.Properties())} 两代同签名，m440 清单）。
 *  第一件 core_module（m527）：配方表三建表口都自动并入它，未注册=超大工作台 0/122 可达；m528 N1 纯 Item 六件。
 *  带行为的件（抓物笼 CaptureCageItem / 压缩包 CompressedPackItem / 随身仓库 / 连接器 / 升级件）随 N 线逐刀对位——注册前先量其行为类能否上白名单（第 20 闸 --candidate），
 *  不能就世代壳各写；纯 Item 的四升级件+两包框可一刀合并。 */
public final class RetroItems {

    private RetroItems() { }

    /** 主线 {@code ModItems.CORE_MODULE = reg("core_module", new Item(new Item.Properties()))}。 */
    public static Item CORE_MODULE;
    // m528（N1）纯 Item 六件，主线 ModItems L27-36 同句 new Item(new Item.Properties())：
    // 四升级件的**行为在画布侧**（主线 xplat/node/NodeUpgrades + 画布屏 UPG 槽，引 src ModItems 未上白名单）——本世代画布尚不吃升级件（A8 升级槽待做），
    // 本刀=能拿、能合、能进浏览器，**装进机器要等 A8**（m99 升级/封顶公式随 A8 一并对位）。两包框主线也只注册不上创造栏（压缩包配方料，N2 接压缩包）。
    public static Item SPEED_UPGRADE, COUNT_UPGRADE, PARALLEL_UPGRADE, STORAGE_UPGRADE;
    public static Item COMPRESSED_PACK_FRAME, SUPER_COMPRESSED_PACK_FRAME;

    static void register() {
        CORE_MODULE = plain("core_module");
        SPEED_UPGRADE = plain("speed_upgrade");       // m528
        COUNT_UPGRADE = plain("count_upgrade");
        PARALLEL_UPGRADE = plain("parallel_upgrade");
        STORAGE_UPGRADE = plain("storage_upgrade");
        COMPRESSED_PACK_FRAME = plain("compressed_pack_frame");
        SUPER_COMPRESSED_PACK_FRAME = plain("super_compressed_pack_frame");
    }

    private static Item plain(String name) {
        return Registry.register(BuiltInRegistries.ITEM, id(name), new Item(new Item.Properties()));
    }

    /** 创造栏顺序照主线 {@code ModItems.init}：核心模块排机器之前。 */
    static void acceptAll(net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries e) {
        e.accept(CORE_MODULE);
    }

    /** 创造栏机器之后：四升级件（主线 L209-212 同序）；两包框主线不上栏，照抄。 */
    static void acceptAfterMachines(net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries e) {
        e.accept(SPEED_UPGRADE);
        e.accept(COUNT_UPGRADE);
        e.accept(PARALLEL_UPGRADE);
        e.accept(STORAGE_UPGRADE);
    }

    private static ResourceLocation id(String p) { return new ResourceLocation("sdzjz", p); }
}
