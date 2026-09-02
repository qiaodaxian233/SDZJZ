package com.sdzjz.node;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * m506（真移植 A5a）：画布分组域**跨代行为契约**——同一套断言，两代各喂自己的节点栈
 * （1.21.1 栈附加数据是组件、1.20.1 是 tag，{@code ItemData}/{@code NodeTags} 两代各装各的实现；
 * 分组六方法本身已是 {@link CanvasGraphState} 里的同一份代码，这套断言压的是「同一份代码 ×
 * 两套栈数据实现」在两个世代上仍是同一个东西）。判官只此一份（配方域 m372 → 存储域 m480 →
 * 路由域 m481 → 本刀分组域，第四次推广）。
 *
 * <p><b>场景由各代判官自己搭</b>：传入至少 4 个节点栈（带 nx/ny 坐标）的图状态，以及一个与它的
 * {@code onChange} 绑定的变更计数器；断言只碰图状态与栈 NBT，不碰世界、不改配置。
 *
 * <p><b>八条判定</b>：
 * ① 建组：三成员成组 gid=1、空名自动"组1"、非成员不带标记、回调恰好 1 次；
 * ② 建组门槛：合法下标不足 2（越界/负数下标剔除后）=不成组且**零回调**（静默拒收在服务端合理：
 *    客户端本就不会发这种包，m99「静默无效」教训的适用面是玩家可见操作）；
 * ③ 重命名：首尾空白裁掉、空名拒、不存在的 gid 拒、超长钳 24 字；
 * ④ 整体位移：成员坐标同增量、非成员不动、只回调 1 次；单包 ±1e5 钳幅 + 终值 ±1e6 钳幅（m269 long 加法）；
 *    不存在的 gid 零回调；
 * ⑤ 一台机器只在一个组：成员被新组挖走后旧组剩 1 台即自动解散并剥标记（sweepGroups 顺手清）；
 * ⑥ 解散：元数据删除+全成员剥标记；重复解散零回调；
 * ⑦ 孤儿清扫：栈上有 gp 但无元数据→剥；有元数据但成员<2→删元数据并剥标记（坏档/史前状态自愈）；
 * ⑧ gid 分配=现有最大+1，不复用中间空洞（与主线 createGroup 原文一致，客户端组框颜色/标题按 gid 稳定）。
 *
 * <p>失败=AssertionError 带中文病灶信息；两代的包装判官各自翻译成 GameTest 失败。
 */
public final class CanvasGroupAssertions {

    private CanvasGroupAssertions() { }

