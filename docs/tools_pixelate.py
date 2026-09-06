#!/usr/bin/env python3
"""m540 贴图两档（作者拍板：**默认低分辨率、高清可选**）——本脚本是唯一数据流：

    高清原图（作者画的）住 src/main/resources/resourcepacks/hd/assets/sdzjz/textures/{item,block}/   ← 「高清立绘」可选资源包
        │  python3 docs/tools_pixelate.py --write
        ▼
    默认贴图 src/main/resources/assets/sdzjz/textures/{item,block}/                                    ← 像素版（物品 32× / 方块 16×）
    1.20.1   versions/1.20.1/src/main/resources/assets/sdzjz/textures/{item,block}/（只写它已有的同名件）  ← 与主线逐字节同源

    python3 docs/tools_pixelate.py           # 校验模式（第 23 闸）：默认目录/1.20.1 与「由 hd 重生成」逐字节对比；默认目录里出现高清尺寸的图也红
    python3 docs/tools_pixelate.py --write   # 先收编：默认目录里最小边 > 目标尺寸的 png（作者按老习惯丢进来的新高清图）连 .mcmeta 一起搬进 hd；再全量生成

**作者老工作流不变**：高清 png 照旧丢进 assets/sdzjz/textures/item/，跑一次 --write 即可（忘了跑=第 23 闸红并给出这条命令）。
工艺（m538 目检定案）：物品 128→32（16× 认不出复杂机器）、方块 64→16；LANCZOS + 轻锐化（UnsharpMask r1/60%/t2）+ 中位切分 40 色无抖动 + alpha 硬阈值 128；
动画帧条（有 .mcmeta 且 h=帧数×w）逐帧处理再叠回；非动画保持长宽比；hd 里最小边已 ≤ 目标尺寸的件原样拷（不是所有件都需要缩）。
GUI 贴图不在此流程。Pillow 同版本同参数产出逐字节确定（CI 装 pillow 校验）。
"""
import argparse, io, json, pathlib, shutil, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
HD = ROOT / 'src/main/resources/resourcepacks/hd'
HD_TEX = HD / 'assets/sdzjz/textures'
MAIN_TEX = ROOT / 'src/main/resources/assets/sdzjz/textures'
RETRO_TEX = ROOT / 'versions/1.20.1/src/main/resources/assets/sdzjz/textures'
TARGET = {'item': 32, 'block': 16}
COLORS = 40
PACK_MCMETA = {"pack": {"pack_format": 34, "description": "生电终结者 · 高清立绘（作者原图：物品 128× / 方块 64×）。默认贴图是它的像素版（docs/tools_pixelate.py 生成）"}}


def png_size(p):
    import struct
    with open(p, 'rb') as f:
        h = f.read(24)
    return struct.unpack('>II', h[16:24]) if h[:8] == b'\x89PNG\r\n\x1a\n' else (0, 0)


def is_hd(p, size):
    w, h = png_size(p)
    meta = p.with_name(p.name + '.mcmeta')
    animated = meta.exists() and w and h % w == 0 and h > w
    frame_h = w if animated else h
    return min(w, frame_h) > size


def pixelate(im, size, colors, animated):
    from PIL import Image, ImageFilter
    im = im.convert('RGBA')
    w, h = im.size
    frames = h // w if (animated and w and h % w == 0 and h > w) else 1
    fh = h // frames
    tw, th = (size, size) if frames > 1 else (size, max(1, round(h * size / w)))
    out = Image.new('RGBA', (tw, th * frames), (0, 0, 0, 0))
    for i in range(frames):
        fr = im.crop((0, i * fh, w, (i + 1) * fh)).resize((tw, th), Image.Resampling.LANCZOS)
        fr = fr.filter(ImageFilter.UnsharpMask(radius=1, percent=60, threshold=2))
        a = fr.getchannel('A').point(lambda v: 255 if v >= 128 else 0)
        rgb = fr.convert('RGB').quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert('RGB')
        rgb.putalpha(a)
        out.paste(rgb, (0, i * th))
    return out


