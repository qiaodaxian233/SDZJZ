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
 * <p><b>十类判定</b>（⑨⑩ 为 m482 D 阶段收判官双写时并入：这两条原本两代**各写了一份同名用例**，
 * 语义纯、零平台依赖，收进契约后判定只此一份，两代判官各自调用）：
 * ① 普通账基本律：存入累加、取出扣减、超量取只取到有多少、空栈无操作；
 * ② 精确账分流：带附加数据的走精确账本，不与裸件混堆；
 * ③ 精确账并账/分账：同款并成一条数量累加，异款各占一条；
 * ④ 附加数据保真：精确取出的模板与存入的同款（组件/tag 不被抹）；
 * ⑤ 类型额度：普通账与精确账**同占**额度（m130），usedTypes 口径一致；
 * ⑥ 类型闸零丢件：达上限后新类型拒收且**栈原样保留**（m293 前验即拒，不吞件）；
 * ⑦ storeView 与 count 一致：视图里的数与逐个 count 出来的数逐项相等；
 * ⑧ 经验池：加/取/取超量只取到有多少、非正数不生效；
 * ⑨ 分次取尽不复制不蒸发：连取两次的和恰等于存入量，取尽后余量为 0（原 two_withdraw_last_stack_no_dupe）；
 * ⑩ 精确索引经中间条目移除后仍正确：提净中间条目触发索引删键+平移，平移后并账必须仍命中原条目
 *    而不是新开一条（原 exact_index_survives_middle_removal——m295 哈希索引最容易错的那一格）。
 *
 * <p><b>⑪ 类型硬顶</b>单独提供（{@link #类型硬顶}），不进 {@link #runAll}：它需要调用方先把
 * {@code absoluteStorageTypeSafetyLimit} 开到一个小值。**配置窗归判官管（各代判官本来就有
 * try/finally 还原），契约只管判定**——契约自己碰配置会污染同批次其它用例（m466 暴露窗教训）。
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
        分次取尽不复制不蒸发(p, plainB);
        精确索引经中间移除仍正确(p, exactA1, exactA2, exactB);
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

    /**
     * ⑪ 类型硬顶（原 type_safety_limit_rejects_new_types，两代同名双写，m482 收进契约）。
     * <p><b>调用方须先把 absoluteStorageTypeSafetyLimit 开到 {@code cap} 并负责在 finally 里还原</b>；
     * 本方法只管判定，一个配置字都不碰。判据三条：达顶后新类型被拒、**被拒的栈原样保留零丢件**
     * （m293 前验即拒）、已有类型不受闸照常并账。
     *
     * @param a 第一种（占一格额度） @param b 第二种（占满 cap=2） @param 第三种 该被拒的那个
     */
    public static void 类型硬顶(StorageLedgerProbe p, int cap, ItemStack a, ItemStack b, ItemStack 第三种) {
        chk(p.usedTypes() == 0, "类型硬顶判定要求空账本起步，实得已用 " + p.usedTypes());
        chk(cap == 2, "本判定按 cap=2 写死场景（两种占满再试第三种），实得 cap=" + cap);
        int n = 第三种.getCount();
        chk(n > 0, "第三种样品该有数量");
        p.deposit(a.copy());
        p.deposit(b.copy());
        chk(p.usedTypes() == 2, "两种该占满额度，实得 " + p.usedTypes());
        p.deposit(第三种); // 传入的原栈——要验它是否被原样退回
        chk(p.usedTypes() == 2, "硬顶=2 时第三种应被拒，usedTypes 实得 " + p.usedTypes());
        chk(第三种.getCount() == n, "被拒的栈必须**原样保留**（m293 前验即拒，不吞件），实得 " + 第三种.getCount());
        ItemStack 再存 = a.copy();
        long 原有 = p.count(idOf(a));
        p.deposit(再存);
        chk(p.count(idOf(a)) == 原有 + a.getCount(), "已有类型不受闸该照常并账，实得 " + p.count(idOf(a)));
    }

    /** ⑨ 分次取尽：连取两次的和恰等于存入量，取尽后余量为 0（无复制无凭空蒸发）。 */
    private static void 分次取尽不复制不蒸发(StorageLedgerProbe p, ItemStack any) {
        String id = idOf(any);
        ItemStack s = any.copy();
        s.setCount(64);
        long 前 = p.count(id);
        p.deposit(s);
        int a = p.withdraw(id, 64);
        int b = p.withdraw(id, 64);
        chk(a + b == 64 + 前, "两路取和必须=存入量（无复制无凭空蒸发），实得 " + a + "+" + b + " vs " + (64 + 前));
        chk(p.count(id) == 0, "取尽后余量必须=0，实得 " + p.count(id));
    }

    /**
     * ⑩ 精确索引经中间条目移除后仍正确——提净中间条目会触发索引删键+平移，
     * <b>平移后并账必须仍命中原条目而不是新开一条</b>。这是 m295 哈希索引最容易错的那一格：
     * 错了不报错不崩，只表现为同款物品悄悄分裂成两条账目。
     */
    private static void 精确索引经中间移除仍正确(StorageLedgerProbe p, ItemStack k1, ItemStack k2, ItemStack k3) {
        // 场地自清：本判定要求精确账为空（前面几条已把自己开的条目取净）
        while (!p.exactTemplates().isEmpty()) {
            ItemStack t = p.exactTemplates().get(0).copy();
            p.withdrawExact(t, Integer.MAX_VALUE);
        }
        ItemStack a = k1.copy(); a.setCount(5); p.deposit(a);
        ItemStack b = k2.copy(); b.setCount(5); p.deposit(b);   // 与 k1 同款：并进第一条
        ItemStack c = k3.copy(); c.setCount(5); p.deposit(c);   // 异款：第二条
        int 条目 = p.exactTemplates().size();
        chk(条目 == 2, "场景前提：该有两条精确条目（k1/k2 同款并账、k3 异款），实得 " + 条目);
        ItemStack 首 = p.exactTemplates().get(0).copy();
        int gone = p.withdrawExact(首, Integer.MAX_VALUE);      // 提净第一条 → 删键+平移
        chk(gone == 10, "同款并账后提净该得 10（5+5），实得 " + gone);
        chk(p.exactTemplates().size() == 1, "提净后该剩 1 条，实得 " + p.exactTemplates().size());
        ItemStack c2 = k3.copy(); c2.setCount(5); p.deposit(c2); // 平移后再存同款
        chk(p.exactTemplates().size() == 1,
                "**平移后并账不得新开条目**（索引删键+平移的正确性），实得 " + p.exactTemplates().size());
        chk(p.exactCount(0) == 10, "平移后同款该累计 10（5+5），实得 " + p.exactCount(0));
        p.withdrawExact(p.exactTemplates().get(0).copy(), Integer.MAX_VALUE); // 收摊
    }

    private static String idOf(ItemStack s) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    /** 同款判定走共用身份键（m478 StackKey，世代差在 Kind 实现里）。 */
    private static boolean sameKind(ItemStack a, ItemStack b) {
        return com.sdzjz.storage.StackKey.of(a).equals(com.sdzjz.storage.StackKey.of(b));
    }
}
