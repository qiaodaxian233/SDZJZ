package com.sdzjz.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

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
    private static final Identifier[] SPRITES = {
            Identifier.of("sdzjz", "item/parallel_upgrade"),
            Identifier.of("sdzjz", "item/count_upgrade"),
            Identifier.of("sdzjz", "item/speed_upgrade"),
            Identifier.of("sdzjz", "item/core_module"),
            Identifier.of("sdzjz", "item/auto_crafter"),      // m330 五台规划器机器节点动画
            Identifier.of("sdzjz", "item/crop_farm"),
            Identifier.of("sdzjz", "item/brewing_tower"),
            Identifier.of("sdzjz", "item/enchant_factory"),
            Identifier.of("sdzjz", "item/villager_trader"),
    };

    private static boolean resolved = false, disabled = false;
    private static java.lang.reflect.Method mark;   // markSpriteActive(Sprite)
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
    public static void tick(MinecraftClient client) {
        if (disabled || client == null || !com.sdzjz.config.SdzjzConfig.get().sodiumIconAnimFix) return;
        if (!resolved) resolve();
        if (disabled) return;
        try {
            SpriteAtlasTexture atlas = client.getBakedModelManager()
                    .getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE); // 1.21 物品精灵同在方块图集
            for (Identifier id : SPRITES) {
                Sprite s = atlas.getSprite(id);
                if (s != null) mark.invoke(markTarget, s);
            }
        } catch (Throwable t) {
            disabled = true; // 反射链任何一环炸=熔断，绝不拖客户端 tick
            com.sdzjz.Sdzjz.LOGGER.warn("[生电终结者] SodiumSpriteKicker: 保活调用异常已熔断：{}", t.toString());
        }
    }
}
