package com.sdzjz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * m320 Sodium 图标动画保活（反射软兼容，m229 ProjectEFCompat 同刀法，零编译依赖）。
 *
 * 病灶（作者实机二分实锤：结构核心等方块动画会动、数量升级等物品图标不动、装了 Sodium）：
 * Sodium 的 "Animate Only Visible Textures"（默认开）只更新被渲染器标记过"活跃"的动画精灵
 * ——方块精灵摆在世界里被区块渲染器标活跃所以会动（m277 三块及其物品图标沾同一张精灵的光），
 * 而升级件/核心模块是**纯 GUI 物品精灵**，没有任何渲染路径给它们标活跃 → 永远冻在第 0 帧。
 *
 * 修法：每客户端 tick 把本模组的动画物品精灵经 Sodium 官方兼容 API
 * SpriteUtil.markSpriteActive 标活跃（Sodium 0.6=INSTANCE 接口方法 / 0.5=静态方法，
 * 反射两式自适应）；未装 Sodium 或 API 变脸=一次熔断静默停用，动画交回原版机制零影响。
 */
public final class SodiumSpriteKicker {
    private SodiumSpriteKicker() {}

    /** 需要保活的动画物品精灵（方块精灵由世界渲染天然保活，不必列；新增动画物品记得进表）。 */
    private static final ResourceLocation[] SPRITES = {
            ResourceLocation.of("sdzjz", "item/parallel_upgrade"),
            ResourceLocation.of("sdzjz", "item/count_upgrade"),
            ResourceLocation.of("sdzjz", "item/speed_upgrade"),
            ResourceLocation.of("sdzjz", "item/core_module"),
            ResourceLocation.of("sdzjz", "item/auto_crafter"),      // m330 五台规划器机器节点动画
            ResourceLocation.of("sdzjz", "item/crop_farm"),
            ResourceLocation.of("sdzjz", "item/brewing_tower"),
            ResourceLocation.of("sdzjz", "item/enchant_factory"),
            ResourceLocation.of("sdzjz", "item/villager_trader"),
            ResourceLocation.of("sdzjz", "item/duplicator"),      // m334 复制机脉冲动画
            // m337 批量动画化 63 张（辉光呼吸管线，漏登=装 Sodium 冻第 0 帧——m320 铁律）
            ResourceLocation.of("sdzjz", "item/amethyst_farm"),
            ResourceLocation.of("sdzjz", "item/animal_farm"),
            ResourceLocation.of("sdzjz", "item/auto_feeder"),
            ResourceLocation.of("sdzjz", "item/bamboo_farm"),
            ResourceLocation.of("sdzjz", "item/basalt_machine"),
            ResourceLocation.of("sdzjz", "item/blaze_farm"),
            ResourceLocation.of("sdzjz", "item/bone_farm"),
            ResourceLocation.of("sdzjz", "item/cactus_farm"),
            ResourceLocation.of("sdzjz", "item/carpet_machine"),
            ResourceLocation.of("sdzjz", "item/chicken_farm"),
            ResourceLocation.of("sdzjz", "item/clay_machine"),
            ResourceLocation.of("sdzjz", "item/cobble_maker"),
            ResourceLocation.of("sdzjz", "item/data_cable"),
            ResourceLocation.of("sdzjz", "item/deep_mining_platform"),
            ResourceLocation.of("sdzjz", "item/distributor_node"),
            ResourceLocation.of("sdzjz", "item/dragon_cannon"),
            ResourceLocation.of("sdzjz", "item/dripstone_farm"),
            ResourceLocation.of("sdzjz", "item/extractor_node"),
            ResourceLocation.of("sdzjz", "item/filter_node"),
            ResourceLocation.of("sdzjz", "item/flesh_farm"),
            ResourceLocation.of("sdzjz", "item/glass_kiln"),
            ResourceLocation.of("sdzjz", "item/gold_smelter"),
            ResourceLocation.of("sdzjz", "item/grindstone_recycler"),
            ResourceLocation.of("sdzjz", "item/gunpowder_farm"),
            ResourceLocation.of("sdzjz", "item/hoglin_farm"),
            ResourceLocation.of("sdzjz", "item/honey_farm"),
            ResourceLocation.of("sdzjz", "item/ice_maker"),
            ResourceLocation.of("sdzjz", "item/iron_smelter"),
            ResourceLocation.of("sdzjz", "item/linker"),
            ResourceLocation.of("sdzjz", "item/logo"),
            ResourceLocation.of("sdzjz", "item/magma_farm"),
            ResourceLocation.of("sdzjz", "item/mega_amethyst_farm"),
            ResourceLocation.of("sdzjz", "item/mega_cobble_maker"),
            ResourceLocation.of("sdzjz", "item/mega_crop_farm"),
            ResourceLocation.of("sdzjz", "item/mega_honey_farm"),
            ResourceLocation.of("sdzjz", "item/mega_piglin_barter"),
            ResourceLocation.of("sdzjz", "item/mega_pigman_tower"),
            ResourceLocation.of("sdzjz", "item/mega_slime_farm"),
            ResourceLocation.of("sdzjz", "item/mega_super_smelter"),
            ResourceLocation.of("sdzjz", "item/mega_wither_skeleton_farm"),
            ResourceLocation.of("sdzjz", "item/obsidian_maker"),
            ResourceLocation.of("sdzjz", "item/pig_farm"),
            ResourceLocation.of("sdzjz", "item/piglin_barter"),
            ResourceLocation.of("sdzjz", "item/pigman_tower"),
            ResourceLocation.of("sdzjz", "item/portable_vault"),
            ResourceLocation.of("sdzjz", "item/sand_maker"),
            ResourceLocation.of("sdzjz", "item/satellite_node"),
            ResourceLocation.of("sdzjz", "item/sculk_line"),
            ResourceLocation.of("sdzjz", "item/sensor_node"),
            ResourceLocation.of("sdzjz", "item/sheep_farm"),
            ResourceLocation.of("sdzjz", "item/slime_farm"),
            ResourceLocation.of("sdzjz", "item/snow_machine"),
            ResourceLocation.of("sdzjz", "item/storage_core"),
            ResourceLocation.of("sdzjz", "item/sugarcane_farm"),
            ResourceLocation.of("sdzjz", "item/super_smelter"),
            ResourceLocation.of("sdzjz", "item/switch_node"),
            ResourceLocation.of("sdzjz", "item/trash_node"),
            ResourceLocation.of("sdzjz", "item/tree_farm"),
            ResourceLocation.of("sdzjz", "item/villager_breeder"),
            ResourceLocation.of("sdzjz", "item/villager_discount_machine"),
            ResourceLocation.of("sdzjz", "item/wire_brusher"),
            ResourceLocation.of("sdzjz", "item/wireless_node"),
            ResourceLocation.of("sdzjz", "item/wither_skeleton_farm"),
    };

