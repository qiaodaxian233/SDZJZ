#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m278 FabricLedger 增量事务日志·算法级性质测试（回归尺）。

镜像 StorageCoreBlockEntity.FabricLedger 的 insert/extract 全分支（普通账本 dict +
精确账本平行双列表，类型上限闸、Long.MAX 钳幅、按下标 set/remove/add 结构操作），
跑两套事务实现喂**同一随机操作流**：
  A 增量日志（快照=位点，回滚=逆序重放 undo——与 Java 版逐行同构）
  B 整本深拷参照（旧版语义）
事务机器同构 Fabric SnapshotParticipant：首写建快照/嵌套逐层/内层 commit 快照下沉外层/
最外层 commit 走 onFinalCommit/abort 读快照。每个最外层事务收口后断言两套状态逐位相等。

跑法：python3 docs/tools_ledger_journal_sim.py   （退出码非 0 = 日志算法有毒，禁止合入）
注意：这是算法级尺子（m109 教训：尺子只证算法，不证 Java 抄写；抄写靠 CI 真编译+实机）。
"""
import random, sys

LONG_MAX = (1 << 63) - 1
MAX_TYPES = 6  # 压小类型上限，逼出"满员拒收新类型"分支


class Ledger:
    """账本+事务参与者。undo_mode=True 走增量日志，False 走整本深拷参照。"""

    def __init__(self, undo_mode):
        self.store = {}          # 普通账本 id -> n（dict 保序=LinkedHashMap）
        self.tpl = []            # 精确账本模板（用字符串代 ItemStack，同值可多次出现按下标区分）
        self.n = []              # 精确账本计数
        self.undo_mode = undo_mode
        self.journal = []        # A：undo 闭包
        self.snaps = []          # [(depth, snapshot)]，snapshot=位点(A)或三元深拷(B)
        self.final_commits = 0

    # ---- SnapshotParticipant 同构 ----
    def create_snapshot(self):
        return len(self.journal) if self.undo_mode else (dict(self.store), list(self.tpl), list(self.n))

    def read_snapshot(self, s):
        if self.undo_mode:
            for i in range(len(self.journal) - 1, s - 1, -1):
                self.journal[i]()
            del self.journal[s:]
        else:
            st, tp, nn = s
            self.store = dict(st); self.tpl = list(tp); self.n = list(nn)

    def update_snapshots(self, depth):
        if not self.snaps or self.snaps[-1][0] < depth:
            self.snaps.append((depth, self.create_snapshot()))

    def close(self, depth, committed):
        if self.snaps and self.snaps[-1][0] == depth:
            _, s = self.snaps.pop()
            if not committed:
                self.read_snapshot(s)
            elif depth == 0:
                if self.undo_mode: self.journal.clear()
                self.final_commits += 1
            elif not self.snaps or self.snaps[-1][0] < depth - 1:
                self.snaps.append((depth - 1, s))  # 快照下沉外层（Fabric 同款）
        elif depth == 0 and committed and self.undo_mode:
            assert not self.journal, '不变量破产：事务外日志非空'

    def used_types(self):
        return len(self.store) + len(self.tpl)

    # ---- insert/extract 与 Java 版逐分支同构 ----
    def insert(self, res, amt, depth, exact):
        if amt <= 0: return 0
        if not exact:
            if res not in self.store and self.used_types() >= MAX_TYPES: return 0
            cur = self.store.get(res, 0)
            accept = min(amt, LONG_MAX - cur)
            if accept <= 0: return 0
            self.update_snapshots(depth)
            if self.undo_mode:
                had = res in self.store
                self.journal.append((lambda r=res, c=cur, h=had:
                                     self.store.__setitem__(r, c) if h else self.store.pop(r, None)))
            self.store[res] = cur + accept
            return accept
        for i in range(len(self.tpl)):
            if self.tpl[i] == res:
                cur = self.n[i]
                accept = min(amt, LONG_MAX - cur)
                if accept <= 0: return 0
                self.update_snapshots(depth)
                if self.undo_mode:
                    self.journal.append(lambda idx=i, c=cur: self.n.__setitem__(idx, c))
                self.n[i] = cur + accept
                return accept
        if self.used_types() >= MAX_TYPES: return 0
        self.update_snapshots(depth)
        if self.undo_mode:
            def undo_add():
                self.tpl.pop(); self.n.pop()
            self.journal.append(undo_add)
        self.tpl.append(res); self.n.append(amt)
        return amt

    def extract(self, res, amt, depth, exact):
        if amt <= 0: return 0
        if not exact:
            have = self.store.get(res, 0)
            take = min(have, amt)
            if take <= 0: return 0
            self.update_snapshots(depth)
            if self.undo_mode:
                self.journal.append(lambda r=res, h=have: self.store.__setitem__(r, h))
            if have - take <= 0: del self.store[res]
            else: self.store[res] = have - take
            return take
        for i in range(len(self.tpl)):
            if self.tpl[i] == res:
                have = self.n[i]
                take = min(have, amt)
                if take <= 0: return 0
                self.update_snapshots(depth)
                if have - take <= 0:
                    if self.undo_mode:
                        self.journal.append((lambda idx=i, t=self.tpl[i], h=have:
                                             (self.tpl.insert(idx, t), self.n.insert(idx, h))))
                    self.tpl.pop(i); self.n.pop(i)
                else:
                    if self.undo_mode:
                        self.journal.append(lambda idx=i, h=have: self.n.__setitem__(idx, h))
                    self.n[i] = have - take
                return take
        return 0

    def state(self):
        return (tuple(sorted(self.store.items())), tuple(self.tpl), tuple(self.n))


def run(seed, txs):
    rng = random.Random(seed)
    A, B = Ledger(True), Ledger(False)
    items = [f'i{k}' for k in range(9)]
    for _ in range(txs):
        # 随机嵌套事务树：深度 0..3，每层 0..6 笔操作，随机 commit/abort
        def level(depth):
            for _ in range(rng.randint(0, 6)):
                op = rng.random()
                res = rng.choice(items); exact = rng.random() < 0.5
                amt = rng.choice([1, 7, 64, 4096, LONG_MAX // 2, LONG_MAX])
                for L in (A, B):
                    (L.insert if op < 0.55 else L.extract)(res, amt, depth, exact)
                if depth < 3 and rng.random() < 0.35:
                    level(depth + 1)
            committed = rng.random() < 0.6
            for L in (A, B):
                L.close(depth, committed)
            return committed
        level(0)
        assert A.state() == B.state(), f'seed={seed} 事务收口后状态漂移\nA={A.state()}\nB={B.state()}'
        assert not A.journal, f'seed={seed} 事务外日志残留 {len(A.journal)} 条'
    assert A.final_commits == B.final_commits
    return A.final_commits


if __name__ == '__main__':
    total = 0
    for seed in range(40):
        total += run(seed, 600)
    print(f'✓ 40 种子 × 600 最外层事务（嵌套≤3 层）逐事务终态相等，日志事务外恒空；final_commit {total} 次')
    sys.exit(0)
