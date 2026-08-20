package com.sdzjz.block;

import net.minecraft.util.StringRepresentable;

/** 数据线某一面的连接形态：无 / 缆对缆（纯细管，连续）/ 对设备（带连接器插头）。 */
public enum CableEnd implements StringRepresentable {
    NONE("none"),
    CABLE("cable"),
    PLUG("plug");

    private final String name;

    CableEnd(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }
}
