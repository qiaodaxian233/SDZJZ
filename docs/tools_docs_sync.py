#!/usr/bin/env python3
# m176 文档同步:机器数唯一数据源=Machines.java(不含 6 个逻辑节点)。
# 默认=校验模式(CI 用,漂移即红);--write 改写 README 标记块并重生成 机器清单.md。
import re, os, sys, json
os.chdir(os.path.join(os.path.dirname(__file__), '..'))
mc = open('common/src/main/java/com/sdzjz/machine/Machines.java', encoding='utf-8').read()
defs = set(re.findall(r'def(?:Multi|Consume)?\("([a-z0-9_]+)"', mc)) | set(re.findall(r'new MachineDef\("([a-z0-9_]+)"', mc))
NODES = {'filter_node','extractor_node','trash_node','sensor_node','switch_node','distributor_node'}
machines = sorted(defs - NODES)
n = len(machines)

readme = open('README.md', encoding='utf-8').read()
m = re.search(r'<!--MC-->(\d+) 台机器<!--/MC-->', readme)
assert m, 'README 缺 <!--MC--> 标记块'

zh = json.load(open('src/main/resources/assets/sdzjz/lang/zh_cn.json', encoding='utf-8'))
en = json.load(open('src/main/resources/assets/sdzjz/lang/en_us.json', encoding='utf-8'))
sr = open('src/main/java/com/sdzjz/machine/SuperBenchRecipes.java', encoding='utf-8').read()
t1 = set(re.findall(r'"sdzjz:([a-z0-9_]+)"', sr.split('TIER1 = ')[1].split(');')[0]))
t3 = set(re.findall(r'"sdzjz:([a-z0-9_]+)"', sr.split('TIER3 = ')[1].split(');')[0]))
def tier(i): return 'Ⅰ' if i in t1 else 'Ⅲ' if i in t3 else 'Ⅱ'

if '--write' in sys.argv:
    readme = re.sub(r'<!--MC-->\d+ 台机器<!--/MC-->', f'<!--MC-->{n} 台机器<!--/MC-->', readme)
    open('README.md', 'w', encoding='utf-8').write(readme)
    lines = ['# 机器清单（tools_docs_sync.py 自动生成，勿手改）', '', f'共 **{n} 台**（不含 6 个逻辑节点）。', '',
             '| id | 中文名 | English | 档 |', '|---|---|---|---|']
    for i in machines:
        lines.append(f"| `{i}` | {zh.get(f'item.sdzjz.{i}','?')} | {en.get(f'item.sdzjz.{i}','?')} | {tier(i)} |")
    open('机器清单.md', 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print(f'已写入:README 机器数={n},机器清单.md {n} 行')
else:
    assert int(m.group(1)) == n, f'README 机器数漂移: 写 {m.group(1)} 实 {n}(跑 --write 修正)'
    missing = [i for i in machines if f'item.sdzjz.{i}' not in zh]
    assert not missing, f'机器缺中文名: {missing}'
    assert os.path.exists('机器清单.md'), '机器清单.md 缺失(跑 --write 生成)'
    lst = open('机器清单.md', encoding='utf-8').read()
    stale = [i for i in machines if f'`{i}`' not in lst]
    assert not stale, f'机器清单.md 漏机: {stale}(跑 --write 重生成)'
    print(f'文档同步校验 ✓ 机器数={n}')
