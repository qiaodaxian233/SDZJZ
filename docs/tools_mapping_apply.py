#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m412 改名应用器（Yarn → Mojmap）——照 docs/MAPPING_TODO.md 批量改，默认只试跑不落盘。
m413 补第二层：m412 版注释承诺"改 import 引出的简名"，实现里却只有全限定名一层——照它跑，
import 行换成了 Mojmap，正文里的 `Identifier`/`Text` 等简名原封不动（实测全库要漏 ~2900 处），
落盘即哑炮。本笔把简名层真正做出来，并用占位符两阶段防"换名链"事故。

**能做什么**：
  · 第一层：全限定名替换（import 行 + 内联 FQN），长名优先防前缀吞噬（m412 原有）；
  · 第二层（m413 新增）：该文件确实 `import` 了的 Yarn 类，其正文简名按词边界替换成 Mojmap 简名。
    没 import、只内联 FQN 的文件不动简名——那些简名属于别人。
**不能做什么**：**方法/字段名一个都不碰**——那层的表由 `gradlew mojmapMembers`（m413）产出后
  供人工查表修，判官仍是真编译器。

红线（m409~m412 一以贯之 + m413 两条新的）：
  · 默认 dry-run，`--write` 才落盘；
  · 只在**给定文件清单**上作业；
  · 长名优先替换——防 `net.minecraft.item.Item` 抢先吃掉 `...item.ItemStack` 的前缀；
  · 表里 Mojmap 列缺失（"人工核"）的类型，命中即**报告并跳过该文件**，不猜；
    已人工确认两边同名的可用 `--assume-same <FQN>` 放行（如 MinecraftServer，m412 查明 proguard
    无其条目=名字没变，见 DEVLOG m412）；
  · **占位符两阶段**（m413）：全部命中先换成 \x01N\x02 占位符、最后统一落地——防换名链互吃
    （实锤三对：Yarn `RegistryKeys`→Moj `Registries` 而 Yarn `Registries`→`BuiltInRegistries`；
    `PlayerInventory`→`Inventory` 而 Yarn `Inventory`→`Container`；`SlotActionType`→`ClickType`）。

用法：
  python3 docs/tools_mapping_apply.py --list xplat/src/main/java/com/sdzjz/net       # 试跑一个包
  python3 docs/tools_mapping_apply.py --list <路径...> --assume-same net.minecraft.server.MinecraftServer --write
"""
import argparse
import os
import re
import sys

import srcroots

ROW = re.compile(r'^\|\s*\d+\s*\|\s*`([^`]+)`\s*\|\s*(?:`([^`]+)`)?')
PH = '\x01{}\x02'  # 占位符：控制符不会出现在源码里，也不参与 \b 词边界


def load_table(path, assume_same=()):
    """读 m411 产出的对照表 → ({yarn FQN: mojmap FQN}, [缺失的 yarn FQN])。"""
    table, missing = {}, []
    for line in open(path, encoding='utf-8'):
        m = ROW.match(line.strip())
        if not m:
            continue
        yarn, moj = m.group(1), m.group(2)
        if moj:
            table[yarn] = moj
        else:
            missing.append(yarn)
    for fqn in assume_same:
        if fqn in missing:
            missing.remove(fqn)
            table[fqn] = fqn  # 人工确认两边同名 → 恒等映射（不改也不再拦文件）
    return table, missing


def rewrite(body, table, missing):
    """返回 (新文本, 改动数, 命中的缺表类型集合)。命中缺表即整文件不动。"""
    hits_missing = {y for y in missing if y in body}
    if hits_missing:
        return body, 0, hits_missing
    ph_map, idx, n = {}, 0, 0
    imported = []
    # 第一层：全限定名 → 占位符（长名优先，防 Item 吃掉 ItemStack 前缀）
    for y in sorted(table, key=len, reverse=True):
        if y not in body:
            continue
        # 先记 import 作用域（含 import static 成员导入），供第二层判断
        if table[y] != y and re.search(r'^\s*import\s+(?:static\s+)?' + re.escape(y) + r'\s*[.;]', body, re.M):
            imported.append(y)
        if table[y] == y:
            continue  # 恒等映射（--assume-same）：无事可做
        tok = PH.format(idx); idx += 1
        ph_map[tok] = table[y]
        n += body.count(y)
        body = body.replace(y, tok)
    # 第二层（m413）：import 引出的简名 → 占位符（词边界；占位符两阶段防换名链互吃）
    for y in sorted(imported, key=lambda s: -len(s.rsplit('.', 1)[-1])):
        ys, ms = y.rsplit('.', 1)[-1], table[y].rsplit('.', 1)[-1]
        if ys == ms:
            continue
        pat = re.compile(r'\b' + re.escape(ys) + r'\b')
        cnt = len(pat.findall(body))
        if not cnt:
            continue
        tok = PH.format(idx); idx += 1
        ph_map[tok] = ms
        body = pat.sub(tok, body)
        n += cnt
    # 第三阶段：占位符统一落地
    for tok, real in ph_map.items():
        body = body.replace(tok, real)
    return body, n, set()


def simple_name(fqn):
    return fqn.rsplit('.', 1)[-1]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--table', default='docs/MAPPING_TODO.md')
    ap.add_argument('--list', nargs='+', required=True, help='要处理的文件或目录（分批作业）')
    ap.add_argument('--assume-same', nargs='*', default=[], help='人工确认两边同名的缺表 FQN（恒等放行）')
    ap.add_argument('--write', action='store_true', help='落盘（默认只试跑）')
    args = ap.parse_args()
    os.chdir(srcroots.repo_root())

    if not os.path.isfile(args.table):
        print('改名应用器 ✗ 找不到对照表 %s（先在有网机器上跑 gradlew mojmapTable）' % args.table)
        return 1
    table, missing = load_table(args.table, args.assume_same)
    print('对照表：可用 %d 条，缺表 %d 条' % (len(table), len(missing)))

    targets = []
    for t in args.list:
        if os.path.isdir(t):
            for dp, _d, fs in os.walk(t):
                targets += [os.path.join(dp, f) for f in fs if f.endswith('.java')]
        elif t.endswith('.java'):
            targets.append(t)
    print('作业文件 %d 个%s' % (len(targets), '' if args.write else '（试跑，不落盘）'))

    total, skipped, residue = 0, [], 0
    for p in sorted(targets):
        body = open(p, encoding='utf-8').read()
        new, n, miss = rewrite(body, table, missing)
        if miss:
            skipped.append((p, sorted(miss)))
            continue
        assert '\x01' not in new and '\x02' not in new, '占位符残留：' + p  # m137 精神：断言自己
        if n and args.write:
            open(p, 'w', encoding='utf-8').write(new)
        if n:
            total += n
            print('    %-64s %d 处' % (p[-64:], n))
        # 终检：落盘态不应再残留任何 Yarn 全限定名（m412 表覆盖过的）
        for y in table:
            if table[y] != y and y in new:
                residue += 1
    print('合计改动 %d 处；跳过 %d 个文件（命中缺表类型，需人工核）；全限定名残留 %d' % (total, len(skipped), residue))
    for p, miss in skipped:
        print('    跳过 %s ← %s' % (p, ', '.join(miss)))
    print('提醒：方法/字段名本工具不碰，查 gradlew mojmapMembers 产出的表逐个修，判官=真编译器（CI）。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
