package com.sdzjz.machine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 抓物笼子里生物类型 → 掉落表。未收录的生物返回 null（不产出）。 */
public final class MobDrops {

    private static final Map<String, List<MachineDef.Drop>> T = new HashMap<>();

    private static MachineDef.Drop d(String item, int min, int max) {
        return new MachineDef.Drop(item, min, max, 1.0f);
    }

    private static MachineDef.Drop d(String item, int min, int max, float chance) {
        return new MachineDef.Drop(item, min, max, chance);
    }

    private static void put(String mob, MachineDef.Drop... drops) {
        T.put(mob, List.of(drops));
    }

    static {
        put("minecraft:zombie", d("minecraft:rotten_flesh", 0, 2));
        put("minecraft:husk", d("minecraft:rotten_flesh", 0, 2));
        put("minecraft:skeleton", d("minecraft:bone", 0, 2), d("minecraft:arrow", 0, 2));
        put("minecraft:stray", d("minecraft:bone", 0, 2), d("minecraft:arrow", 0, 2));
        put("minecraft:creeper", d("minecraft:gunpowder", 0, 2));
        put("minecraft:spider", d("minecraft:string", 0, 2), d("minecraft:spider_eye", 0, 1, 0.33f));
        put("minecraft:cave_spider", d("minecraft:string", 0, 2), d("minecraft:spider_eye", 0, 1, 0.33f));
        put("minecraft:cow", d("minecraft:beef", 1, 3), d("minecraft:leather", 0, 2));
        put("minecraft:mooshroom", d("minecraft:beef", 1, 3), d("minecraft:leather", 0, 2));
        put("minecraft:pig", d("minecraft:porkchop", 1, 3));
        put("minecraft:chicken", d("minecraft:chicken", 1, 1), d("minecraft:feather", 0, 2), d("minecraft:egg", 0, 1, 0.2f));
        put("minecraft:sheep", d("minecraft:mutton", 1, 2), d("minecraft:white_wool", 1, 1));
        put("minecraft:rabbit", d("minecraft:rabbit", 0, 1), d("minecraft:rabbit_hide", 0, 1), d("minecraft:rabbit_foot", 0, 1, 0.1f));
        put("minecraft:enderman", d("minecraft:ender_pearl", 0, 1));
        put("minecraft:blaze", d("minecraft:blaze_rod", 0, 1));
        put("minecraft:slime", d("minecraft:slime_ball", 0, 2));
        put("minecraft:magma_cube", d("minecraft:magma_cream", 0, 1));
        put("minecraft:witch", d("minecraft:redstone", 0, 2), d("minecraft:glowstone_dust", 0, 2),
                d("minecraft:sugar", 0, 1), d("minecraft:gunpowder", 0, 1), d("minecraft:glass_bottle", 0, 1, 0.3f));
        put("minecraft:guardian", d("minecraft:prismarine_shard", 0, 2), d("minecraft:prismarine_crystals", 0, 1), d("minecraft:cod", 0, 1, 0.4f));
        put("minecraft:elder_guardian", d("minecraft:prismarine_shard", 1, 3), d("minecraft:prismarine_crystals", 0, 2), d("minecraft:wet_sponge", 0, 1, 0.2f));
        put("minecraft:cod", d("minecraft:cod", 1, 1));
        put("minecraft:salmon", d("minecraft:salmon", 1, 1));
        put("minecraft:pufferfish", d("minecraft:pufferfish", 1, 1));
        put("minecraft:squid", d("minecraft:ink_sac", 1, 3));
        put("minecraft:glow_squid", d("minecraft:glow_ink_sac", 1, 3));
        put("minecraft:wither_skeleton", d("minecraft:bone", 0, 2), d("minecraft:coal", 0, 1), d("minecraft:wither_skeleton_skull", 0, 1, 0.025f));
        put("minecraft:piglin", d("minecraft:gold_nugget", 0, 3));
        put("minecraft:hoglin", d("minecraft:porkchop", 2, 4), d("minecraft:leather", 0, 1));
        put("minecraft:ghast", d("minecraft:ghast_tear", 0, 1), d("minecraft:gunpowder", 0, 2));
        put("minecraft:zombified_piglin", d("minecraft:rotten_flesh", 0, 1), d("minecraft:gold_nugget", 0, 1), d("minecraft:gold_ingot", 0, 1, 0.05f));
        put("minecraft:phantom", d("minecraft:phantom_membrane", 0, 1));
        put("minecraft:shulker", d("minecraft:shulker_shell", 0, 1, 0.5f));
        put("minecraft:iron_golem", d("minecraft:iron_ingot", 3, 5), d("minecraft:poppy", 0, 2));
        put("minecraft:bee", d("minecraft:honeycomb", 0, 1, 0.5f));
        put("minecraft:bogged", d("minecraft:bone", 0, 2), d("minecraft:arrow", 0, 2));
        put("minecraft:breeze", d("minecraft:breeze_rod", 0, 1));
    }

    public static List<MachineDef.Drop> get(String mobId) {
        return T.get(mobId);
    }
}
