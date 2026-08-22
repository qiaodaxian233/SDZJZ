package com.sdzjz.node;

import net.minecraft.world.item.ItemStack;

/**
 * m430 绞杀者 mA1（GraphNbt 方案 A 第一刀，docs/GraphNbt拆分方案_m429.md 作者拍板）：
 * 画布渲染子集状态对象——m275 清单认定的「画布客户端消费面全集」12+1 字段自
 * StructureCoreBlockEntity 原样搬家（声明一字未改，仅 private→public；依附注释随迁）。
 * SCBE 持唯一实例 g，SCBE 内全部引用机械前缀 g. / be.g.（逐名计数断言）。
 * 本刀只动字段归属；渲染编解码（writeRenderNbt/readRenderNbt）mA2 迁入本类成员方法。
 * 纯状态容器：零方法零逻辑，字段语义与同步节奏的注释仍以 SCBE 侧使用点为准。
 */
public final class CanvasGraphState {

    public final java.util.List<ItemStack> machineNodes = new java.util.ArrayList<>();
    public final java.util.List<int[]> connections = new java.util.ArrayList<>(); // {from, to} 节点下标
    public final java.util.LinkedHashMap<Integer, String> groupNames = new java.util.LinkedHashMap<>(); // m191 画布分组 id→名（成员归属存各节点栈 NBT "gp"，随栈走免下标重映射）

    // ===== 存储/终端接口节点（画布右侧显示，连了几个显示几个） =====
    /** 已扫描到的接口端点：{posLong, kind}，kind 0=绑定 1=有线 2=无线 3=卫星 4=离线(仅被连线引用) 5=数据终端。 */
    public final java.util.List<long[]> storageEndpoints = new java.util.ArrayList<>();
    public final java.util.List<String> storageEndpointDims = new java.util.ArrayList<>(); // 与上表同序的维度 id
    public final java.util.Map<Long, int[]> storageNodePos = new java.util.HashMap<>();    // posLong → 画布坐标
    /** 机器↔存储 定向连线：{machineIndex, posLong, dir}，dir 0=机器→存储(产出) 1=存储→机器(供料)。 */
    public final java.util.List<long[]> storageEdges = new java.util.ArrayList<>();
    public final java.util.List<String> storageEdgeDims = new java.util.ArrayList<>();

    // 节点状态灯：0=待机 1=正常(绿) 2=阻塞/关闸(黄) 3=缺料(红)（同步节奏与 statusDirty 在 SCBE）
    public final java.util.List<Integer> nodeStatus = new java.util.ArrayList<>();
    // m178 阻塞原因（错误解释）：与 nodeStatus 平行同步，卡面黄/红灯常显人话原因
    public final java.util.List<String> nodeReason = new java.util.ArrayList<>();

    // m85：总线库存（网络前10物品，画布顶栏「存储总线（网络库存）」展示）
    public final java.util.List<String> busTopIds = new java.util.ArrayList<>();
    public final java.util.List<Long> busTopCounts = new java.util.ArrayList<>();

    // m86：实测产量快照（分钟滚动窗口的结算值；计量窗口 prodWin/prodWinStart 留 SCBE）
    public long prodPerMin = 0;
}
