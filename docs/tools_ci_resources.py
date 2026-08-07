#!/usr/bin/env python3
# m175 CI 资源审计(全库扫,不吃入参):
# ① resources 下所有 JSON 可解析 ② 中英语言键集合一致 ③ 每张物品模型的 layer0 贴图实存
# ④ 每个 reg("id") 有 模型+双语言键 ⑤ 每条 bom 结果 id 已注册 ⑥ 贴图断言(128×128 RGBA 或 16 倍数)
import json, os, re, sys, struct

os.chdir(os.path.join(os.path.dirname(__file__), '..'))
RES = 'src/main/resources'
fails = []

# ① 全 JSON 合法
n_json = 0
for root, _, files in os.walk(RES):
    for f in files:
        if f.endswith('.json'):
            p = os.path.join(root, f); n_json += 1
            try: json.load(open(p, encoding='utf-8'))
            except Exception as e: fails.append(f'JSON 非法: {p}: {e}')
print(f'① JSON 合法 ×{n_json}' + ('' if not fails else ' ✗'))

# ② 中英键一致
zh = json.load(open(f'{RES}/assets/sdzjz/lang/zh_cn.json', encoding='utf-8'))
en = json.load(open(f'{RES}/assets/sdzjz/lang/en_us.json', encoding='utf-8'))
d1, d2 = set(zh) - set(en), set(en) - set(zh)
if d1: fails.append(f'zh 有 en 无: {sorted(d1)}')
if d2: fails.append(f'en 有 zh 无: {sorted(d2)}')
print(f'② 中英键一致 ×{len(zh)}' + ('' if not (d1 or d2) else ' ✗'))

# ③ 模型→贴图配对 + ⑥ 贴图头断言(PNG IHDR)
mdir, tdir = f'{RES}/assets/sdzjz/models/item', f'{RES}/assets/sdzjz/textures/item'
n_tex = 0
for f in sorted(os.listdir(mdir)):
    m = json.load(open(os.path.join(mdir, f), encoding='utf-8'))
    layer = m.get('textures', {}).get('layer0', '')
    if not layer.startswith('sdzjz:item/'):
        continue  # 引用原版贴图的不查
    png = os.path.join(tdir, layer.split('/')[-1] + '.png')
    if not os.path.exists(png):
        fails.append(f'模型缺贴图: {f} → {png}'); continue
    with open(png, 'rb') as fh:
        head = fh.read(26)
    if head[:8] != b'\x89PNG\r\n\x1a\n':
        fails.append(f'非 PNG: {png}'); continue
    w, h = struct.unpack('>II', head[16:24])
    if w != h or w % 16 != 0:
        # m314 口径：竖条帧动画=高为宽的整数倍(≥2帧)且旁挂 .png.mcmeta（json 可解析）才放行
        anim_ok = (w % 16 == 0 and h % w == 0 and h // w >= 2
                   and os.path.exists(png + '.mcmeta'))
        if anim_ok:
            try:
                json.load(open(png + '.mcmeta', encoding='utf-8'))
            except Exception:
                anim_ok = False
        if not anim_ok:
            fails.append(f'贴图尺寸异常 {w}x{h}: {png}')
    n_tex += 1
print(f'③⑥ 模型贴图配对+尺寸 ×{n_tex}' + ('' if not fails else ' …'))

# ④ 注册项 → 模型+双语言
mi = open('src/main/java/com/sdzjz/registry/ModItems.java', encoding='utf-8').read()
ids = re.findall(r'reg\("([a-z0-9_]+)"', mi)
for i in ids:
    if not os.path.exists(f'{mdir}/{i}.json'): fails.append(f'注册缺模型: {i}')
    if f'item.sdzjz.{i}' not in zh: fails.append(f'注册缺中文名: {i}')
    if f'item.sdzjz.{i}' not in en: fails.append(f'注册缺英文名: {i}')
print(f'④ 注册项资源闭环 ×{len(ids)}')

# ⑤ 配方结果已注册（物品 或 方块）
src = open('src/main/java/com/sdzjz/machine/SuperBenchRecipes.java', encoding='utf-8').read()
mb = open('src/main/java/com/sdzjz/registry/ModBlocks.java', encoding='utf-8').read()
regset = set(ids) | set(re.findall(r'reg\("([a-z0-9_]+)"', mb))
for r in re.findall(r'bom\("sdzjz:([a-z0-9_]+)"', src) + re.findall(r'addSmall\w*\("sdzjz:([a-z0-9_]+)"', src):
    if r not in regset: fails.append(f'配方结果未注册: {r}')
print(f'⑤ 配方结果注册闭环')

if fails:
    print('\n== 失败 %d 项 ==' % len(fails))
    for f in fails: print(' ✗', f)
    sys.exit(1)
print('\n全绿 ✓')
