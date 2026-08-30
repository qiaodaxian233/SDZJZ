package com.sdzjz.node;

import net.minecraft.world.item.ItemStack;

/**
 * m481（真移植 D 阶段先行·第二域）：路由脑**判定函数探针**——把两代结构核心里签名已经对齐的
 * 四个判定函数声明成一个接口，好让 {@link RouteDomainAssertions} 那套断言两代各喂自己的实现
 * 跑同一份（照 m480 存储域、m372 配方域的样板）。
 *
 * <p><b>为什么只有四口</b>：路由域与存储域不同——存储域十三个方法两代**本来就同名同签名**
 * （各加一行 implements 即可），路由域只有四个对得齐：
 * <ul>
 *   <li>{@code accepts}（主线 accepts0）(Level,int,String) —— 收料面；</li>
 *   <li>{@code chainWants}（主线 chainWants0）(Level,int,String,int,Set) —— 需求面；</li>
 *   <li>{@code sensorOpen}(Level,int) —— 传感器闸门；</li>
 *   <li>{@code extractorLive}(Level,int,ItemStack) —— 抽取节点是否在抽。</li>
 * </ul>
 * 另两个**实现形状不同，本刀不纳入契约、显式记档留给 C1 统一**：
 * {@code allGatesClosed} 主线是 (Level,int[] targets)（m355 数组化，收目标数组），1.20.1 是
 * (Level,int from)（收起点下标自己遍历出边）；{@code chainEndsInTrash} 两代参数个数不同。
 * 这两处差异不是语义差，是我在 1.20.1 仿写时的切分差——C1 路由脑下沉时一并归一。
 *
 * <p>实现方各写四行转发（纯加法，不改任何现有方法体）；{@code level} 是不透明代际句柄，
 * 与 RecipeAccess/StackCodec 同一约法。
 */
public interface RouteBrainProbe {

    /** 收料面：目标节点当下肯不肯收这个 id。 */
    boolean probeAccepts(Object level, int target, String id);

    /** 需求面：从该节点出发，沿出线链有没有人真要这个 id（深度与去重由实现方起头）。 */
    boolean probeChainWants(Object level, int from, String id);

    /** 传感器闸门：监测条件当下是否放行。 */
    boolean probeSensorOpen(Object level, int i);

    /** 抽取节点当下是否在抽（手动开关 ∩ 感应放行）。 */
    boolean probeExtractorLive(Object level, int i, ItemStack st);
}
