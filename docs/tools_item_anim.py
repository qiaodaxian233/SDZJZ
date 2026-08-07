#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m314 物品贴图帧动画生成器——m277 方块动画同一把刀，搬到物品贴图上。

原理与 m277 完全同源：读基础贴图，只对"发光像素"(HSV 阈值分类)逐帧调制亮度，
轮廓/暗底像素每帧原样复制；输出原版竖条帧动画 (宽 × 宽·帧数) + .png.mcmeta
(interpolate=true)。基础美术一像素不改。物品贴图动画原版原生支持（时钟/指南针同款），
item/generated 与 Blockbench 元素模型的贴图都吃这一套。

四件方案（作者点名：并发/数量/速度升级 + 核心模块，"类似上次做的"=m277）：
  parallel_upgrade   并发升级(绿三叉分线器)：能量自根部沿枝干向上流，三枚端球错相闪烁
  count_upgrade      数量升级(紫方块堆)：三块方块按 顶→左→右 轮流点亮（计数感）
  speed_upgrade      速度升级(青齿轮+上箭头)：箭头亮波持续向上冲 + 齿轮环流转微光
  core_module_model  核心模块(Blockbench 调色板贴图)：按调色格亮度分层脉冲=模型由内芯
                     向外圈心跳（Blockbench 里那条 core_module_pulse 动画的物品端复活），
                     橙色指示灯走 2 倍频快闪

跑法：python3 docs/tools_item_anim.py        # 生成 + 全部断言
自带断言（m277 同款四道）：①条带尺寸=宽×宽·N ②每帧非发光像素与基础贴图逐位相等
        ③发光像素全周期平均亮度漂移 ≤8% ④mcmeta 过 json.load
