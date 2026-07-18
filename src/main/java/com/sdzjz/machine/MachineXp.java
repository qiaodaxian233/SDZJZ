package com.sdzjz.machine;

import java.util.HashMap;
import java.util.Map;

/**
 * 经验表（对齐原版）：机器每周期经验 = 对应生物击杀经验 或 每件熔炼经验；
 * 采集类/切石/猪灵交易/铁傀儡 原版就不给经验 → 0（不入表）。
 * 累积规则：xp × 同时运行台数（并列=多杀，数量升级只放大掉落不放大经验，诚实对齐）。
 */
public final class MachineXp {
    private MachineXp() {}

    private static final Map<String, Double> MACHINE = new HashMap<>();
    private static final Map<String, Double> MOB = new HashMap<>();

    static {
        // ---- 刷怪类机器（原版击杀经验）----
        for (String m : new String[]{"wire_brusher", "bone_farm", "gunpowder_farm", "flesh_farm", "pearl_farm",
                "witch_tower", "swamp_spawner", "mob_tower", "drowned_tower", "wither_skeleton_farm",
                "raid_tower", "pigman_tower", "ghast_tower", "shulker_farm"}) MACHINE.put(m, 5.0);
        MACHINE.put("slime_farm", 2.0);
        MACHINE.put("magma_farm", 2.0);
        MACHINE.put("guardian_farm", 10.0);
        MACHINE.put("blaze_farm", 10.0);
        MACHINE.put("breeze_farm", 10.0);
        // ---- 熔炼类（原版每件经验）----
        MACHINE.put("iron_smelter", 0.7);
        MACHINE.put("super_smelter", 0.7);
        MACHINE.put("gold_smelter", 1.0);
        MACHINE.put("charcoal_kiln", 0.15);
        MACHINE.put("glass_kiln", 0.1);

        // ---- 抓物笼子生物（原版击杀经验）----
        for (String m : new String[]{"zombie", "husk", "stray", "skeleton", "creeper", "spider", "cave_spider",
                "enderman", "witch", "piglin", "zombified_piglin", "hoglin", "phantom", "bogged",
                "wither_skeleton", "shulker", "ghast"}) MOB.put("minecraft:" + m, 5.0);
        for (String m : new String[]{"blaze", "guardian", "elder_guardian", "breeze"}) MOB.put("minecraft:" + m, 10.0);
        for (String m : new String[]{"slime", "magma_cube"}) MOB.put("minecraft:" + m, 2.0);
        for (String m : new String[]{"cow", "pig", "sheep", "chicken", "rabbit", "mooshroom"}) MOB.put("minecraft:" + m, 2.0);
        for (String m : new String[]{"squid", "glow_squid", "cod", "salmon", "pufferfish", "bee"}) MOB.put("minecraft:" + m, 1.0);
        // iron_golem 原版 0 经验，不入表
    }

    public static double of(String machineId) {
        return MACHINE.getOrDefault(machineId, 0.0);
    }

    public static double mob(String mobId) {
        return MOB.getOrDefault(mobId, 0.0);
    }
}
