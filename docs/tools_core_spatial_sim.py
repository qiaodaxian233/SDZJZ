#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m279 存储核心空间索引·算法级性质测试（回归尺）。

镜像 StorageCoreBlockEntity 的 64 格桶索引（bucketKey 算术右移含负坐标 / packBuckets 打包 /
coresNear AABB 桶遍历 + 超阈值全表兜底），随机注册/注销核心 + 随机范围查询，
断言：coresNear 候选经球面 d2 精筛后 == 全表 d2 精筛（口径零漂移），且候选恒为其超集。
跑法：python3 docs/tools_core_spatial_sim.py（退出码非 0 = 桶算法有毒）
"""
import random, sys

SHIFT = 6

def i32(v):  # Java int 溢出语义（打包用不到溢出，防御性钳一下）
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= (1 << 31) else v

def pack(bx, bz):
    return ((bz & 0xFFFFFFFFFFFFFFFF) << 32 | (bx & 0xFFFFFFFF)) & 0xFFFFFFFFFFFFFFFF

def bucket_key(x, z):
    return pack(x >> SHIFT, z >> SHIFT)  # python >> 对负数=向 -∞ 取整，与 Java 算术右移同义

class Index:
    def __init__(self):
        self.flat = set(); self.buckets = {}
    def register(self, p):
        if p in self.flat: return
        self.flat.add(p)
        self.buckets.setdefault(bucket_key(p[0], p[2]), set()).add(p)
    def unregister(self, p):
        self.flat.discard(p)
        k = bucket_key(p[0], p[2]); b = self.buckets.get(k)
        if b is not None:
            b.discard(p)
            if not b: del self.buckets[k]
    def cores_near(self, c, rng):
        if not self.buckets: return []
        r = min(rng, 30_000_000)
        b0x, b1x = (c[0]-r) >> SHIFT, (c[0]+r) >> SHIFT
        b0z, b1z = (c[2]-r) >> SHIFT, (c[2]+r) >> SHIFT
        cells = (b1x-b0x+1) * (b1z-b0z+1)
        if cells > 1024 or cells > 4*len(self.buckets): return list(self.flat)  # 兜底
        out = []
        for bx in range(b0x, b1x+1):
            for bz in range(b0z, b1z+1):
                out.extend(self.buckets.get(pack(bx, bz), ()))
        return out

def d2(a, b): return sum((x-y)**2 for x, y in zip(a, b))

def run(seed):
    rng = random.Random(seed); idx = Index(); alive = []
    for step in range(4000):
        op = rng.random()
        if op < 0.45 or not alive:
            p = (rng.randint(-100000, 100000), rng.randint(-64, 320), rng.randint(-100000, 100000))
            idx.register(p); alive.append(p)
        elif op < 0.6:
            p = alive.pop(rng.randrange(len(alive))); idx.unregister(p)
        else:
            c = (rng.randint(-100000, 100000), rng.randint(-64, 320), rng.randint(-100000, 100000))
            if rng.random() < 0.5 and alive:  # 一半查询锚在既有核心附近，逼出桶界命中
                base = rng.choice(alive)
                c = (base[0]+rng.randint(-80, 80), base[1], base[2]+rng.randint(-80, 80))
            r = rng.choice([16, 63, 64, 65, 128, 500, 10**6])
            cand = idx.cores_near(c, r)
            got = sorted(p for p in cand if d2(p, c) <= r*r)
            ref = sorted(p for p in idx.flat if d2(p, c) <= r*r)
            assert got == ref, f'seed={seed} step={step} 精筛结果漂移 c={c} r={r}'
            assert set(ref) <= set(cand), f'seed={seed} 候选漏点'
    # 收尾不变量：flat 与桶并集恒等、无空桶泄漏
    union = set().union(*idx.buckets.values()) if idx.buckets else set()
    assert union == idx.flat and all(idx.buckets.values())

if __name__ == '__main__':
    for s in range(30): run(s)
    print('✓ 30 种子 × 4000 步（注册/注销/范围查询含桶界±1与兜底档）精筛结果与全表逐点相等，桶∪=平面表，无空桶')
    sys.exit(0)
