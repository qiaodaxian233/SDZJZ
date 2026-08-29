package com.sdzjz.machine;

import net.minecraft.world.item.ItemStack;

/**
 * m480（真移植 D 阶段先行）：存储账本**跨代行为契约**——同一套断言，两个实现：
 * 1.21.1 的 GameTest 喂 StorageCoreBlockEntity，1.20.1 的 GameTest 喂 StorageCore120。
 * 判官只此一份（照 m372 {@code RecipeDomainAssertions} 的成熟样板；那套做法项目里早就有，
 * 一直只用在配方域，本刀起推广到存储域）。
 *
 * <p><b>它先于手术存在</b>：B 阶段要把账本核心下沉成共用类（两代 BE 改为转发），
 * 下沉之后这套断言必须**继续全绿**——契约先立、手术后做，行为不变量因此有机器保证，
 * 而不是靠「我记得没改语义」。这也是真移植路线里最便宜的一份保险：零业务代码改动。
 *
 * <p><b>八类判定</b>：
 * ① 普通账基本律：存入累加、取出扣减、超量取只取到有多少、空栈无操作；
 * ② 精确账分流：带附加数据的走精确账本，不与裸件混堆；
 * ③ 精确账并账/分账：同款并成一条数量累加，异款各占一条；
 * ④ 附加数据保真：精确取出的模板与存入的同款（组件/tag 不被抹）；
 * ⑤ 类型额度：普通账与精确账**同占**额度（m130），usedTypes 口径一致；
 * ⑥ 类型闸零丢件：达上限后新类型拒收且**栈原样保留**（m293 前验即拒，不吞件）；
 * ⑦ storeView 与 count 一致：视图里的数与逐个 count 出来的数逐项相等；
 * ⑧ 经验池：加/取/取超量只取到有多少、非正数不生效。
 *
 * <p>失败=AssertionError 带中文病灶信息；两代的包装判官各自翻译成 GameTest 失败。
 * 断言只碰账本口，不碰世界、不改配置（零暴露窗）。
 */
public final class StorageDomainAssertions {

    private StorageDomainAssertions() { }

