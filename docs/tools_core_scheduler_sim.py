#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m302/m309 算法尺：CoreScheduler Python 逐行移植 + 不变量模拟。

m309 版新增（作者 100×64+1 产线实测暴露 k>cap 恒饿后重写）：
  - 多节点口径：每核每拍发多次 request（吃到≥1 后其余节点吃零不记名=FED 集语义）。
  - 拍龄资历轮转：k>cap 时最饿者先食，任何核心连续挨饿拍数有界。
不变量（任一失败=退出码1，挂CI）：
  I1 预算硬顶：每拍全服批准总量 ≤ cap。
  I2 有界饥饿：预算为正时任何常驻核心连续零批准拍数 ≤ 2*ceil(k/cap)+4（k=核数）。
  I3 幽灵不堵门：记名后消失的核心不阻塞他核进食。
  I4 闸关旁路：cap<=0 时批准=请求，名单恒空。
  I5 回归-作者实测场景：101核×cap100 固定序长跑，min>0 且人人 granted>拍数/4。
  I6 回归-k远超cap：120核×cap50 固定序长跑，min>0（资历轮转生效）。
对照组：旧语义（逐请求记名+无资历）在 I5 场景下末核恒 0（证明 bug 真实、修复必要）。
"""
import random, sys
from collections import defaultdict

class Sched:
    """CoreScheduler.java m309 的逐行移植。"""
    def __init__(self):
        self.tick_stamp = None
        self.spent = 0
        self.reserve_left = 0
        self.starved = {}        # key -> age
        self.starved_next = {}
        self.fed = set()
        self.unserved_by_age = defaultdict(int)

    def _roll(self, now):
        self.tick_stamp = now
        self.spent = 0
        self.starved = self.starved_next
        self.starved_next = {}
        self.fed = set()
        self.unserved_by_age = defaultdict(int)
        for a in self.starved.values(): self.unserved_by_age[a] += 1
        self.reserve_left = len(self.starved)

    def _older(self, a):
        return sum(v for k, v in self.unserved_by_age.items() if k > a)

    def _bucket_dec(self, a):
        self.unserved_by_age[a] -= 1
        if self.unserved_by_age[a] <= 0: del self.unserved_by_age[a]

    def request(self, now, key, want, cap):
        if want <= 0: return 0
        if cap <= 0: return want
        if now != self.tick_stamp: self._roll(now)
        remain = cap - self.spent
        age = self.starved.get(key)
        if age is not None:
            del self.starved[key]
            self._bucket_dec(age)
            self.reserve_left -= 1
            if remain - self._older(age) < 1:
                self.starved_next[key] = max(self.starved_next.get(key, 0), age + 1)
                return 0
            open_ = remain - max(0, self.reserve_left)
            allow = max(open_, min(1, remain))
            granted = min(want, remain, max(0, allow))
        else:
            open_ = remain - max(0, self.reserve_left)
            granted = min(want, max(0, open_))
        if granted <= 0:
            if key not in self.fed:
                self.starved_next[key] = max(self.starved_next.get(key, 0), 1)
            return 0
        self.fed.add(key)
        self.spent += granted
        return granted

class OldSched:
    """m302~m308 旧语义（逐请求记名+无资历）——对照组证明 bug。"""
    def __init__(self):
        self.tick_stamp=None; self.spent=0; self.reserve_left=0
        self.starved=set(); self.starved_next=set()
    def request(self, now, key, want, cap):
        if want<=0: return 0
        if cap<=0: return want
        if now!=self.tick_stamp:
            self.tick_stamp=now; self.spent=0
            self.starved=self.starved_next; self.starved_next=set()
            self.reserve_left=len(self.starved)
        remain=cap-self.spent
        was=key in self.starved
        if was: self.starved.remove(key); self.reserve_left-=1
        open_=remain-max(0,self.reserve_left)
        allow=max(open_,min(1,remain)) if was else open_
        granted=min(want,remain,max(0,allow))
        if granted<=0:
            self.starved_next.add(key); return 0
        self.spent+=granted; return granted

def fail(msg):
    print("FAIL:", msg); sys.exit(1)

def drive(sched, n_cores, nodes, cap, ticks, ghost_die=None, want=20):
    """固定序驱动：每核每拍 nodes 次请求。返回 fed 累计与最大连续零拍。"""
    fed=[0]*n_cores; streak=[0]*n_cores; worst=[0]*n_cores
    for t in range(ticks):
        for c in range(n_cores):
            if ghost_die and c in ghost_die and t>=ghost_die[c]: continue
            got=0
            for _ in range(nodes):
                got += sched.request(t, ("d", c), want, cap)
            if cap>0:
                if got==0: streak[c]+=1; worst[c]=max(worst[c],streak[c])
                else: streak[c]=0
            fed[c]+=got
    return fed, worst

def run_seed(seed):
    rng=random.Random(seed)
    n=rng.randint(2,60); nodes=rng.randint(1,8)
    cap=rng.choice([0,1,3,10,50,120])
    ghosts={g:rng.randint(50,150) for g in rng.sample(range(n),k=rng.randint(0,n//3))}
    s=Sched()
    total_by_tick_ok=True
    # 单独按拍核 I1
    for t in range(300):
        tick_sum=0
        for c in range(n):
            if c in ghosts and t>=ghosts[c]: continue
            for _ in range(nodes):
                g=s.request(t,("d",c),rng.randint(1,20),cap)
                tick_sum+=g
                if cap<=0 and g==0: fail(f"seed={seed} 闸关旁路失效")
        if cap>0 and tick_sum>cap: fail(f"seed={seed} t={t} 超顶 {tick_sum}>{cap}")   # I1
        if cap<=0 and (s.starved or s.starved_next): fail(f"seed={seed} 闸关名单非空")  # I4
    if cap>0:
        s2=Sched()
        live=[c for c in range(n) if c not in ghosts]
        fed,worst=drive(s2,n,nodes,cap,400,ghost_die=ghosts)
        import math
        bound=2*math.ceil(n/cap)+4
        for c in live:
            if worst[c]>bound: fail(f"seed={seed} core{c} 连续零拍 {worst[c]} > 界 {bound} (n={n} cap={cap})")  # I2/I3

def steady_window(sched_cls, n, nodes, cap, warm, run):
    """稳态窗口口径（作者实测=RUN 前已达稳态,stats 于 RUN 起点清零）：warm 拍热身后只记 run 拍增量。"""
    s=sched_cls(); fed=[0]*n; worst=[0]*n; streak=[0]*n
    for t in range(warm+run):
        for c in range(n):
            got=0
            for _ in range(nodes): got += s.request(t,("d",c),20,cap)
            if t>=warm:
                fed[c]+=got
                if got==0: streak[c]+=1; worst[c]=max(worst[c],streak[c])
                else: streak[c]=0
    return fed,worst

def regressions():
    import math
    # I5 作者实测场景：101核 cap100（稳态窗口=热身100拍后计300拍）
    fed,worst=steady_window(Sched,101,4,100,100,300)
    if min(fed)<=0: fail(f"I5 101核cap100 稳态窗口仍有恒饿核 min={min(fed)}")
    if min(fed)<300//4: fail(f"I5 最低核吞吐 {min(fed)} < 窗口拍数/4")
    # 对照组：旧语义同场景=至少一核稳态窗口颗粒无收（作者实测 567,0,587 的复现）
    fed_o,_=steady_window(OldSched,101,4,100,100,300)
    if min(fed_o)!=0: fail(f"对照组失真：旧语义稳态窗口 min={min(fed_o)}≠0（bug 复现失败,检查移植）")
    # I6 k远超cap：120核 cap50
    fed,worst=steady_window(Sched,120,4,50,100,500)
    if min(fed)<=0: fail(f"I6 120核cap50 有恒饿核 min={min(fed)}")
    bound=2*math.ceil(120/50)+4
    if max(worst)>bound: fail(f"I6 最大连续零拍 {max(worst)} > 界 {bound}")

if __name__ == "__main__":
    for seed in range(60):
        run_seed(seed)
    regressions()
    print("OK: 60种子四不变量 + 回归I5(101核cap100,旧语义对照恒饿复现) + I6(120核cap50资历轮转) 全过。")
