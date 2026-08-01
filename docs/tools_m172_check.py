#!/usr/bin/env python3
# 离线校验（镜像 SuperBenchRecipes 生成器逻辑，管线同 m165-m171）：
# ① 全表配方多重集两两唯一 ② 每方 BOM 总件数≤144、种数≤18 ③ 生物笼计数=生物数
# ④ 注册六件套计数断言（MachineDef/ModItems reg+创造栏/配方/双语言/模型/贴图）
import re, json, sys, os
os.chdir('/home/claude/SDZJZ')

src = open('src/main/java/com/sdzjz/machine/SuperBenchRecipes.java').read()

# ---- 解析 bom(...) 调用 ----
def parse_calls(name):
    calls, i = [], 0
    while True:
        i = src.find(name + '("', i)
        if i < 0: break
        j, depth = i + len(name), 0
        while True:
            if src[j] == '(': depth += 1
            elif src[j] == ')':
                depth -= 1
                if depth == 0: break
            j += 1
        calls.append(src[i + len(name) + 1: j])
        i = j
    return calls

# 常量表（与 java 文件里的缩写对齐）
consts = {}
for m in re.finditer(r'(\w+)\s*=\s*"((?:minecraft|sdzjz):[\w/]+)"', src):
    consts[m.group(1)] = m.group(2)

def tokenize(body):
    # 剥行内注释
    body = re.sub(r'//[^\n]*', '', body)
    toks, cur, depth, instr = [], '', 0, False
    for ch in body:
        if ch == '"': instr = not instr; cur += ch
        elif ch == ',' and not instr and depth == 0:
            toks.append(cur.strip()); cur = ''
        else:
            if not instr:
                if ch == '(': depth += 1
                if ch == ')': depth -= 1
            cur += ch
    if cur.strip(): toks.append(cur.strip())
    return toks

def val(tok):
    if tok.startswith('"'): return tok.strip('"')
    if tok in consts: return consts[tok]
    return int(tok)

recipes = {}  # result -> (multiset dict, mobs)
for body in parse_calls('bom'):
    toks = tokenize(body)
    result = val(toks[0]); mobs_csv = val(toks[1])
    mobs = [] if mobs_csv == '' else str(mobs_csv).split(',')
    ing = {}
    if mobs: ing['sdzjz:capture_cage'] = len(mobs)
    kv = toks[2:]
    assert len(kv) % 2 == 0, result
    for a, b in zip(kv[::2], kv[1::2]):
        ing[val(a)] = ing.get(val(a), 0) + int(val(b))
    ing['sdzjz:core_module'] = ing.get('sdzjz:core_module', 0) + 4
    assert result not in recipes, 'dup result ' + result
    recipes[result] = (ing, mobs)

# addSmall / addSmall9 小件
small = {}
for body in parse_calls('addSmall9'):
    toks = tokenize(body)
    result = val(toks[0]); ing = {}
    for t in toks[2:]:
        v = val(t)
        if v: ing[v] = ing.get(v, 0) + 1
    small[result] = ing
for body in parse_calls('addSmall('.rstrip('(')) if False else []:
    pass
for m in re.finditer(r'addSmall\("(.*?)",\s*"(.*?)"\)', src):
    result, soul = m.group(1), m.group(2)
    ing = {'minecraft:iron_ingot': 5, 'minecraft:redstone': 2, 'sdzjz:core_module': 1, soul: 1}
    small[result] = ing

allr = dict(recipes); allr.update({k: (v, []) for k, v in small.items()})
print(f'配方总数: {len(allr)}（机器 BOM {len(recipes)} + 小件 {len(small)}）')

# ① 多重集两两唯一
seen = {}
for r, (ing, _) in allr.items():
    key = tuple(sorted(ing.items()))
    assert key not in seen, f'多重集撞车: {r} vs {seen[key]}'
    seen[key] = r
print('① 多重集两两唯一 ✓')

# ② 件数/种数
worst = (0, ''); worst_t = (0, '')
for r, (ing, mobs) in recipes.items():
    n = sum(ing.values()); t = len(ing)
    assert n <= 144, f'{r} 件数 {n} > 144'
    assert t <= 18, f'{r} 种数 {t} > 18'
    if n > worst[0]: worst = (n, r)
    if t > worst_t[0]: worst_t = (t, r)
print(f'② 件数≤144 / 种数≤18 ✓（最重 {worst[1]}={worst[0]} 件，最多 {worst_t[1]}={worst_t[0]} 种）')

# ③ 生物笼计数
for r, (ing, mobs) in recipes.items():
    if mobs:
        assert ing.get('sdzjz:capture_cage', 0) == len(mobs), f'{r} 笼数 {ing.get("sdzjz:capture_cage")} ≠ 生物 {len(mobs)}'
print('③ 生物笼计数=生物数 ✓')

# ④ 六件套断言：新增机器逐项 grep
new_ids = sys.argv[1:] or []
mi = open('src/main/java/com/sdzjz/registry/ModItems.java').read()
mc = open('src/main/java/com/sdzjz/machine/Machines.java').read()
zh = json.load(open('src/main/resources/assets/sdzjz/lang/zh_cn.json'))
en = json.load(open('src/main/resources/assets/sdzjz/lang/en_us.json'))
for i in new_ids:
    assert f'"{i}"' in mc, f'{i}: MachineDef 缺'
    assert f'reg("{i}"' in mi, f'{i}: reg 缺'
    fld = i.upper()
    assert f'entries.add({fld});' in mi, f'{i}: 创造栏缺'
    assert f'sdzjz:{i}' in src, f'{i}: 配方缺'
    assert f'item.sdzjz.{i}' in zh and f'item.sdzjz.{i}' in en, f'{i}: 双语言缺'
    assert os.path.exists(f'src/main/resources/assets/sdzjz/models/item/{i}.json'), f'{i}: 模型缺'
    assert os.path.exists(f'src/main/resources/assets/sdzjz/textures/item/{i}.png'), f'{i}: 贴图缺'
    json.load(open(f'src/main/resources/assets/sdzjz/models/item/{i}.json'))
print(f'④ 六件套断言 ×{len(new_ids)} ✓' if new_ids else '④ （无新增名单入参）')
