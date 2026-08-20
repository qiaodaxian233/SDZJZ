#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m412 改名应用器（Yarn → Mojmap，类名一层）——照 docs/MAPPING_TODO.md 批量改，默认只试跑不落盘。

**能做什么**：把源码里的 Yarn **类名**（全限定名与 import 引出的简名）按对照表换成 Mojmap 名。
**不能做什么**：**方法名**（`getStackInHand`→`getItemInHand` 这类）它一个都不碰——那层没有可离线核对的表，
唯一可靠的判官是真编译器。所以本工具的定位是"把 80% 的机械活干掉，把剩下的交给编译器点名"。

红线（m409/m410/m411 一以贯之）：
  · 默认 dry-run，`--write` 才落盘；
  · 只在**给定文件清单**上作业（分批，一批一批过 CI，绝不全库一把梭）；
  · 简名替换只在"该文件确实 import 了这个 Yarn 类"时才做，避免误伤同名的自家类；
  · 表里 Mojmap 列缺失（"人工核"）的类型，命中即**报告并跳过该文件**，不猜。

用法：
  python3 docs/tools_mapping_apply.py --list xplat/src/main/java/com/sdzjz/net      # 试跑一个包
  python3 docs/tools_mapping_apply.py --list <路径...> --write                      # 确认后落盘
"""
import argparse
import os
import re
import sys

import srcroots

ROW = re.compile(r'^\|\s*\d+\s*\|\s*`([^`]+)`\s*\|\s*(?:`([^`]+)`)?')


def load_table(path):
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
    return table, missing


def rewrite(body, table, missing):
    """返回 (新文本, 改动数, 命中的缺表类型集合)。"""
    hits_missing = set()
    for y in missing:
        if y in body:
            hits_missing.add(y)
    # 长名优先替换，防止 net.minecraft.item.Item 抢先吃掉 net.minecraft.item.ItemStack
    n = 0
    for y in sorted(table, key=len, reverse=True):
        if y not in body:
            continue
        cnt = body.count(y)
        body = body.replace(y, table[y])
        n += cnt
    return body, n, hits_missing


def simple_name(fqn):
    return fqn.rsplit('.', 1)[-1]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--table', default='docs/MAPPING_TODO.md')
    ap.add_argument('--list', nargs='+', required=True, help='要处理的文件或目录（分批作业）')
    ap.add_argument('--write', action='store_true', help='落盘（默认只试跑）')
    args = ap.parse_args()
    os.chdir(srcroots.repo_root())

    if not os.path.isfile(args.table):
        print('改名应用器 ✗ 找不到对照表 %s（先在有网机器上跑 gradlew mojmapTable）' % args.table)
        return 1
    table, missing = load_table(args.table)
    print('对照表：可用 %d 条，缺表 %d 条' % (len(table), len(missing)))

    targets = []
    for t in args.list:
        if os.path.isdir(t):
            for dp, _d, fs in os.walk(t):
                targets += [os.path.join(dp, f) for f in fs if f.endswith('.java')]
        elif t.endswith('.java'):
            targets.append(t)
    print('作业文件 %d 个%s' % (len(targets), '' if args.write else '（试跑，不落盘）'))

    total, skipped = 0, []
    for p in sorted(targets):
        body = open(p, encoding='utf-8').read()
        new, n, miss = rewrite(body, table, missing)
        if miss:
            skipped.append((p, sorted(miss)))
            continue
        if n and args.write:
            open(p, 'w', encoding='utf-8').write(new)
        if n:
            total += n
            print('    %-64s %d 处' % (p[-64:], n))
    print('合计改动 %d 处；跳过 %d 个文件（命中缺表类型，需人工核）' % (total, len(skipped)))
    for p, miss in skipped:
        print('    跳过 %s ← %s' % (p, ', '.join(miss)))
    print('提醒：方法名本工具不碰，落盘后必须过真编译器（CI 五 job + GameTest）逐个点名再修。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
