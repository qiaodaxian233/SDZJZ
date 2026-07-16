package com.sdzjz.machine;

import java.util.List;

/**
 * 内置机器表。Phase 1 先落刷线机；后续每种产物加一台（刷石机/刷铁机…同套路，配方与产物各异）。
 */
public final class Machines {
    private Machines() {}

    /** 刷线机：对应蜘蛛农场——蜘蛛自然刷出、杀了掉线，故不消耗、直接出线（每周期出 1，基础 20t）。 */
    public static final MachineDef WIRE_BRUSHER = new MachineDef(
            "wire_brusher", "minecraft:string", 1, 20, false, List.of());
}