基础美术唯一源=docs/anim_base/（首跑自动从 textures/item 引种原图，重跑以 anim_base 为准）。
"""
import colorsys, json, math, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TDIR = os.path.join(ROOT, 'src/main/resources/assets/sdzjz/textures/item')
BASE = os.path.join(ROOT, 'docs/anim_base')  # 基础美术唯一源（条带是产物，别拿条带当源）
try:
    from PIL import Image
except ImportError:
    sys.exit('需要 Pillow：pip install pillow')


def load(name, size):
    """读基础贴图：docs/anim_base 为唯一源；首跑时从 textures/item 引种（须是 size² 原图）。"""
    os.makedirs(BASE, exist_ok=True)
    src = os.path.join(BASE, name + '.png')
    if not os.path.exists(src):
        seed = Image.open(os.path.join(TDIR, name + '.png')).convert('RGBA')
        assert seed.size == (size, size), \
            f'{name} 引种失败：textures 里已是条带？请把 {size}×{size} 原图放进 docs/anim_base/'
        seed.save(src)
    im = Image.open(src).convert('RGBA')
    assert im.size == (size, size), f'{name} 基础图尺寸非 {size}×{size}'
    return im


def hsv(p):
    return colorsys.rgb_to_hsv(p[0] / 255, p[1] / 255, p[2] / 255)


def modulate(p, f):
    """按系数 f 调制像素亮度；过冲部分降饱和泛白（发光体自然过曝），alpha 不动。m277 原样。"""
    h, s, v = hsv(p)
    nv = v * f
    if nv > 1.0:
        s = max(0.0, s - (nv - 1.0) * 0.8)
        nv = 1.0
    r, g, b = colorsys.hsv_to_rgb(h, s, max(0.0, nv))
    return (round(r * 255), round(g * 255), round(b * 255), p[3])


def emit(name, size, frames_n, frametime, factor_fn, mask_pred):
    """m277 emit 同构，尺寸参数化。mask_pred(x,y,hsv0)->bool；factor_fn(x,y,u,hsv0)->float。"""
    base = load(name, size)
    bp = base.load()
    mask = {(x, y) for y in range(size) for x in range(size)
            if bp[x, y][3] > 0 and mask_pred(x, y, hsv(bp[x, y]))}
    strip = Image.new('RGBA', (size, size * frames_n))
    sums = {xy: 0.0 for xy in mask}
    for t in range(frames_n):
        fr = base.copy(); fp = fr.load()
        for (x, y) in mask:
            f = factor_fn(x, y, t / frames_n, hsv(bp[x, y]))
            sums[(x, y)] += f
            fp[x, y] = modulate(bp[x, y], f)
        strip.paste(fr, (0, size * t))
    out = os.path.join(TDIR, name + '.png')
    strip.save(out)
    with open(out + '.mcmeta', 'w', encoding='utf-8') as fh:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, fh)
    # ---- 断言（m277 四道原样）----
    st = Image.open(out); assert st.size == (size, size * frames_n), '条带尺寸错'
    sp = st.load()
    for t in range(frames_n):
        for y in range(size):
            for x in range(size):
                if (x, y) not in mask:
                    assert sp[x, y + size * t] == bp[x, y], f'非发光像素被改: {name} f{t} ({x},{y})'
    drift = max(abs(v / frames_n - 1.0) for v in sums.values()) if sums else 0.0
    assert drift <= 0.08, f'{name} 平均亮度漂移 {drift:.3f} 超 8%'
    json.load(open(out + '.mcmeta', encoding='utf-8'))
    print(f'✓ {name}: {frames_n} 帧 × frametime {frametime} = {frames_n*frametime/20:.1f}s 循环，'
          f'发光像素 {len(mask)}，平均亮度漂移 {drift:.3%}')


# ---------------------------------------------------------------- 四件语义

def gen_parallel_upgrade():
    """并发升级：能量沿枝干自下而上流(相位=高度)；端球(最亮件)叠 2 倍频、按横向位置错相
    ——三条支路各闪各的，"并发"本义。"""
    S = 128

    def factor(x, y, u, hsv0):
        h, s, v = hsv0
        rise = (S - 1 - y) / S                       # 0=根部 1=顶端
        amp = 0.10 + 0.22 * min(1.0, v)
        f = 1.0 + amp * math.sin(2 * math.pi * (u - rise))
        if v >= 0.72:                                # 端球/高光件：错相快闪
            f += 0.12 * math.sin(2 * math.pi * (2 * u + x / S * 3.0))
        return f
    emit('parallel_upgrade', S, 8, 5, factor,
         lambda x, y, hv: 0.16 <= hv[0] <= 0.50 and hv[1] >= 0.25 and hv[2] >= 0.25)


def gen_count_upgrade():
    """数量升级：三块紫方块 顶→左→右 依次点亮（相位差 1/3 圈），一格一格数上去。
    分块几何：掩码包围盒上 45% 高度=顶块，其下按中线分左右块。"""
    S = 128
    base = load('count_upgrade', S); bp = base.load()
    pred = lambda x, y, hv: 0.62 <= hv[0] <= 0.90 and hv[1] >= 0.20 and hv[2] >= 0.22
    mask = {(x, y) for y in range(S) for x in range(S)
            if bp[x, y][3] > 0 and pred(x, y, hsv(bp[x, y]))}
    ys = [y for _, y in mask]; xs = [x for x, _ in mask]
    split_y = min(ys) + 0.45 * (max(ys) - min(ys))
    mid_x = (min(xs) + max(xs)) / 2.0

    def cube(x, y):
        if y < split_y:
            return 0                                  # 顶块
        return 1 if x < mid_x else 2                  # 左块 / 右块

    def factor(x, y, u, hsv0):
        amp = 0.10 + 0.24 * min(1.0, hsv0[2])
        return 1.0 + amp * math.sin(2 * math.pi * (u - cube(x, y) / 3.0))
    emit('count_upgrade', S, 9, 5, factor, pred)      # 9 帧=3 的倍数，三块节拍整分


def gen_speed_upgrade():
    """速度升级：双通道——中央箭头=亮波持续向上冲(相位=高度、幅度大)；
    齿轮环=沿角度流转的微光(整圈一周期，首尾无缝)。"""
    S = 128
    C = (S - 1) / 2.0
    RIN = 0.34 * S                                    # 内外通道分界半径(箭头区实测在环内)

    def factor(x, y, u, hsv0):
        h, s, v = hsv0
        r = math.hypot(x - C, y - C)
        if r < RIN:                                   # 箭头：向上行波
            rise = (S - 1 - y) / S
            amp = 0.14 + 0.24 * min(1.0, v)
            return 1.0 + amp * math.sin(2 * math.pi * (u - rise * 1.5))
        ang = math.atan2(y - C, x - C) / (2 * math.pi)  # 齿轮环：角向流光
        return 1.0 + 0.15 * math.sin(2 * math.pi * (u - ang))
    emit('speed_upgrade', S, 8, 5, factor,
         lambda x, y, hv: 0.40 <= hv[0] <= 0.60 and hv[2] >= 0.28)


def gen_core_module():
    """核心模块：Blockbench 调色板贴图(64²=4×4 个 16px 色格，模型各层元素各取一格)。
    按色格明度排相位=模型由最亮内芯向暗外圈逐层心跳（core_module_pulse 物品端复活）；
    橙色格(指示灯)独走 2 倍频快闪。暗底格(v<0.2)纹丝不动。"""
    S, CELL = 64, 16
    base = load('core_module_model', S); bp = base.load()
    cell_v, cell_orange = {}, {}
    for cy in range(S // CELL):
        for cx in range(S // CELL):
            px = [(x, y) for y in range(cy * CELL, (cy + 1) * CELL)
                  for x in range(cx * CELL, (cx + 1) * CELL) if bp[x, y][3] > 0]
            if not px:
                continue
            hs = [hsv(bp[x, y]) for x, y in px]
            v = sum(t[2] for t in hs) / len(hs)
            orange = sum(1 for t in hs if t[0] < 0.17 and t[1] >= 0.5 and t[2] >= 0.35) > len(hs) * 0.5
            cell_v[(cx, cy)] = v
            cell_orange[(cx, cy)] = orange
    lit = {c for c, v in cell_v.items() if v >= 0.20}          # 暗底格出局
    vmax = max(cell_v[c] for c in lit); vmin = min(cell_v[c] for c in lit)

    def cell_of(x, y):
        return (x // CELL, y // CELL)

    def factor(x, y, u, hsv0):
        c = cell_of(x, y)
        if cell_orange[c]:                            # 指示灯：快闪
            return 1.0 + 0.28 * math.sin(2 * math.pi * (2 * u + 0.25))
        vn = (cell_v[c] - vmin) / max(1e-6, vmax - vmin)       # 1=最亮内芯 0=最暗外圈
        phase = 0.75 * (1.0 - vn)                     # 内芯先跳，波向外扩
        amp = 0.12 + 0.24 * vn
        w = 2 * math.pi * (u - phase)
        return 1.0 + amp * (math.sin(w) + 0.3 * math.sin(2 * w)) / 1.3   # m277 心跳双峰波形
    emit('core_module_model', S, 8, 5, factor,
         lambda x, y, hv: cell_of(x, y) in lit)


if __name__ == '__main__':
    gen_parallel_upgrade()
    gen_count_upgrade()
    gen_speed_upgrade()
    gen_core_module()
    print('全部生成+断言通过 ✓（改基础贴图后重跑本脚本即重出动画）')