    private static void chk(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    /** 跑全部八类。probe=本世代的存储核心（必须是**空账本**的新实例，判官自备干净场地）。 */
    public static void runAll(StorageLedgerProbe p, ItemStack plainA, ItemStack plainB,
                              ItemStack exactA1, ItemStack exactA2, ItemStack exactB) {
        chk(p.usedTypes() == 0, "契约前提：判官必须喂空账本，实得已用类型 " + p.usedTypes());
        普通账基本律(p, plainA, plainB);
        精确账分流与并账(p, plainA, exactA1, exactA2, exactB);
        类型额度与闸(p);
        视图一致(p);
        经验池(p);
    }

    /** ① 普通账基本律。 */
    private static void 普通账基本律(StorageLedgerProbe p, ItemStack a, ItemStack b) {
        String ida = idOf(a);
        ItemStack s1 = a.copy();
        s1.setCount(10);
        p.deposit(s1);
        chk(p.count(ida) == 10, "存入 10 该记 10，实得 " + p.count(ida));
        chk(s1.isEmpty(), "存入成功后原栈该被清空（全额收下），实剩 " + s1.getCount());
        ItemStack s2 = a.copy();
        s2.setCount(5);
        p.deposit(s2);
        chk(p.count(ida) == 15, "同款该累加到 15，实得 " + p.count(ida));
        chk(p.withdraw(ida, 4) == 4 && p.count(ida) == 11, "取 4 该剩 11，实得 " + p.count(ida));
        chk(p.withdraw(ida, 999) == 11, "超量取只取到有多少（11）");
        chk(p.count(ida) == 0, "取空后该为 0，实得 " + p.count(ida));
        chk(p.withdraw(ida, 5) == 0, "空账再取该得 0（不许负数不许异常）");
        chk(p.count(idOf(b)) == 0, "没存过的物品 count 该为 0");
        ItemStack empty = ItemStack.EMPTY;
        p.deposit(empty); // 空栈无操作，不该炸也不该建条目
        chk(p.usedTypes() == 0, "存空栈不该占类型额度，实得 " + p.usedTypes());
    }

    /** ②③④ 精确账分流、并账/分账、附加数据保真。 */
    private static void 精确账分流与并账(StorageLedgerProbe p, ItemStack plain,
                                        ItemStack a1, ItemStack a2, ItemStack b) {
        ItemStack pl = plain.copy();
        pl.setCount(3);
        p.deposit(pl);
        int 基线 = p.exactTemplates().size();
        chk(基线 == 0, "裸件不该进精确账本，实得精确条目 " + 基线);

        ItemStack e1 = a1.copy();
        e1.setCount(4);
        p.deposit(e1); // deposit 自动分流（带附加数据 → 精确账）
        chk(p.exactTemplates().size() == 1, "带附加数据的该分流进精确账本，实得 " + p.exactTemplates().size());
        chk(p.exactCount(0) == 4, "精确账该记 4，实得 " + p.exactCount(0));

        ItemStack e2 = a2.copy(); // 与 a1 同款（同物品同附加数据）
        e2.setCount(6);
        p.deposit(e2);
        chk(p.exactTemplates().size() == 1, "同款该并成一条，实得 " + p.exactTemplates().size());
        chk(p.exactCount(0) == 10, "同款该数量累加到 10，实得 " + p.exactCount(0));

        ItemStack e3 = b.copy(); // 异款
        e3.setCount(2);
        p.deposit(e3);
        chk(p.exactTemplates().size() == 2, "异款该各占一条，实得 " + p.exactTemplates().size());

        ItemStack tpl = p.exactTemplates().get(0).copy();
        chk(tpl.getCount() == 1, "精确模板 count 恒为 1（计数走 exactCount），实得 " + tpl.getCount());
        int got = p.withdrawExact(tpl, 3);
        chk(got == 3, "精确取 3 该得 3，实得 " + got);
        chk(p.exactCount(0) == 7, "精确取后该剩 7，实得 " + p.exactCount(0));
        chk(sameKind(tpl, a1), "精确模板该与存入的同款（附加数据保真，不被抹）");
        chk(p.withdrawExact(tpl, 999) == 7, "精确超量取只取到有多少（7）");
        chk(p.exactTemplates().size() == 1, "取空的条目该被移除，实得 " + p.exactTemplates().size());
    }

    /** ⑤⑥ 类型额度与类型闸零丢件。 */
    private static void 类型额度与闸(StorageLedgerProbe p) {
        int used = p.usedTypes();
        chk(used == p.storeView().size() + p.exactTemplates().size(),
                "usedTypes 该=普通账条目+精确账条目（m130 同占额度），实得 " + used
                        + " vs " + p.storeView().size() + "+" + p.exactTemplates().size());
        int cap = p.maxTypes();
        if (cap <= 0 || cap == Integer.MAX_VALUE || cap > 4096) return; // 无限/超大额度：闸不可测，跳过（判官侧不改配置，零暴露窗）
        // 填到上限（用一批必然存在的原版物品；不够填满就跳过——不为测试造假物品）
        java.util.List<net.minecraft.world.item.Item> pool = new java.util.ArrayList<>();
        for (net.minecraft.world.item.Item it : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            pool.add(it);
            if (pool.size() > cap + 4) break;
        }
        if (pool.size() < cap + 2) return;
        int k = 0;
        while (p.usedTypes() < cap && k < pool.size()) {
            ItemStack s = new ItemStack(pool.get(k++), 1);
            if (s.isEmpty()) continue;
            p.deposit(s);
        }
        if (p.usedTypes() < cap) return; // 池子不够（多数是重复类型），不硬测
        ItemStack over = null;
        while (k < pool.size()) {
            ItemStack s = new ItemStack(pool.get(k++), 7);
            if (!s.isEmpty() && p.count(idOf(s)) == 0) { over = s; break; }
        }
        if (over == null) return;
        int before = p.usedTypes();
        p.deposit(over);
        chk(p.usedTypes() == before, "达上限后新类型该被拒收（m293 前验即拒），实得已用 " + p.usedTypes());
        chk(over.getCount() == 7, "拒收时栈该**原样保留**零丢件，实剩 " + over.getCount());
    }

    /** ⑦ storeView 与 count 一致。 */
    private static void 视图一致(StorageLedgerProbe p) {
        for (var e : p.storeView().entrySet()) {
            chk(p.count(e.getKey()) == e.getValue(),
                    "storeView 与 count 不一致：" + e.getKey() + " 视图 " + e.getValue() + " vs 查 " + p.count(e.getKey()));
            chk(e.getValue() > 0, "账本里不该有零/负条目（写路径 remove 口径）：" + e.getKey() + "=" + e.getValue());
        }
    }

    /** ⑧ 经验池。 */
    private static void 经验池(StorageLedgerProbe p) {
        long base = p.xpBank();
        p.xpAdd(100);
        chk(p.xpBank() == base + 100, "加 100 该记 100，实得 " + (p.xpBank() - base));
        p.xpAdd(0);
        p.xpAdd(-5);
        chk(p.xpBank() == base + 100, "非正数加值不该生效，实得 " + (p.xpBank() - base));
        chk(p.xpTake(30) == 30 && p.xpBank() == base + 70, "取 30 该剩 70，实得 " + (p.xpBank() - base));
        chk(p.xpTake(9999) == base + 70, "超量取只取到有多少");
        chk(p.xpBank() == 0, "取空后该为 0，实得 " + p.xpBank());
    }

    private static String idOf(ItemStack s) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    /** 同款判定走共用身份键（m478 StackKey，世代差在 Kind 实现里）。 */
    private static boolean sameKind(ItemStack a, ItemStack b) {
        return com.sdzjz.storage.StackKey.of(a).equals(com.sdzjz.storage.StackKey.of(b));
    }
}