    private static void chk(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    private static int gp(CanvasGraphState g, int i) { return NodeTags.nodeGroup(g.machineNodes.get(i)); }

    private static int nx(CanvasGraphState g, int i) { return NodeTags.viewOf(g.machineNodes.get(i)).getInt("nx"); }

    private static int ny(CanvasGraphState g, int i) { return NodeTags.viewOf(g.machineNodes.get(i)).getInt("ny"); }

    /** 直写栈上 gp（模拟坏档/史前状态，走两代共用的 ItemData 写口——读→view 写→copy 铁律 m353）。 */
    private static void rawGp(CanvasGraphState g, int i, int gid) {
        ItemStack s = g.machineNodes.get(i);
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        n.putInt("gp", gid);
        com.sdzjz.item.ItemData.write(s, n);
    }

    /**
     * 全套判定。
     * @param g       至少 4 个节点栈、每栈带 nx/ny 的图状态，且其 onChange 绑在 changes 上
     * @param changes 变更计数器（{@code changes[0]}，构造 g 时 {@code () -> changes[0]++}）
     */
    public static void runAll(CanvasGraphState g, int[] changes) {
        chk(g.machineNodes.size() >= 4, "场景前提：至少 4 个节点栈，实得 " + g.machineNodes.size());
        chk(g.groupNames.isEmpty(), "场景前提：开局无组");
        int[] x0 = new int[4], y0 = new int[4];
        for (int i = 0; i < 4; i++) { x0[i] = nx(g, i); y0[i] = ny(g, i); }

        // ① 建组
        changes[0] = 0;
        g.createGroup(java.util.List.of(0, 1, 2), "");
        chk(g.groupNames.size() == 1 && "组1".equals(g.groupNames.get(1)), "①建组：首组 gid 该是 1、空名该自动补\"组1\"，实得 " + g.groupNames);
        chk(gp(g, 0) == 1 && gp(g, 1) == 1 && gp(g, 2) == 1, "①建组：三成员栈上 gp 该都是 1，实得 " + gp(g, 0) + "/" + gp(g, 1) + "/" + gp(g, 2));
        chk(gp(g, 3) == -1, "①建组：非成员不该带标记，实得 " + gp(g, 3));
        chk(changes[0] == 1, "①建组：该回调恰好 1 次（改完只同步一次），实得 " + changes[0]);

        // ② 门槛：合法下标不足 2
        changes[0] = 0;
        g.createGroup(java.util.List.of(3, 99, -1), "废");
        chk(g.groupNames.size() == 1 && gp(g, 3) == -1, "②门槛：只剩 1 个合法下标不该成组，实得 " + g.groupNames + " gp3=" + gp(g, 3));
        chk(changes[0] == 0, "②门槛：不成组就不该回调，实得 " + changes[0]);

        // ③ 重命名
        changes[0] = 0;
        g.renameGroup(1, "  产线甲  ");
        chk("产线甲".equals(g.groupNames.get(1)), "③重命名：首尾空白该裁掉，实得 [" + g.groupNames.get(1) + "]");
        chk(changes[0] == 1, "③重命名：该回调 1 次，实得 " + changes[0]);
        g.renameGroup(1, "   ");
        chk("产线甲".equals(g.groupNames.get(1)) && changes[0] == 1, "③重命名：空名该拒且不回调，实得 [" + g.groupNames.get(1) + "] 回调 " + changes[0]);
        g.renameGroup(99, "幽灵");
        chk(g.groupNames.size() == 1 && changes[0] == 1, "③重命名：不存在的 gid 该拒且不回调，实得 " + g.groupNames + " 回调 " + changes[0]);
        String long30 = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十";
        g.renameGroup(1, long30);
        chk(g.groupNames.get(1).length() == 24 && long30.startsWith(g.groupNames.get(1)), "③重命名：超长该钳到 24 字，实得 " + g.groupNames.get(1).length());

        // ④ 整体位移
        changes[0] = 0;
        g.moveGroup(1, 5, -7);
        for (int i = 0; i < 3; i++)
            chk(nx(g, i) == x0[i] + 5 && ny(g, i) == y0[i] - 7, "④位移：成员 " + i + " 该 (+5,-7)，实得 (" + (nx(g, i) - x0[i]) + "," + (ny(g, i) - y0[i]) + ")");
        chk(nx(g, 3) == x0[3] && ny(g, 3) == y0[3], "④位移：非成员不该动");
        chk(changes[0] == 1, "④位移：整组该只回调 1 次，实得 " + changes[0]);
        g.moveGroup(1, 1_000_000_000, -1_000_000_000);
        chk(nx(g, 0) == x0[0] + 5 + 100_000 && ny(g, 0) == y0[0] - 7 - 100_000, "④位移：单包增量该钳 ±100000，实得 (" + nx(g, 0) + "," + ny(g, 0) + ")");
        for (int k = 0; k < 12; k++) g.moveGroup(1, 100_000, 100_000); // 反复发包累加：终值必须被 ±1e6 钳住不溢出（m269）
        chk(nx(g, 0) == 1_000_000 && ny(g, 0) == 1_000_000, "④位移：累加终值该钳 ±1000000（m269 long 加法），实得 (" + nx(g, 0) + "," + ny(g, 0) + ")");
        changes[0] = 0;
        g.moveGroup(77, 1, 1);
        chk(changes[0] == 0, "④位移：不存在的 gid 该零回调，实得 " + changes[0]);

        // ⑤ 一台机器只在一个组：挖走成员后旧组剩 1 台自动解散
        g.createGroup(java.util.List.of(2, 3), "乙");   // gid 2；组 1 剩 {0,1} 仍成组
        chk(g.groupNames.size() == 2 && "乙".equals(g.groupNames.get(2)), "⑤挖人：第二组 gid 该是 2、名\"乙\"，实得 " + g.groupNames);
        chk(gp(g, 2) == 2 && gp(g, 3) == 2 && gp(g, 0) == 1 && gp(g, 1) == 1, "⑤挖人：2/3 该进组 2，0/1 该仍在组 1");
        changes[0] = 0;
        g.createGroup(java.util.List.of(1, 3), "丙");   // gid 3；组 1 剩 {0}、组 2 剩 {2} → 两组都该被清
        chk(g.groupNames.size() == 1 && "丙".equals(g.groupNames.get(3)), "⑤挖人：旧组只剩 1 台该自动解散，只剩组 3，实得 " + g.groupNames);
        chk(gp(g, 0) == -1 && gp(g, 2) == -1, "⑤挖人：被解散组的孤儿该剥标记，实得 gp0=" + gp(g, 0) + " gp2=" + gp(g, 2));
        chk(gp(g, 1) == 3 && gp(g, 3) == 3, "⑤挖人：新组成员该带 gp=3");
        chk(changes[0] == 1, "⑤挖人：建组含顺手清扫该仍只回调 1 次，实得 " + changes[0]);

        // ⑥ 解散
        changes[0] = 0;
        g.dissolveGroup(3);
        chk(g.groupNames.isEmpty(), "⑥解散：元数据该删净，实得 " + g.groupNames);
        for (int i = 0; i < 4; i++) chk(gp(g, i) == -1, "⑥解散：成员 " + i + " 该剥标记，实得 " + gp(g, i));
        chk(changes[0] == 1, "⑥解散：该回调 1 次，实得 " + changes[0]);
        g.dissolveGroup(3);
        chk(changes[0] == 1, "⑥解散：重复解散该零回调，实得 " + changes[0]);

        // ⑦ 孤儿清扫（坏档/史前状态）
        rawGp(g, 0, 7);                          // 有标记无元数据
        g.groupNames.put(8, "孤");
        rawGp(g, 1, 8);                          // 有元数据但只 1 台
        g.sweepGroups();
        chk(gp(g, 0) == -1, "⑦清扫：无元数据的孤儿 gp 该剥，实得 " + gp(g, 0));
        chk(!g.groupNames.containsKey(8) && gp(g, 1) == -1, "⑦清扫：成员<2 的组该删元数据并剥标记，实得 " + g.groupNames + " gp1=" + gp(g, 1));

        // ⑧ gid 分配=现有最大+1，不复用空洞
        g.createGroup(java.util.List.of(0, 1), "甲");
        chk(g.groupNames.containsKey(1), "⑧gid：全空后首组该回到 1，实得 " + g.groupNames);
        g.groupNames.put(5, "占位"); rawGp(g, 2, 5); rawGp(g, 3, 5);
        g.dissolveGroup(1);                     // 现存 {5}
        g.createGroup(java.util.List.of(0, 1), "丁");
        chk(g.groupNames.containsKey(6) && "丁".equals(g.groupNames.get(6)), "⑧gid：有 5 在场时新组该拿 6（最大+1，不复用 1~4 空洞），实得 " + g.groupNames);
        chk(gp(g, 0) == 6 && gp(g, 1) == 6 && gp(g, 2) == 5 && gp(g, 3) == 5, "⑧gid：成员标记该与元数据一致");
    }
}
