package com.sdzjz.graph;

/**
 * MVP 节点类型 id。后续要加模块（切石/锻造/酿造/逻辑/路由…）时，
 * 这里加常量即可；再往后可升级成注册表让其他 mod 也能注册节点类型。
 */
public final class NodeKinds {
    private NodeKinds() {}

    /** 一头进料：相邻箱子 / 数据仓储，或直接供料（config 可选是否免费）。 */
    public static final String INPUT = "input";

    /** 3×3 合成模块（config = 配方 / 产物 id）。 */
    public static final String CRAFT = "craft";

    /** 熔炼模块（熔炉 / 高炉 / 烟熏炉）。 */
    public static final String SMELT = "smelt";

    /** 另一头出料：箱子 / 仓储 / 世界（喷射）。 */
    public static final String OUTPUT = "output";
}
