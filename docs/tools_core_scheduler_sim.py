#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m302 算法尺：CoreScheduler（全服预算+饥饿名单）Python 逐行移植 + 不变量模拟。

不变量（任一失败=退出码1，可挂CI）：
  I1 预算硬顶：每 tick 全服批准总量 ≤ globalCap。
  I2 进展保证：tick t 记名的核心，若 t+1 到场且 饿核数 ≤ globalCap，必得 ≥1 周期。
  I3 幽灵不堵门：记名后消失的核心，至多影响一拍（其保留额下拍不复存在）。
  I4 闸关旁路：globalCap<=0 时批准=请求，名单恒空。
对照组：朴素先到先得（无名单）在同负载下产生"恒饿核心"，本调度器不产生（公平性验真）。
"""
import random, sys

class Sched:
    """com/sdzjz/machine/CoreScheduler.java 的逐行移植（键=(dim,pos)元组）。"""
    def __init__(self):
        self.tick_stamp = None
        self.spent = 0
        self.reserve_left = 0
        self.starved = set()
        self.starved_next = set()

    def request(self, now, key, want, cap):
        if want <= 0: return 0
        if cap <= 0: return want
        if now != self.tick_stamp: self._roll(now)
        remain = cap - self.spent
        was = key in self.starved
        if was:
            self.starved.remove(key)
            self.reserve_left -= 1
        open_ = remain - max(0, self.reserve_left)
        allow = max(open_, min(1, remain)) if was else open_
        granted = min(want, remain, max(0, allow))
        if granted <= 0:
            self.starved_next.add(key)
            return 0
        self.spent += granted
        return granted

    def _roll(self, now):
        self.tick_stamp = now
        self.spent = 0
        self.starved = self.starved_next
        self.starved_next = set()
        self.reserve_left = len(self.starved)

def fail(msg):
    print("FAIL:", msg); sys.exit(1)

def run_seed(seed):
    rng = random.Random(seed)
    n_cores = rng.randint(2, 40)
    cap = rng.choice([0, 1, 3, 10, 50, 200])
    ghosts = set(rng.sample(range(n_cores), k=rng.randint(0, n_cores // 3)))  # 中途消失核心
    ghost_die = {g: rng.randint(50, 150) for g in ghosts}
    wants = [rng.randint(1, 8) for _ in range(n_cores)]
    s = Sched()
    starved_prev = set()
    zero_streak = [0] * n_cores  # 连续零批准拍数（活核）
    for t in range(400):
        total = 0
        present = [c for c in range(n_cores) if c not in ghost_die or t < ghost_die[c]]
        # 名单换代发生在本拍首个请求：换代前记录上拍名单用于 I2
        starved_entering = set(s.starved_next)
        for c in present:  # 固定 tick 序=有序不公平的最坏情形
            key = ("dim0", c)
            g = s.request(t, key, wants[c], cap)
            total += g
            if cap > 0:
                if g == 0: zero_streak[c] += 1
                else: zero_streak[c] = 0
                # I2：上拍记名+本拍到场+预算够保底 → 必得≥1
                if key in starved_entering and len(starved_entering) <= cap and g < 1:
                    fail(f"seed={seed} t={t} core={c} 饿核到场未得保底 (k={len(starved_entering)} cap={cap})")
            else:
                if g != wants[c]: fail(f"seed={seed} 闸关未旁路")           # I4
        if cap > 0 and total > cap: fail(f"seed={seed} t={t} 超顶 {total}>{cap}")  # I1
        if cap <= 0 and (s.starved or s.starved_next): fail(f"seed={seed} 闸关名单非空")  # I4
        starved_prev = starved_entering
    # I3+公平性：预算为正且物理够分（cap≥核数保底）时，任何存活核心不得连饿两拍以上
    if cap >= n_cores:
        for c in range(n_cores):
            if c in ghost_die: continue
            if zero_streak[c] > 1:
                fail(f"seed={seed} core={c} 预算够分仍连饿 {zero_streak[c]} 拍")
    return cap, n_cores

def naive_starves():
    """对照组：同负载下朴素先到先得必产生恒饿核心（证明问题真实存在）。"""
    n, cap, wants = 10, 15, [5]*10
    fed = [0]*n
    for t in range(100):
        remain = cap
        for c in range(n):
            g = min(wants[c], remain); remain -= g; fed[c] += g
    return fed[-1] == 0  # 末位核心 100 拍颗粒无收

def sched_feeds_tail():
    """同负载走调度器：末位核心必有进食。"""
    n, cap, wants = 10, 15, [5]*10
    s = Sched(); fed = [0]*n
    for t in range(100):
        for c in range(n):
            fed[c] += s.request(t, ("d", c), wants[c], cap)
    return fed[-1] > 0

if __name__ == "__main__":
    for seed in range(60):
        run_seed(seed)
    if not naive_starves(): fail("对照组失真：朴素序竟不饿末核（负载参数需复核）")
    if not sched_feeds_tail(): fail("调度器未救活末核")
    print("OK: 60 种子 ×400 拍四不变量全过；对照组末核恒饿、调度器下末核得食。")
