#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m314 物品贴图帧动画生成器——m277 方块动画同一把刀，搬到物品贴图上。

原理与 m277 完全同源：读基础贴图，只对"发光像素"(HSV 阈值分类)逐帧调制亮度，
轮廓/暗底像素每帧原样复制；输出原版竖条帧动画 (宽 × 宽·帧数) + .png.mcmeta
(interpolate=true)。基础美术一像素不改。物品贴图动画原版原生支持（时钟/指南针同款），
item/generated 与 Blockbench 元素模型的贴图都吃这一套。

四件方案（m319 二轮返工：m314 的行波调制在 16px 图标下被降采样平均抵消——相位随像素
位置连续变化的波，邻近像素明暗互补，缩到图标尺寸求和≈常数，肉眼=不动。数量升级/旧核心
模块能看见动正因它们是"大块同相区域"。铁律：图标动画以大块同相区域为单位，工具自带
16px 可读性断言把关）：
  parallel_upgrade   并发升级(绿三叉分线器)：三条支路(枝干+端球)作为三个整体按 1/3 相位
                     轮流点亮（并发本义），根部整体缓呼吸
  count_upgrade      数量升级(紫方块堆)：三块方块整块按 顶→左→右 轮流点亮（m314 原案，
                     本就是块状同相，16px 断言直接过）
  speed_upgrade      速度升级(青齿轮+上箭头)：箭头整体强脉冲一循环两拍（提速感），
                     齿轮环=120° 亮弧整体旋转（均值居中防漂移）
  core_module        核心模块(作者原图 128²，m319 换回)：中央菱形芯整体心跳双峰、四向
                     端口反相呼吸（芯暗港亮）、电路纹走半同相半曼哈顿流、金针 2 倍频齐闪

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
    # ---- m319 新增：16px 可读性断言（教训=行波调制在图标尺寸被降采样平均抵消，肉眼不动）----
    small = [strip.crop((0, size * t, size, size * (t + 1))).resize((16, 16), Image.BILINEAR)
             for t in range(frames_n)]
    op = [(x, y) for y in range(16) for x in range(16) if small[0].load()[x, y][3] >= 64]
    def v16(im):
        p = im.load()
        return {xy: max(p[xy][0], p[xy][1], p[xy][2]) / 255.0 for xy in op}
    vs = [v16(im) for im in small]
    move = max(sum(abs(va[xy] - vb[xy]) for xy in op) / max(1, len(op))
               for i, va in enumerate(vs) for vb in vs[i + 1:])
    assert move >= 0.012, f'{name} 16px 可读性不足：帧间平均亮度位移 {move:.4f} < 0.012（行波抵消？改块状同相）'
    print(f'✓ {name}: {frames_n} 帧 × frametime {frametime} = {frames_n*frametime/20:.1f}s 循环，'
          f'发光像素 {len(mask)}，漂移 {drift:.3%}，16px 位移 {move:.4f}')


# ---------------------------------------------------------------- 四件语义

