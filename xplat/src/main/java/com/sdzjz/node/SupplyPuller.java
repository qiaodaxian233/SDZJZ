package com.sdzjz.node;

import com.sdzjz.machine.StorageAccess;
import com.sdzjz.machine.StorageLedgerProbe;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * m498（真移植·B2 收尾）：**逻辑节点供料拉取两代共用一份**——主线 tick 里的 m92 拉料循环
 * （连同 m154 主动泵、m157「没去处的不抽」、m160 白名单、m163a 泵缓存放宽、
 * m155→m158 精确账本授权支路）整段搬。
 *
 * <p>照 m495/m496 的办法逐句对过，差异六处**全是参数化能收的**，无一是"逻辑不同"：
 * ①泵速倍数（主线 ×(1+数量升级)，本世代无升级恒 ×1）→ {@link Host#pumpRateMul}；
 * ②精确账本源（主线可能多源含数据面板聚合，本世代单源）→ {@link Host#exactBanksOf}；
 * ③出线目标（主线 m355 数组化传入，本世代现算）→ {@link Host#outTargets}；
 * ④饱和加法的宿主类名 → 本类静态 {@link #satAdd}；
 * ⑤合成机需求缓存（主线 scratch 复用）→ 调用方传入；
 * ⑥m218c 多核心移相 → {@link Host#tickPhase}。
 * 其余逐字相同，包括 m157 那段"猪人塔产物消失"的血案注释。
 *
 * <p><b>bank 类型直接用 {@link StorageLedgerProbe}</b>——m480 建它时是为跨代行为契约，
 * 这里白捡：两代的存储核心都已实现，精确账本的 `exactTemplates`/`withdrawExact` 现成。
 */
public final class SupplyPuller {

    private SupplyPuller() { }

    public interface Host {
        int nodeCount();
        ItemStack nodeStack(int i);
        int[] outTargets(int i);
        Map<String, Long> nodeBuf(int i);
        boolean bufTypeOk(Map<String, Long> buf, String id);
        boolean accepts(Object level, int target, String id);
        boolean chainWants(Object level, int i, String id);
        boolean chainEndsInTrash(Object level, int i, String id);
        boolean extractorLive(Object level, int i, ItemStack st);
        /** 该节点的 kind1 供料源；null=没接。 */
        StorageAccess supplyFor(Object level, int i);
        /** 有无定向产出仓（m157 搬仓模式判据）。 */
        boolean hasDepositFor(Object level, int i);
        /** 泵速倍数：主线 = 1 + 数量升级级数，本世代恒 1。 */
        long pumpRateMul(ItemStack st);
        /** 精确账本源：主线可能多源（存储核心 + 数据面板聚合），本世代单源。 */
        List<StorageLedgerProbe> exactBanksOf(StorageAccess sup);
        /** 用宿主的 scratch 整锅转存仓账（m350：withdraw 当场实扣，防聚合视图虚账）。 */
        int fillDrain(Map<String, Long> store);
        String drainId(int k);
        long drainAmt(int k);
        void markChanged();
        /** m218c 多核心错峰相位（本世代恒 0）。 */
        default long tickPhase() { return 0; }
    }

    /** m273 非负计数饱和加法：溢出封顶 Long.MAX_VALUE。 */
    public static long satAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }

    /** 是不是拉料拍（m116：20t→5t 与逻辑节点转发同拍；m218c 多核心移相）。 */
    public static boolean isPullTick(Host host, long ticks) {
        return Math.floorMod(ticks + host.tickPhase(), 5) == 0;
    }

    /**
     * 跑一遍拉料（主线 m92 段原文）：任何逻辑节点接了「存储→自己」的供料边，
     * 都按 <b>自身放行规则 ∩ 下游机器真实需求</b> 拉料。遍历的是仓库类型清单（有限）。
     * 抽取节点是<b>主动泵</b>：不问下游要不要，按挡位速率抽——但 m157 修订过"没去处的不抽"。
     */
    public static void pull(Host host, Object level) {
        int nSize = host.nodeCount();
        for (int i = 0; i < nSize; i++) {
            ItemStack stL = host.nodeStack(i);
            // 五族逻辑节点（**不含垃圾桶**——防「仓→垃圾桶」手滑清空整仓，m150 边界）
            if (!(NodeTags.isFilter(stL) || NodeTags.isSwitch(stL) || NodeTags.isSensor(stL)
                    || NodeTags.isDistributor(stL) || NodeTags.isExtractor(stL))) continue;
            boolean pump = NodeTags.isExtractor(stL); // m154 抽取节点=主动泵
            if (pump && !host.extractorLive(level, i, stL)) continue; // m160 手动关或感应未放行=不抽
            boolean pumpAll = pump && host.hasDepositFor(level, i); // m157 有定向存储出线=搬仓，全抽
            StorageAccess sup = host.supplyFor(level, i);
            if (sup == null) continue;
            Map<String, Long> ownL = host.nodeBuf(i);
            long pumpRate = 0, pumped = 0;
            long bufCapL = 4096;
            if (pump) {
                pumpRate = NodeTags.extractorRate(stL) * host.pumpRateMul(stL);
                // m163a：泵缓存只是 id→long 计数不占实存，直接放到双周期余量（钳住就回到"速率>缓存卡喉"）
                bufCapL = Math.max(4096, pumpRate * 2);
            }
            boolean touched = false;
            final int dnP = host.fillDrain(sup.storeView()); // m350 转存 scratch
            for (int dk = 0; dk < dnP; dk++) {
                String id = host.drainId(dk);
                long have = ownL.getOrDefault(id, 0L);
                if (have >= bufCapL) continue; // m116 每种封顶（泵按速率放宽）
                if (!pump && !host.chainWants(level, i, id)) continue;
                if (pump && !NodeTags.machineFilterAllows(stL, id)) continue; // m160 抽取白名单：名单外碰都不碰
                if (pump && !pumpAll) {
                    // m157（用户实测：猪人塔/幽匿线产物"消失"）：m154 的无条件抽把全网络吸进
                    // 缓存囤着失踪——改为"没有去处的不抽"：出线机器目标当下肯收才抽。
                    boolean anyTake = false;
                    int[] tgP = host.outTargets(i);
                    if (tgP != null)
                        for (int t : tgP)
                            if (t >= 0 && t < nSize && host.accepts(level, t, id)) { anyTake = true; break; }
                    if (!anyTake) continue;
                }
                long roomL = bufCapL - have;
                if (pump) roomL = Math.min(roomL, pumpRate - pumped); // 泵按挡位限速
                if (roomL <= 0) { if (pump) break; else continue; }
                if (!host.bufTypeOk(ownL, id)) continue; // m270 类型上限：withdraw **前**判，拒收=不抽物品留仓零损失
                int got = sup.withdraw(id, (int) Math.min(roomL, Integer.MAX_VALUE));
                if (got > 0) { ownL.merge(id, (long) got, SupplyPuller::satAdd); pumped += got; touched = true; }
            }
            // m155 精确账本抽取 → m158 推广到任意逻辑节点的供料边。授权闸：只在「该 id 的出线链
            // 通向垃圾桶」时才抽（chainEndsInTrash 尊重过滤白名单/开关/抽取启停）——反正是去销毁，
            // 抹组件无损语义；顺带收益：链上有关着的抽取节点=闸断不抽，抽取节点成了销毁线的启停阀。
            for (StorageLedgerProbe bank : host.exactBanksOf(sup)) {
                for (ItemStack t : new java.util.ArrayList<>(bank.exactTemplates())) {
                    String idE = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(t.getItem()).toString();
                    long haveE = ownL.getOrDefault(idE, 0L);
                    if (haveE >= bufCapL) continue; // m163a：与普通支路同一上限
                    if (pump && !NodeTags.machineFilterAllows(stL, idE)) continue; // m160
                    if (!host.bufTypeOk(ownL, idE)) continue; // m270 前判，拒收=不抽零损失
                    if (!host.chainEndsInTrash(level, i, idE)) continue; // 授权闸
                    long roomE = bufCapL - haveE;
                    if (pump) roomE = Math.min(roomE, pumpRate - pumped);
                    if (roomE <= 0) break;
                    int gotE = bank.withdrawExact(t.copyWithCount(1), (int) Math.min(roomE, Integer.MAX_VALUE));
                    if (gotE > 0) { ownL.merge(idE, (long) gotE, SupplyPuller::satAdd); pumped += gotE; touched = true; }
                }
            }
            if (touched) host.markChanged();
        }
    }
}
