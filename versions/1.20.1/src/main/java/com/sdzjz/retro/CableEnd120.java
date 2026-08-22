package com.sdzjz.retro;

import net.minecraft.util.StringRepresentable;

/** m444：数据线某一面的连接形态——语义蓝本=xplat {@code CableEnd}（xplat 未挂本世代，对位新写）。
 *  序列化名 none/cable/plug 与根仓 blockstate 多部件资产键同源（资产文件本身即同一份拷贝）。 */
enum CableEnd120 implements StringRepresentable {
    NONE("none"),
    CABLE("cable"),
    PLUG("plug");

    private final String name;

    CableEnd120(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