def gen_parallel_upgrade():
    """并发升级：三条支路(枝干+端球)整块按 1/3 相位轮流点亮，根部整体缓呼吸。
    分块几何：掩码包围盒上 62% 高度=支路区（按 x 三等分归属左/中/右支），其下=根部。
    9 帧=3 拍整分。块状同相=16px 下可读（m319 铁律）。"""
    S = 128
    base = load('parallel_upgrade', S); bp = base.load()
    pred = lambda x, y, hv: 0.16 <= hv[0] <= 0.50 and hv[1] >= 0.25 and hv[2] >= 0.25
    mask = {(x, y) for y in range(S) for x in range(S)
            if bp[x, y][3] > 0 and pred(x, y, hsv(bp[x, y]))}
    xs = [x for x, _ in mask]; ys = [y for _, y in mask]
    split_y = min(ys) + 0.62 * (max(ys) - min(ys))
    x0, x1 = min(xs), max(xs)

    def factor(x, y, u, hsv0):
        if y >= split_y:                              # 根部：整体缓呼吸
            return 1.0 + 0.15 * math.sin(2 * math.pi * u)
        unit = min(2, int((x - x0) / max(1, (x1 - x0 + 1)) * 3))  # 左/中/右支
        amp = 0.14 + 0.30 * min(1.0, hsv0[2])
        return 1.0 + amp * math.sin(2 * math.pi * (u - unit / 3.0))
    emit('parallel_upgrade', S, 9, 5, factor, pred)

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
    """速度升级：中央箭头**整体**强脉冲、一循环两拍(提速感)；齿轮环=120° 亮弧整体
    旋转(max(0,cos)² 窗、减均值 0.25 居中防漂移)。整块同相=16px 可读（m319 铁律）。"""
    S = 128
    C = (S - 1) / 2.0
    RIN = 0.34 * S                                    # 内外通道分界半径(箭头区实测在环内)

    def factor(x, y, u, hsv0):
        h, s, v = hsv0
        r = math.hypot(x - C, y - C)
        if r < RIN:                                   # 箭头：整体双拍脉冲
            return 1.0 + (0.15 + 0.28 * min(1.0, v)) * math.sin(4 * math.pi * u)
        ang = math.atan2(y - C, x - C)                # 齿轮环：旋转亮弧
        w = max(0.0, math.cos(2 * math.pi * u - ang)) ** 2
        return 1.0 + 0.30 * (w - 0.25)                # 全周均值≈0.25，居中后漂移≈0
    emit('speed_upgrade', S, 8, 5, factor,
         lambda x, y, hv: 0.40 <= hv[0] <= 0.60 and hv[2] >= 0.28)

def gen_core_module():
    """核心模块（m319 作者点名换回原图）：中央菱形芯整体心跳双峰(带 0.2 微径向迟滞)、
    四向端口/外缘蓝光**反相**呼吸(芯暗港亮，交替感 16px 极易读)、青电路纹=半同相半
    曼哈顿流(近看有流动、缩图不抵消)、金针 2 倍频齐闪。暗底盘/描边逐位不动。"""
    S = 128
    C = (S - 1) / 2.0
    R_CORE = 0.24 * S
    R_PORT = 0.36 * S

    def is_gold(hv):
        return hv[0] < 0.17 and hv[1] >= 0.45 and hv[2] >= 0.35

    def factor(x, y, u, hsv0):
        h, s, v = hsv0
        if is_gold(hsv0):                             # 金针：2 倍频齐闪
            return 1.0 + 0.26 * math.sin(4 * math.pi * (u + 0.125))
        r = math.hypot(x - C, y - C)
        if r < R_CORE:                                # 中央芯：心跳双峰，整体同相+微迟滞
            w = 2 * math.pi * (u - 0.2 * r / R_CORE)
            return 1.0 + (0.15 + 0.25 * min(1.0, v)) * (math.sin(w) + 0.3 * math.sin(2 * w)) / 1.3
        if r >= R_PORT and 0.52 <= h <= 0.66 and v >= 0.45:  # 四向端口/外缘：反相呼吸
            return 1.0 - 0.24 * math.sin(2 * math.pi * u)
        manh = (abs(x - C) + abs(y - C)) / (2 * C)    # 电路纹：半同相半流动
        return 1.0 + 0.10 * math.sin(2 * math.pi * u) + 0.12 * math.sin(2 * math.pi * (u - manh))
    emit('core_module', S, 8, 5, factor,
         lambda x, y, hv: is_gold(hv) or (0.42 <= hv[0] <= 0.70 and hv[2] >= 0.30 and hv[1] >= 0.15)
                          or (hv[2] >= 0.75 and hv[1] <= 0.45))   # 白热芯低饱和高亮也入列


# ------------------------------------------------- m330 五台规划器机器节点（作者点名"自动合成机那些"）

