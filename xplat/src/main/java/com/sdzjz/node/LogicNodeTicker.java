package com.sdzjz.node;

import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * m499（真移植·B2 收尾）：**逻辑节点清运六分支两代共用一份**。
 *
 * <p>m496 逐句对过两代的这段，当时结论是"同构，唯一差异是主线 {@code distributeEven} 为
 * {@code void}（有输出缓存兜底永远送得出去）而本世代要拿剩余量回灌"。m495/m496 把分发与均分
 * 下沉、统一成<b>都返回剩余量</b>之后（主线兜底恒回 0），这段就再没有拦路的差异了——本刀下沉。
 *
 * <p>六分支（分配器 / 过滤器 / 垃圾桶 / 抽取 / 开关 / 传感器）逐句照原文，包括：
 * 5t 清运节拍、m350 整锅转存再清（{@code fillDrain} 后 {@code clear}，回灌进空表）、
 * m150 垃圾桶吞光并累计"已吞"、m153 抽取节点的 <b>残量留缓存=背压</b>（不亮黄，与其余五族不同，
 * 是原文刻意的）、开关/传感器关闸时的"持料待命"词条。
 *
 * <p><b>灯表走宿主口</b>：两代的灯表口径本就不同（本世代 m473 加了"持料待命"黄灯，
 * 因为它没有输出缓存、货真的会停在缓存里；主线有输出缓存所以不需要这条），
 * 这是真实的世代差，收进 {@link Host#lampAfterDrain} 各实现各的。
 */
public final class LogicNodeTicker {

    private LogicNodeTicker() { }

    public interface Host {
        long ticks();
        Map<String, Long> nodeBuf(int i);
        /** m350 整锅转存进宿主 scratch，返回条数。 */
        int fillDrain(Map<String, Long> m);
        String drainId(int k);
        long drainAmt(int k);
        /** 常规路由：返回未送出的余量（主线恒 0，本世代回吐）。 */
        long route(Object level, int i, boolean hasOut, String id, long amt);
        /** 均分路由：同上。 */
        long routeEven(Object level, int i, boolean hasOut, String id, long amt);
        /** 传感器闸门当下是否放行。 */
        boolean sensorOpen(Object level, int i);
        /**
         * 清运后的灯表收口。
         * @param moved 送出去过 @param held 回灌了多少（本世代据此亮"持料待命"黄灯）
         * @param zeroWhenIdle 没动时是否归零待机（开关/传感器/抽取=true，分配器/过滤器=保持上拍灯）
         */
        void lampAfterDrain(int i, boolean moved, long held, boolean zeroWhenIdle);
        /** 直接点灯（关闸/已吞等固定词条）。 */
        void lamp(int i, int code, String why);
        void markChanged();
        /** 饱和加法（两代同式，宿主提供以免跨层引用）。 */
        long satAdd(long a, long b);
    }

    /**
     * 若该节点是六族逻辑节点，本拍归它管并返回 true（调用方 continue）。
     */
    public static boolean tick(Host host, Object level, int i, ItemStack st, boolean hasOut) {
        boolean isF = NodeTags.isFilter(st), isT = NodeTags.isTrash(st),
                isX = NodeTags.isExtractor(st), isSw = NodeTags.isSwitch(st),
                isSe = NodeTags.isSensor(st), isD = NodeTags.isDistributor(st);
        if (!(isF || isT || isX || isSw || isSe || isD)) return false;
        if (host.ticks() % 5 != 0) return true; // 5t 节拍（非节拍不动缓存不改灯）
        Map<String, Long> own = host.nodeBuf(i);

        if (isD) { // 分配器：来料在出线目标间均分（余数轮转），没人要的走兜底
            if (own.isEmpty()) return true;
            boolean moved = false;
            long held = 0;
            final int dn = host.fillDrain(own);
            own.clear(); // m350 整锅转存再清（回灌进空表）
            for (int dk = 0; dk < dn; dk++) {
                String id = host.drainId(dk);
                long amt = host.drainAmt(dk);
                if (amt <= 0) continue;
                long left = host.routeEven(level, i, hasOut, id, amt);
                if (left < amt) moved = true;
                if (left > 0) { own.merge(id, left, host::satAdd); held += left; }
            }
            host.lampAfterDrain(i, moved, held, false); // 动了才点绿，没动保持上拍灯
            return true;
        }
        if (isF) { // 过滤器：放行的沿出线下游，拦下的直落兜底（主线 targets=null 同义）
            if (own.isEmpty()) return true;
            boolean moved = false;
            long held = 0;
            final int dn = host.fillDrain(own);
            own.clear();
            for (int dk = 0; dk < dn; dk++) {
                String id = host.drainId(dk);
                long amt = host.drainAmt(dk);
                if (amt <= 0) continue;
                long left = host.route(level, i, hasOut && NodeTags.filterPasses(st, id), id, amt);
                if (left < amt) moved = true;
                if (left > 0) { own.merge(id, left, host::satAdd); held += left; }
            }
            host.lampAfterDrain(i, moved, held, false);
            return true;
        }
        if (isT) { // m150 垃圾桶：吞光输入缓存并累计"已吞"（白名单在收料侧 accepts 把关，吞侧不设卡）
            if (own.isEmpty()) return true;
            long ate = 0;
            final int dn = host.fillDrain(own);
            own.clear();
            for (int dk = 0; dk < dn; dk++) if (host.drainAmt(dk) > 0) ate += host.drainAmt(dk);
            if (ate > 0) {
                NodeTags.addTrashCount(st, ate); // m353 三段式写读（丢写="已吞"死数血案）
                host.markChanged();
                host.lamp(i, 1, "");
            }
            return true;
        }
        if (isX) { // m154 抽取节点：关着=退料待命，开着=清运（残量留缓存=背压，原文刻意不亮黄）
            boolean on = NodeTags.extractorOn(st);
            if (own.isEmpty()) { host.lamp(i, on ? 1 : 0, ""); return true; }
            boolean moved = false;
            final int dn = host.fillDrain(own);
            own.clear();
            for (int dk = 0; dk < dn; dk++) {
                String id = host.drainId(dk);
                long amt = host.drainAmt(dk);
                if (amt <= 0) continue;
                long left = host.route(level, i, hasOut, id, amt); // 两轮垫底 + m157 搬仓
                if (left < amt) moved = true;
                if (left > 0) own.merge(id, left, host::satAdd); // 残量留缓存=背压（原样，不亮黄）
            }
            host.lamp(i, moved ? 1 : 0, "");
            return true;
        }
        if (isSw && !NodeTags.switchOn(st)) { host.lamp(i, 2, "开关已关：持料待命"); return true; }
        if (isSe && !host.sensorOpen(level, i)) {
            host.lamp(i, 2, "传感器关闸：持料待命（监测条件未满足）");
            return true;
        }
        // 开关（开）/ 传感器（开闸）：直通转发自己的缓存（两段同构）
        if (own.isEmpty()) { host.lamp(i, 0, ""); return true; }
        boolean moved = false;
        long held = 0;
        final int dn = host.fillDrain(own);
        own.clear();
        for (int dk = 0; dk < dn; dk++) {
            String id = host.drainId(dk);
            long amt = host.drainAmt(dk);
            if (amt <= 0) continue;
            long left = host.route(level, i, hasOut, id, amt);
            if (left < amt) moved = true;
            if (left > 0) { own.merge(id, left, host::satAdd); held += left; }
        }
        host.lampAfterDrain(i, moved, held, true);
        return true;
    }
}
