#!/usr/bin/env python3
"""m141 图标提取：矩形粗裁 → alpha连通域清邻居残楼 → 裁边 → 4%边距补方 → LANCZOS 128 → 断言"""
from PIL import Image
import numpy as np
from scipy import ndimage
import os, sys

SRC = "/home/claude/repo/素材/概念图"
OUT = "/home/claude/icons"
PREV = "/home/claude/preview"
os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)

# name: (源图, crop(l,t,r,b), seeds[(x,y)原图坐标], clean 是否做连通域清理)
TARGETS = {
    # 凋灵机 五张
    "wither_farm":        ("凋灵机", (220, 10, 1090, 512), [(600, 280), (920, 300), (620, 100)], True),
    "froglight_farm":     ("凋灵机", (40, 490, 338, 965),  [(190, 700)], True),
    "goat_horn_farm":     ("凋灵机", (338, 515, 630, 985), [(480, 730)], True),
    "armadillo_farm":     ("凋灵机", (605, 545, 900, 1015),[(750, 760)], True),
    "sniffer_garden":     ("凋灵机", (858, 575, 1195, 1075),[(1020, 790)], True),
    # G组 三张（同一机身三仓，连通域清理无意义，纯直裁沿框柱竖切）
    "cobweb_machine":         ("G组杂项机器", (25, 233, 420, 1015),  [(240, 500)], False),
    "spore_blossom_farm":     ("G组杂项机器", (413, 250, 768, 1040), [(580, 520)], False),
    "budding_amethyst_farm":  ("G组杂项机器", (755, 268, 1215, 1005),[(950, 540)], False),
    # 幽匿线 三张
    "sculk_catalyst_farm":    ("幽匿线", (338, 85, 775, 1085),  [(555, 500)], True),
    "sculk_sensor_farm":      ("幽匿线", (828, 128, 1245, 608), [(970, 360)], True),
    "sculk_shrieker_farm":    ("幽匿线", (770, 612, 1245, 1115),[(960, 840)], True),
    # 附魔工厂：整图路线（m131b 酿造塔先例；m132 旧图是裁切=异形同病，换整图）
    "enchant_factory":    ("附魔自动化", None, None, False),
}

def process(name, src, crop, seeds, clean):
    im = Image.open(f"{SRC}/{src}.png").convert("RGBA")
    if crop:
        im = im.crop(crop)
        seeds = [(x - crop[0], y - crop[1]) for x, y in seeds]
    arr = np.array(im)
    a = arr[:, :, 3]
    mask = a > 16
    if clean:
        lab, n = ndimage.label(mask, structure=np.ones((3, 3)))
        keep_ids = set()
        for sx, sy in seeds:
            v = lab[sy, sx]
            if v: keep_ids.add(v)
        if not keep_ids:  # 种子落空则保最大块
            sizes = ndimage.sum(mask, lab, range(1, n + 1))
            keep_ids.add(int(np.argmax(sizes)) + 1)
        # 主体bbox（膨胀15px内）里、不贴裁切边、≥20px 的浮粒也保留
        keep = np.isin(lab, list(keep_ids))
        ys, xs = np.where(keep)
        bb = (max(xs.min()-15,0), max(ys.min()-15,0), min(xs.max()+15, mask.shape[1]-1), min(ys.max()+15, mask.shape[0]-1))
        border = set(lab[0, :]) | set(lab[-1, :]) | set(lab[:, 0]) | set(lab[:, -1])
        for cid in range(1, n + 1):
            if cid in keep_ids or cid in border: continue
            cys, cxs = np.where(lab == cid)
            if len(cys) >= 20 and cxs.min() >= bb[0] and cxs.max() <= bb[2] and cys.min() >= bb[1] and cys.max() <= bb[3]:
                keep_ids.add(cid)
        keep = np.isin(lab, list(keep_ids))
        arr = arr.copy(); arr[:, :, 3] = np.where(keep, a, 0)
        mask = keep
        im = Image.fromarray(arr)
    # 裁边
    ys, xs = np.where(mask)
    im = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    # 4% 边距补方
    w, h = im.size
    side = int(round(max(w, h) * 1.08))
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(im, ((side - w) // 2, (side - h) // 2), im)
    icon = canvas.resize((128, 128), Image.LANCZOS)
    # 断言
    assert icon.size == (128, 128) and icon.mode == "RGBA"
    cov = (np.array(icon)[:, :, 3] > 16).mean()
    assert cov > 0.30, f"{name} 覆盖率过低 {cov:.1%}"
    icon.save(f"{OUT}/{name}.png")
    # 预览 256
    canvas.resize((256, 256), Image.LANCZOS).save(f"{PREV}/{name}_256.png")
    return cov

if __name__ == "__main__":
    only = sys.argv[1:] or list(TARGETS)
    for name in only:
        src, crop, seeds, clean = TARGETS[name]
        cov = process(name, src, crop, seeds, clean)
        print(f"{name}: 覆盖率 {cov:.1%}")
