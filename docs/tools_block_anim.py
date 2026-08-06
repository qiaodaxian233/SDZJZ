#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m277 方块贴图帧动画生成器——动画"长在贴图里"，不是浮在方块上的加片。

原理：读方块基础贴图(64×64)，只对"发光像素"(HSV 阈值分类)逐帧调制亮度，
底盘/金属等非发光像素每帧原样复制；输出原版竖条帧动画 (64 × 64·帧数) + .png.mcmeta
(interpolate=true 帧间插值，少帧也丝滑)。基础美术一像素不改——发光件呼吸/流光即动画。

三块方案（m249/m250 全息 BER 三件套的替代品）：
  structure_core  能量波：以中央芯为源、亮度脉冲沿径向外扩（越亮的件摆幅越大）
  data_panel      屏幕群：连通域=一块屏，各屏独立相位/波形错相闪刷 + 下扫刷新带
  super_bench     双通道：紫晶径向心跳呼吸(白热芯泛白) + 金导线沿曼哈顿距离外流光

跑法：python3 docs/tools_block_anim.py        # 生成 + 全部断言
自带断言：①条带尺寸=64×64·N ②每帧非发光像素与基础贴图逐位相等
        ③发光像素全周期平均亮度漂移 ≤8%（能量守恒，不整体变亮/变暗）④mcmeta 过 json.load
