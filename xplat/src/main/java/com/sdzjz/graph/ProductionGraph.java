package com.sdzjz.graph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 生产图：ComfyUI 式「节点 + 连线」的纯数据模型。
 * 一头输入 → 中间若干模块节点 → 另一头输出。
 * 从第一天起就按「节点 + 边」存，画布（编辑）与运行时（执行）共用同一份数据；
 * 用 Codec 序列化进方块实体 / 数据组件，服务端执行、客户端只编辑走 packet 同步。
 */
public record ProductionGraph(List<Node> nodes, List<Edge> edges) {

    /** 画布上的一个节点。type 见 {@link NodeKinds}；config 存类型相关配置（如合成节点的配方 id）。 */
    public record Node(String id, String type, int x, int y, Optional<String> config) {
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Node::id),
                Codec.STRING.fieldOf("type").forGetter(Node::type),
                Codec.INT.fieldOf("x").forGetter(Node::x),
                Codec.INT.fieldOf("y").forGetter(Node::y),
                Codec.STRING.optionalFieldOf("config").forGetter(Node::config)
        ).apply(i, Node::new));
    }

    /** 一条连线：from 节点的第 fromPort 个输出口 → to 节点的第 toPort 个输入口。 */
    public record Edge(String from, int fromPort, String to, int toPort) {
        public static final Codec<Edge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("from").forGetter(Edge::from),
                Codec.INT.fieldOf("fromPort").forGetter(Edge::fromPort),
                Codec.STRING.fieldOf("to").forGetter(Edge::to),
                Codec.INT.fieldOf("toPort").forGetter(Edge::toPort)
        ).apply(i, Edge::new));
    }

    public static final Codec<ProductionGraph> CODEC = RecordCodecBuilder.create(i -> i.group(
            Node.CODEC.listOf().fieldOf("nodes").forGetter(ProductionGraph::nodes),
            Edge.CODEC.listOf().fieldOf("edges").forGetter(ProductionGraph::edges)
    ).apply(i, ProductionGraph::new));

    public static final ProductionGraph EMPTY = new ProductionGraph(List.of(), List.of());

    /** 加节点（返回新图，保持不可变）。 */
    public ProductionGraph withNode(Node n) {
        List<Node> ns = new ArrayList<>(nodes);
        ns.add(n);
        return new ProductionGraph(ns, edges);
    }

    /** 加连线（返回新图）。 */
    public ProductionGraph withEdge(Edge e) {
        List<Edge> es = new ArrayList<>(edges);
        es.add(e);
        return new ProductionGraph(nodes, es);
    }

    public Node findNode(String id) {
        for (Node n : nodes) {
            if (n.id().equals(id)) return n;
        }
        return null;
    }
}