def gen_auto_crafter():
    """自动合成机：青蓝 3×3 合成面按 y 三分位切三行，顶→中→底轮流点亮（合成格逐行
    填充本义）。等距视角格面与侧徽同列重叠（列直方图实测无空档），不强拆、整行同相
    （m319 铁律：大块同相，16px 可读）。9 帧=3 拍整分。"""
    S = 128
    base = load('auto_crafter', S); bp = base.load()
    pred = lambda x, y, hv: 0.45 <= hv[0] <= 0.72 and hv[1] >= 0.20 and hv[2] >= 0.30
    mask = {(x, y) for y in range(S) for x in range(S)
            if bp[x, y][3] > 0 and pred(x, y, hsv(bp[x, y]))}
    ys = sorted(y for _, y in mask)
    t1, t2 = ys[len(ys) // 3], ys[2 * len(ys) // 3]   # 像素量三等分（比几何三等分抗徽记偏置）

    def factor(x, y, u, hsv0):
        row = 0 if y <= t1 else (1 if y <= t2 else 2)
        amp = 0.14 + 0.28 * min(1.0, hsv0[2])
        return 1.0 + amp * math.sin(2 * math.pi * (u - row / 3.0))
    emit('auto_crafter', S, 9, 5, factor, pred)


def gen_crop_farm():
    """种植机：作物像素（高饱和暖色=胡萝卜麦穗 + 绿色=叶苗）按货架层三分位，
    底→中→顶轮流点亮=生长向上波。木架/暗底（低饱和暖色）阈值挡在掩码外逐位不动。
    9 帧=3 拍整分。"""
    S = 128
    base = load('crop_farm', S); bp = base.load()
    pred = lambda x, y, hv: ((0.04 <= hv[0] <= 0.16 and hv[1] >= 0.48 and hv[2] >= 0.40)
                             or (0.16 < hv[0] <= 0.42 and hv[1] >= 0.30 and hv[2] >= 0.30))
    mask = {(x, y) for y in range(S) for x in range(S)
            if bp[x, y][3] > 0 and pred(x, y, hsv(bp[x, y]))}
    ys = sorted(y for _, y in mask)
    mid = ys[len(ys) // 2]

    # 首版三分位在 16px 断言下红（0.0080<0.012）：作物是散点小簇，三相位在图标尺寸互抵。
    # 按 m319 剧本改上下两块**反相**（此亮彼暗交替=位移翻倍）+ 加幅 + 放宽暖色阈值多收作物像素。
    def factor(x, y, u, hsv0):
        sgn = 1.0 if y > mid else -1.0                    # 下半（近根）与上半（近穗）反相=呼吸生长
        amp = 0.16 + 0.32 * min(1.0, hsv0[2])
        return 1.0 + sgn * amp * math.sin(2 * math.pi * u)
    emit('crop_farm', S, 8, 5, factor, pred)


def gen_brewing_tower():
    """酿造塔：青蓝液体件按塔身三分位 底→中→顶 涌升（酿造液上塔）；暖色炉口/灯
    2 倍频齐闪（火感）。9 帧（涌升 3 拍整分；灯 4.5 周非整分、单像素零均值不碍漂移）。"""
    S = 128
    base = load('brewing_tower', S); bp = base.load()
    # 二轮阈值（16px 断言逼出来的账：本图发光像素天生稀少，0.0059/0.0071 两红都是掩码
    # 太小被整轮廓平均稀释——放宽到亮玻璃/暖木光晕也入列（539→1396 枚），幅度同步抬）。
    is_warm = lambda hv: (hv[0] <= 0.14 or hv[0] >= 0.94) and hv[1] >= 0.30 and hv[2] >= 0.25
    is_cool = lambda hv: 0.42 <= hv[0] <= 0.80 and hv[1] >= 0.14 and hv[2] >= 0.18
    pred = lambda x, y, hv: is_warm(hv) or is_cool(hv)
    mask = {(x, y) for y in range(S) for x in range(S)
            if bp[x, y][3] > 0 and pred(x, y, hsv(bp[x, y]))}
    cool_ys = sorted(y for (x, y) in mask if is_cool(hsv(bp[x, y])))
    mid = cool_ys[len(cool_ys) // 2] if cool_ys else 64

    # 首版三段涌升在 16px 断言下红（0.0059<0.012）：瓶簇散点三相互抵（crop_farm 同病同药）。
    # 改冷色上下两块**反相**（塔顶馏出/塔底回流的交替感）+ 加幅；暖色炉口 2 倍频齐闪不变。
    def factor(x, y, u, hsv0):
        if is_warm(hsv0):                              # 炉口/灯：2 倍频齐闪
            return 1.0 + 0.30 * math.sin(4 * math.pi * (u + 0.125))
        sgn = 1.0 if y > mid else -1.0                 # 塔下半与上半反相
        amp = 0.20 + 0.34 * min(1.0, hsv0[2])
        return 1.0 + sgn * amp * math.sin(2 * math.pi * u)
    emit('brewing_tower', S, 8, 5, factor, pred)


def gen_enchant_factory():
    """附魔工厂：紫符文按 y 像素量中位切上/下两块**反相**呼吸（core_module 芯/港交替
    同款，16px 极易读）；暖色指示灯 2 倍频齐闪。8 帧。"""
    S = 128
    base = load('enchant_factory', S); bp = base.load()
    # 二轮阈值（16px 断言逼的账，酿造塔同病：紫符文天生稀少 0.0066 红——放宽到暗紫辉光
    # 也入列 407→928+241 枚，幅度同步抬）。
    is_warm = lambda hv: hv[0] <= 0.16 and hv[1] >= 0.25 and hv[2] >= 0.22
    is_viol = lambda hv: 0.55 <= hv[0] <= 0.92 and hv[1] >= 0.10 and hv[2] >= 0.15
    pred = lambda x, y, hv: is_warm(hv) or is_viol(hv)
    mask = {(x, y) for y in range(S) for x in range(S)
            if bp[x, y][3] > 0 and pred(x, y, hsv(bp[x, y]))}
    vys = sorted(y for (x, y) in mask if is_viol(hsv(bp[x, y])))
    mid = vys[len(vys) // 2] if vys else 64

    def factor(x, y, u, hsv0):
        if is_warm(hsv0):
            return 1.0 + 0.30 * math.sin(4 * math.pi * (u + 0.125))
        sgn = 1.0 if y <= mid else -1.0                # 上屏/下符文反相：此亮彼暗
        amp = 0.20 + 0.34 * min(1.0, hsv0[2])
        return 1.0 + sgn * amp * math.sin(2 * math.pi * u)
    emit('enchant_factory', S, 8, 5, factor, pred)


def gen_villager_trader():
    """村民无限交易机：琥珀行情板按 y 三分位 顶→中→底 逐行走灯（行情滚动感）；
    绿宝石格 2 倍频齐闪（成交闪烁）。9 帧=3 拍整分。"""
    S = 128
    base = load('villager_trader', S); bp = base.load()
    is_emer = lambda hv: 0.25 <= hv[0] <= 0.46 and hv[1] >= 0.35 and hv[2] >= 0.30
    is_amber = lambda hv: 0.03 <= hv[0] <= 0.16 and hv[1] >= 0.40 and hv[2] >= 0.35
    pred = lambda x, y, hv: is_emer(hv) or is_amber(hv)
    mask = {(x, y) for y in range(S) for x in range(S)
            if bp[x, y][3] > 0 and pred(x, y, hsv(bp[x, y]))}
    ays = sorted(y for (x, y) in mask if is_amber(hsv(bp[x, y])))
    t1 = ays[len(ays) // 3] if ays else 0
    t2 = ays[2 * len(ays) // 3] if ays else 0

    def factor(x, y, u, hsv0):
        if is_emer(hsv0):                              # 绿宝石：2 倍频成交闪
            return 1.0 + 0.26 * math.sin(4 * math.pi * (u + 0.125))
        row = 0 if y <= t1 else (1 if y <= t2 else 2)  # 顶行先亮=行情向下滚
        amp = 0.13 + 0.27 * min(1.0, hsv0[2])
        return 1.0 + amp * math.sin(2 * math.pi * (u - row / 3.0))
    emit('villager_trader', S, 9, 5, factor, pred)


if __name__ == '__main__':
    gen_parallel_upgrade()
    gen_count_upgrade()
    gen_speed_upgrade()
    gen_core_module()  # m319 起=作者原图 128²（Blockbench 调色板版已退役）
    gen_auto_crafter()      # m330 五台规划器（作者点名"自动合成机那些"）
    gen_crop_farm()
    gen_brewing_tower()
    gen_enchant_factory()
    gen_villager_trader()
    print('全部生成+断言通过 ✓（改基础贴图后重跑本脚本即重出动画）')