"""
import colorsys, hashlib, json, math, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TDIR = os.path.join(ROOT, 'src/main/resources/assets/sdzjz/textures/block')
BASE = os.path.join(ROOT, 'docs/anim_base')  # 基础美术唯一源（条带是产物，别拿条带当源）
try:
    from PIL import Image
except ImportError:
    sys.exit('需要 Pillow：pip install pillow')

S = 64  # 贴图边长


def load(name):
    """读基础贴图：docs/anim_base 为唯一源；首跑时从 textures 目录引种（须是 64×64 原图）。"""
    os.makedirs(BASE, exist_ok=True)
    src = os.path.join(BASE, name + '.png')
    if not os.path.exists(src):
        seed = Image.open(os.path.join(TDIR, name + '.png')).convert('RGBA')
        assert seed.size == (S, S), f'{name} 引种失败：textures 里已是条带？请把 64×64 原图放进 docs/anim_base/'
        seed.save(src)
    im = Image.open(src).convert('RGBA')
    assert im.size == (S, S), f'{name} 基础图尺寸非 {S}×{S}'
    return im


def hsv(p):
    return colorsys.rgb_to_hsv(p[0] / 255, p[1] / 255, p[2] / 255)


def modulate(p, f):
    """按系数 f 调制像素亮度；过冲部分降饱和泛白（发光体自然过曝），alpha 不动。"""
    h, s, v = hsv(p)
    nv = v * f
    if nv > 1.0:
        s = max(0.0, s - (nv - 1.0) * 0.8)
        nv = 1.0
    r, g, b = colorsys.hsv_to_rgb(h, s, max(0.0, nv))
    return (round(r * 255), round(g * 255), round(b * 255), p[3])


def clusters(mask_set):
    """8 邻域连通域切分：一块屏 = 一个簇。返回 [set(坐标), ...]，按最小坐标排序保证确定性。"""
    seen, out = set(), []
    for start in sorted(mask_set):
        if start in seen:
            continue
        comp, stack = set(), [start]
        while stack:
            x, y = stack.pop()
            if (x, y) in seen or (x, y) not in mask_set:
                continue
            seen.add((x, y)); comp.add((x, y))
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    stack.append((x + dx, y + dy))
        out.append(comp)
    return out


def emit(name, frames_n, frametime, factor_fn, mask_pred):
    base = load(name)
    bp = base.load()
    mask = {(x, y) for y in range(S) for x in range(S)
            if bp[x, y][3] > 0 and mask_pred(*hsv(bp[x, y]))}
    strip = Image.new('RGBA', (S, S * frames_n))
    sums = {xy: 0.0 for xy in mask}
    for t in range(frames_n):
        fr = base.copy(); fp = fr.load()
        for (x, y) in mask:
            f = factor_fn(x, y, t / frames_n, hsv(bp[x, y]))
            sums[(x, y)] += f
            fp[x, y] = modulate(bp[x, y], f)
        strip.paste(fr, (0, S * t))
    out = os.path.join(TDIR, name + '.png')
    strip.save(out)
    with open(out + '.mcmeta', 'w', encoding='utf-8') as fh:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, fh)
    # ---- 断言 ----
    st = Image.open(out); assert st.size == (S, S * frames_n), '条带尺寸错'
    sp = st.load()
    for t in range(frames_n):
        for y in range(S):
            for x in range(S):
                if (x, y) not in mask:
                    assert sp[x, y + S * t] == bp[x, y], f'非发光像素被改: {name} f{t} ({x},{y})'
    drift = max(abs(v / frames_n - 1.0) for v in sums.values()) if sums else 0.0
    assert drift <= 0.08, f'{name} 平均亮度漂移 {drift:.3f} 超 8%'
    json.load(open(out + '.mcmeta', encoding='utf-8'))
    print(f'✓ {name}: {frames_n} 帧 × frametime {frametime} = {frames_n*frametime/20:.1f}s 循环，'
          f'发光像素 {len(mask)}，平均亮度漂移 {drift:.3%}')
    return mask


CX = CY = (S - 1) / 2.0
RMAX = math.hypot(CX, CY)


def gen_structure_core():
    """青色能量波：sin(t − 径向相位) 由芯外扩；摆幅随像素自身亮度升(芯呼吸最猛，边件微闪)。"""
    def factor(x, y, u, hsv0):
        phase = math.hypot(x - CX, y - CY) / RMAX
        amp = 0.10 + 0.30 * min(1.0, hsv0[2])
        return 1.0 + amp * math.sin(2 * math.pi * (u - phase))
    emit('structure_core', 8, 5, factor,
         lambda h, s, v: v >= 0.45 and 0.42 <= h <= 0.68 and s >= 0.25)


def gen_data_panel():
    """屏幕群错相闪刷：连通域=屏，每屏定相位/摆幅/倍频(坐标哈希，确定可复现)；
    叠一条 4px 下扫刷新带只提亮发光像素(CRT 刷新感)。"""
    base = load('data_panel'); bp = base.load()
    pred = lambda h, s, v: (0.42 <= h <= 0.58 and s >= 0.6 and v >= 0.25) or (0.42 <= h <= 0.62 and v >= 0.42)
    mask = {(x, y) for y in range(S) for x in range(S)
            if bp[x, y][3] > 0 and pred(*hsv(bp[x, y]))}
    param = {}
    for comp in clusters(mask):
        seed = int(hashlib.md5(str(min(comp)).encode()).hexdigest(), 16)
        phi = (seed % 997) / 997.0
        amp = 0.12 + 0.14 * ((seed // 997) % 100) / 100.0
        dbl = 0.10 if (seed // 99700) % 3 == 0 else 0.0  # 1/3 的屏叠二倍频=闪刷感
        for xy in comp:
            param[xy] = (phi, amp, dbl)

    def factor(x, y, u, hsv0):
        phi, amp, dbl = param[(x, y)]
        f = 1.0 + amp * math.sin(2 * math.pi * (u + phi)) + dbl * math.sin(4 * math.pi * (u + phi))
        d = (y - u * S) % S          # 下扫刷新带（周期整循环，首尾无缝）
        if d < 4:
            f += 0.22 * (1 - d / 4)  # 带头最亮向后羽化
        return f
    emit('data_panel', 10, 4, factor, pred)


def gen_super_bench():
    """双通道：紫晶(含白热芯)径向心跳呼吸；金导线沿曼哈顿距离外流光(能量顺线跑)。"""
    def is_gold(h, s, v):
        return h < 0.2 and s >= 0.5 and v >= 0.35

    def factor(x, y, u, hsv0):
        h, s, v = hsv0
        if is_gold(h, s, v):
            manh = (abs(x - CX) + abs(y - CY)) / (CX + CY)
            return 1.0 + 0.15 * math.sin(2 * math.pi * (u - manh))
        phase = math.hypot(x - CX, y - CY) / RMAX
        amp = (0.12 + 0.24 * min(1.0, v)) * (1.35 if v >= 0.7 else 1.0)  # 白热芯呼吸最猛
        w = 2 * math.pi * (u - 0.9 * phase)
        return 1.0 + amp * (math.sin(w) + 0.3 * math.sin(2 * w)) / 1.3   # 心跳双峰波形
    emit('super_bench', 8, 5, factor,
         lambda h, s, v: (0.66 <= h <= 0.92 and v >= 0.38) or (h < 0.2 and s >= 0.5 and v >= 0.35)
                         or (v >= 0.7 and s <= 0.45))


if __name__ == '__main__':
    gen_structure_core()
    gen_data_panel()
    gen_super_bench()
    print('全部生成+断言通过 ✓（改基础贴图后重跑本脚本即重出动画）')
