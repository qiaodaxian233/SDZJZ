#!/usr/bin/env python3
"""m538 像素风内置资源包生成器（作者视频评论：贴图太高清/和原版差太多/有些跳脱）。

    python3 docs/tools_pixelate.py --write   # 从 assets/sdzjz/textures/{item,block} 生成 src/main/resources/resourcepacks/pixel/
    python3 docs/tools_pixelate.py           # 校验模式（第 23 闸）：重生成到内存与已提交产物逐字节对比，漂移即红

**不动作者原图**：高清件（物品 128×、方块 64×）留在默认资源里；本脚本产出一份像素风覆盖包，玩家在「资源包」里一键切。
工艺（m538 目检定案，三工艺对照见 DEVLOG）：物品 128→**32**（原版 16× 糊成一团认不出复杂机器，32× 能认且像素味足，只差原版 2 倍）；
方块 64→**16**（原版方块尺寸）；LANCZOS 降采样 + 轻锐化（UnsharpMask r1/60%/t2）+ 中位切分 40 色量化（无抖动）+ alpha 硬阈值 128；
动画帧条（h = 帧数×w，见 .png.mcmeta）**逐帧处理再叠回**，防锐化跨帧串色；.mcmeta 原样拷（帧时/插值不变）。
最小边已 ≤ 目标尺寸的贴图不进包（含 160×16 宽条、已 16× 的件；包只覆盖它含有的文件）；非动画件保持长宽比。GUI 贴图不动（不是评论说的对象）。
产出确定性：Pillow 同版本同参数逐字节一致（--check 依赖这点；CI 与本地 Pillow 版本不同导致假红时，以 --write 重生成后的 diff 为准）。
"""
import argparse, io, json, os, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / 'src/main/resources/assets/sdzjz/textures'
OUT = ROOT / 'src/main/resources/resourcepacks/pixel'
PACK_ID = 'pixel'
TARGET = {'item': 32, 'block': 16}
COLORS = 40
PACK_MCMETA = {"pack": {"pack_format": 34, "description": "生电终结者 · 像素风（物品 32× / 方块 16×，贴近原版尺寸；由 docs/tools_pixelate.py 从默认高清贴图生成）"}}


def pixelate(im, size, colors, animated):
    """animated=有 .mcmeta 且 h 是 w 的整数倍：按 w×w 逐帧处理再叠回；否则单帧、保持长宽比（宽条/图集不压扁）。"""
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


def render_all():
    """返回 {相对路径: bytes}（含 pack.mcmeta 与各 .png/.png.mcmeta），不写盘。"""
    from PIL import Image
    files = {'pack.mcmeta': (json.dumps(PACK_MCMETA, ensure_ascii=False, indent=2) + '\n').encode('utf-8')}
    for sub, size in TARGET.items():
        d = SRC / sub
        for p in sorted(d.glob('*.png')):
            im = Image.open(p)
            w, h = im.size
            meta = p.with_name(p.name + '.mcmeta')
            animated = meta.exists() and h % w == 0 and h > w
            frame_h = w if animated else h
            if min(w, frame_h) <= size:
                continue  # 已经是像素尺寸（含 160×16 这类宽条/已 16× 的件），不覆盖
            buf = io.BytesIO()
            pixelate(im, size, COLORS, animated).save(buf, format='PNG', optimize=True)
            rel = f'assets/sdzjz/textures/{sub}/{p.name}'
            files[rel] = buf.getvalue()
            if meta.exists():
                files[rel + '.mcmeta'] = meta.read_bytes()
    return files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--write', action='store_true', help='写盘生成/更新资源包')
    a = ap.parse_args()
    try:
        import PIL  # noqa: F401
    except ImportError:
        print('像素风资源包 ✗ 缺 Pillow（pip install pillow）')
        return 1
    files = render_all()
    if a.write:
        if OUT.exists():
            for old in OUT.rglob('*'):
                if old.is_file():
                    old.unlink()
        for rel, data in files.items():
            dst = OUT / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_bytes(data)
        n_png = sum(1 for r in files if r.endswith('.png'))
        print(f'像素风资源包 ✓ 写盘 {n_png} 张贴图 + {len(files) - n_png - 1} 个 .mcmeta → {OUT.relative_to(ROOT)}')
        return 0
    # 校验模式
    bad = []
    committed = {str(p.relative_to(OUT)).replace(os.sep, '/'): p.read_bytes() for p in OUT.rglob('*') if p.is_file()} if OUT.exists() else {}
    for rel, data in files.items():
        if rel not in committed:
            bad.append(f'缺产物 {rel}')
        elif committed[rel] != data:
            bad.append(f'漂移 {rel}')
    for rel in committed:
        if rel not in files:
            bad.append(f'多余 {rel}（源贴图已删或已 ≤ 目标尺寸）')
    if bad:
        print(f'像素风资源包 ✗ {len(bad)} 处与源贴图不一致——贴图换皮后跑 `python3 docs/tools_pixelate.py --write` 再提交：')
        for b in bad[:20]:
            print('    ' + b)
        return 1
    n_png = sum(1 for r in files if r.endswith('.png'))
    print(f'像素风资源包 ✓ {n_png} 张贴图与源一致（物品→{TARGET["item"]}× / 方块→{TARGET["block"]}×，{COLORS} 色）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
