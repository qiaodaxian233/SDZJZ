package com.sdzjz.node;

import net.minecraft.world.item.ItemStack;

/**
 * m481（真移植 D 阶段先行·第二域）：路由脑**跨代行为契约**——同一套断言，两个实现：
 * 1.21.1 的 GameTest 喂 StructureCoreBlockEntity，1.20.1 的喂 StructureCore120。
 * 判官只此一份（配方域 m372 → 存储域 m480 → 本刀路由域，第三次推广）。
 *
 * <p><b>它先于 C1 存在</b>：C 阶段第一刀要把路由脑（accepts/chainWants 等）下沉成共用代码，
 * 下沉之后这套断言必须继续全绿——**契约先立、手术后做**。而且路由域比存储域更需要这层保险：
 * 它的语义里有好几条**反直觉**的（见下），靠人脑记「我没改语义」最容易出事的就是这种地方。
 *
 * <p><b>场景由各代判官自己搭</b>（两代建画布/加节点的写法本就不同），断言只吃「哪个下标是什么」，
 * 只做判定与断言，不碰世界、不改配置。
 *
 * <p><b>六条判定（全部成对：收料面 accepts × 需求面 chainWants）</b>：
 * ① 过滤器：白名单内两面都放行，名单外两面都不要；
 * ② 开关：关着两面都拒，开着两面都放；
 * ③ <b>传感器——两面刻意不同的那一格</b>：关闸时 accepts 拒收，但 chainWants <b>照样放行</b>
 *    （蓝本原注「闸门在下发阶段生效」）。这是整个路由域最反直觉的一条，也是 m131b→m132-6
 *    血案的正中心，两代必须一致；
 * ④ 暂停：两面都拒（m110b，暂停=不收不要）；
 * ⑤ 垃圾桶：accepts 看安全白名单，chainWants <b>无条件为真</b>（m153 授权语义：销毁线永远「要」）；
 * ⑥ 通用耗料机：吃的 id 两面都放，不吃的两面都拒。
 *
 * <p>失败=AssertionError 带中文病灶信息；两代的包装判官各自翻译成 GameTest 失败。
 */
public final class RouteDomainAssertions {

    private RouteDomainAssertions() { }

    private static void chk(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    /** 成对断言的公共形状：收料面与需求面各自的期望值。 */
    private static void 成对(RouteBrainProbe p, Object lvl, int idx, String id,
                             boolean 期望收, boolean 期望要, String 场景) {
        boolean 收 = p.probeAccepts(lvl, idx, id);
        boolean 要 = p.probeChainWants(lvl, idx, id);
        chk(收 == 期望收, 场景 + "：收料面 accepts(" + id + ") 该是 " + 期望收 + "，实得 " + 收);
        chk(要 == 期望要, 场景 + "：需求面 chainWants(" + id + ") 该是 " + 期望要 + "，实得 " + 要);
    }

    /** ① 过滤器成对：白名单内两面放行，名单外两面都不要。 */
    public static void 过滤器(RouteBrainProbe p, Object lvl, int idx, String 放行id, String 拦截id) {
        chk(p.probeAccepts(lvl, idx, 放行id), "过滤器：白名单内的该收，实得拒收 " + 放行id);
        chk(!p.probeAccepts(lvl, idx, 拦截id), "过滤器：名单外的该拒收，实得收下 " + 拦截id);
        chk(!p.probeChainWants(lvl, idx, 拦截id), "过滤器：名单外的需求面也该说不要（成对），实得要 " + 拦截id);
    }

    /** ② 开关成对：关着两面都拒。（开着的情形由调用方翻转后再调一次。） */
    public static void 开关关着(RouteBrainProbe p, Object lvl, int idx, String id) {
        chk(!p.probeAccepts(lvl, idx, id), "开关关着：收料面该拒");
        chk(!p.probeChainWants(lvl, idx, id), "开关关着：需求面也该拒（成对）");
    }

    public static void 开关开着(RouteBrainProbe p, Object lvl, int idx, String id) {
        chk(p.probeAccepts(lvl, idx, id), "开关开着：收料面该放行");
        chk(p.probeChainWants(lvl, idx, id), "开关开着：需求面该放行");
    }

    /**
     * ③ <b>传感器——两面刻意不同的那一格</b>。关闸时收料面拒、需求面**照样放行**
     * （蓝本「闸门在下发阶段生效」）。这条是整个契约里最值钱的断言：它长得像 bug，
     * 所以最容易在下沉时被「顺手改对」，一改就是 m132-6 血案重演。
     */
    public static void 传感器关闸时两面刻意不同(RouteBrainProbe p, Object lvl, int idx, String id) {
        chk(!p.probeSensorOpen(lvl, idx), "传感器场景前提：本例该是关闸状态");
        chk(!p.probeAccepts(lvl, idx, id), "传感器关闸：收料面该拒（闸门在收料阶段生效）");
        chk(p.probeChainWants(lvl, idx, id), "传感器关闸：需求面**照样放行**——闸门在下发阶段生效，"
                + "这不是 bug，改成一致会让上游停产（m131b→m132-6 血案）");
    }

    public static void 传感器开闸时两面放行(RouteBrainProbe p, Object lvl, int idx, String id) {
        chk(p.probeSensorOpen(lvl, idx), "传感器场景前提：本例该是开闸状态");
        成对(p, lvl, idx, id, true, true, "传感器开闸");
    }

    /** ④ 暂停成对：两面都拒（m110b）。 */
    public static void 暂停(RouteBrainProbe p, Object lvl, int idx, String id) {
        chk(!p.probeAccepts(lvl, idx, id), "已暂停：收料面该拒");
        chk(!p.probeChainWants(lvl, idx, id), "已暂停：需求面该拒（成对）");
    }

    /** ⑤ 垃圾桶：accepts 看安全白名单；chainWants **无条件为真**（m153 授权语义）。 */
    public static void 垃圾桶授权语义(RouteBrainProbe p, Object lvl, int idx, String 名单内, String 名单外) {
        chk(p.probeChainWants(lvl, idx, 名单内), "垃圾桶：需求面该无条件为真（m153 销毁线永远要）");
        chk(p.probeChainWants(lvl, idx, 名单外), "垃圾桶：需求面对名单外的也该为真（授权语义与安全桶白名单是两回事）");
        chk(p.probeAccepts(lvl, idx, 名单内), "垃圾桶：白名单内的该真收下（m160 安全桶）");
        chk(!p.probeAccepts(lvl, idx, 名单外), "垃圾桶：名单外的收料面该拒（m160 安全桶不许误吞）");
    }

    /** ⑥ 通用耗料机：吃的两面放，不吃的两面拒。 */
    public static void 耗料机(RouteBrainProbe p, Object lvl, int idx, String 吃的, String 不吃的) {
        成对(p, lvl, idx, 吃的, true, true, "耗料机（吃的）");
        成对(p, lvl, idx, 不吃的, false, false, "耗料机（不吃的）");
    }

    /** 抽取节点启停语义：手动关=不活；手动开且无感应=活。 */
    public static void 抽取启停(RouteBrainProbe p, Object lvl, int idx, ItemStack 关着的, ItemStack 开着的) {
        chk(!p.probeExtractorLive(lvl, idx, 关着的), "抽取节点：手动关着该不活（m154 点击才开始）");
        chk(p.probeExtractorLive(lvl, idx, 开着的), "抽取节点：手动开且无感应条件该活");
    }
}