def render_defaults():
    """从 hd 渲染默认贴图：返回 {'item/x.png': bytes, 'item/x.png.mcmeta': bytes, ...}（不写盘）。"""
    from PIL import Image
    files = {}
    for sub, size in TARGET.items():
        d = HD_TEX / sub
        if not d.exists():
            continue
        for p in sorted(d.glob('*.png')):
            meta = p.with_name(p.name + '.mcmeta')
            rel = f'{sub}/{p.name}'
            if is_hd(p, size):
                buf = io.BytesIO()
                pixelate(Image.open(p), size, COLORS, meta.exists()).save(buf, format='PNG', optimize=True)
                files[rel] = buf.getvalue()
            else:
                files[rel] = p.read_bytes()  # 已是像素尺寸的件原样进默认
            if meta.exists():
                files[rel + '.mcmeta'] = meta.read_bytes()
    return files


def adopt():
    """默认目录里的高清尺寸 png（连 .mcmeta）搬进 hd（覆盖）。返回搬了几张。"""
    n = 0
    for sub, size in TARGET.items():
        d = MAIN_TEX / sub
        if not d.exists():
            continue
        for p in sorted(d.glob('*.png')):
            if is_hd(p, size):
                dst = HD_TEX / sub / p.name
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(p), str(dst))
                meta = p.with_name(p.name + '.mcmeta')
                if meta.exists():
                    shutil.copy2(str(meta), str(dst.with_name(dst.name + '.mcmeta')))
                n += 1
    return n


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--write', action='store_true', help='收编默认目录里的高清图 → 重生成默认贴图与 1.20.1 同名件 → 写 hd 的 pack.mcmeta')
    a = ap.parse_args()
    try:
        import PIL  # noqa: F401
    except ImportError:
        print('贴图两档 ✗ 缺 Pillow（pip install pillow）')
        return 1
    pack_bytes = (json.dumps(PACK_MCMETA, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    if a.write:
        moved = adopt()
        files = render_defaults()
        for rel, data in files.items():
            dst = MAIN_TEX / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_bytes(data)
            rp = RETRO_TEX / rel
            if rp.exists() or (rel.endswith('.mcmeta') and (RETRO_TEX / rel[:-7]).exists()):
                rp.write_bytes(data)  # 1.20.1 只镜像它已有的同名件（含其动画 meta）
        (HD / 'pack.mcmeta').parent.mkdir(parents=True, exist_ok=True)
        (HD / 'pack.mcmeta').write_bytes(pack_bytes)
        n_png = sum(1 for r in files if r.endswith('.png'))
        print(f'贴图两档 ✓ 收编 {moved} 张高清图进 hd；默认目录重生成 {n_png} 张（物品→{TARGET["item"]}× / 方块→{TARGET["block"]}×，{COLORS} 色）；1.20.1 同名件已镜像')
        return 0
    # 校验模式（第 23 闸）
    bad = []
    for sub, size in TARGET.items():
        for p in sorted((MAIN_TEX / sub).glob('*.png')):
            if is_hd(p, size):
                bad.append(f'默认目录出现高清尺寸 {sub}/{p.name}（高清图归 resourcepacks/hd，默认目录只放像素版）')
    files = render_defaults()
    for rel, data in files.items():
        mp = MAIN_TEX / rel
        if not mp.exists():
            bad.append(f'默认缺 {rel}')
        elif mp.read_bytes() != data:
            bad.append(f'默认漂移 {rel}')
        rp = RETRO_TEX / rel
        if rp.exists() and rp.read_bytes() != data:
            bad.append(f'1.20.1 漂移 {rel}')
    if not (HD / 'pack.mcmeta').exists() or (HD / 'pack.mcmeta').read_bytes() != pack_bytes:
        bad.append('hd/pack.mcmeta 缺失或漂移')
    if bad:
        print(f'贴图两档 ✗ {len(bad)} 处——跑 `python3 docs/tools_pixelate.py --write` 后提交：')
        for b in bad[:20]:
            print('    ' + b)
        return 1
    n_png = sum(1 for r in files if r.endswith('.png'))
    print(f'贴图两档 ✓ 默认 {n_png} 张与 hd 同源（物品→{TARGET["item"]}× / 方块→{TARGET["block"]}×）；1.20.1 同名件一致；默认目录无高清尺寸残留')
    return 0


if __name__ == '__main__':
    sys.exit(main())