    private static boolean resolved = false, disabled = false;
    private static java.lang.reflect.Method mark;   // markSpriteActive(TextureAtlasSprite)
    private static Object markTarget;               // Sodium 0.6 的 INSTANCE；0.5 静态方法则为 null

    private static void resolve() {
        resolved = true;
        try {
            Class<?> util = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil");
            for (java.lang.reflect.Method m : util.getMethods())
                if (m.getName().equals("markSpriteActive") && m.getParameterCount() == 1) { mark = m; break; }
            if (mark == null) {
                disabled = true;
                com.sdzjz.Sdzjz.LOGGER.warn("[生电终结者] SodiumSpriteKicker: 找到 SpriteUtil 但无 markSpriteActive(1参)——Sodium API 变脸，垫片停用（图标动画可能被其可见纹理优化冻结）");
                return;
            }
            if (!java.lang.reflect.Modifier.isStatic(mark.getModifiers()))
                markTarget = util.getField("INSTANCE").get(null);
            com.sdzjz.Sdzjz.LOGGER.info("[生电终结者] SodiumSpriteKicker: 已挂接 Sodium {} 式 markSpriteActive，九 件动画物品精灵每tick保活",
                    markTarget == null ? "0.5静态" : "0.6实例");
        } catch (ClassNotFoundException e) {
            disabled = true;
            com.sdzjz.Sdzjz.LOGGER.info("[生电终结者] SodiumSpriteKicker: 未检测到 Sodium 兼容 API（未装 Sodium 属正常；装了但走到这=版本过老无该 API），垫片停用");
        } catch (Throwable t) {
            disabled = true;
            com.sdzjz.Sdzjz.LOGGER.warn("[生电终结者] SodiumSpriteKicker: 挂接异常已熔断：{}", t.toString());
        }
    }

    /** SdzjzClient 注册进 END_CLIENT_TICK：4 次表查+反射调用/tick 纳秒级；任何异常一次熔断永不再试。 */
    public static void tick(Minecraft client) {
        if (disabled || client == null || !com.sdzjz.config.SdzjzConfig.get().sodiumIconAnimFix) return;
        if (!resolved) resolve();
        if (disabled) return;
        try {
            TextureAtlas atlas = client.getBakedModelManager()
                    .getAtlas(TextureAtlas.BLOCK_ATLAS_TEXTURE); // 1.21 物品精灵同在方块图集
            for (ResourceLocation id : SPRITES) {
                TextureAtlasSprite s = atlas.getSprite(id);
                if (s != null) mark.invoke(markTarget, s);
            }
        } catch (Throwable t) {
            disabled = true; // 反射链任何一环炸=熔断，绝不拖客户端 tick
            com.sdzjz.Sdzjz.LOGGER.warn("[生电终结者] SodiumSpriteKicker: 保活调用异常已熔断：{}", t.toString());
        }
    }
}
