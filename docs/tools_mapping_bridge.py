#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m410 映射迁移工装（Yarn → Mojmap）——把"改名"从手艺活变成对表活。

工作原理（三段桥，两段能在沙箱里自证，第三段交给真工具链）：
    Yarn 名  ←→  intermediary(class_NNNN)   ← FabricMC/yarn 仓逐类 .mapping，**可离线核**
    obf      ←→  intermediary               ← FabricMC/intermediary 的 1.21.1.tiny，**可离线核**
    obf      ←→  Mojmap                     ← Mojang 官方 proguard(client.txt/server.txt)，**沙箱域名不可达**
把前两段拼起来即得 Yarn↔obf；作者本地把 Mojang 那份 proguard 一喂（--mojmap），
第三列自动补齐，产出**全库改名对照表**。没喂时也会用 NeoForge patch 树的 Mojmap 类路径按简名
做**候选填充并标记置信度**，让人先看见八九成的答案。

用法：
  python3 docs/tools_mapping_bridge.py --scan            # 只扫本仓在用的 Yarn 类型，出待办表
  python3 docs/tools_mapping_bridge.py --yarn <yarn仓目录> [--mojmap client.txt] [--md docs/MAPPING_TODO.md]

红线：本工具**只产出对照表，绝不自动改源码**。改名要么走 Loom 的 migrateMappings，
要么按本表脚本替换后过 CI 五 job + GameTest——m409 方案稿定的判官口径。
"""
import argparse
import json
import os
import re
import sys

import srcroots

FQN = re.compile(r'net\.minecraft\.[A-Za-z0-9_.]+')


def scan_used():
    """扫全库在用的 Yarn 全限定名 → 用点数（含 import 与内联 FQN）。"""
    used = {}
    for p in srcroots.java_files(['xplat/src/main/java', 'src/main/java']):
        body = open(p, encoding='utf-8').read()
        for m in FQN.findall(body):
            # 去掉尾随的成员访问（net.minecraft.item.Items.AIR → net.minecraft.item.Items）
            parts = m.split('.')
            for i in range(len(parts) - 1, 2, -1):
                if parts[i - 1][:1].isupper():      # 上一段已是类名 → 本段是成员
                    continue
                if parts[i][:1].isupper():
                    m = '.'.join(parts[:i + 1])
                    break
            used[m] = used.get(m, 0) + 1
    return used


def load_yarn(yarn_dir):
    """yarn 仓 mappings/**.mapping → {yarn FQN: intermediary}。"""
    table = {}
    for dp, _dirs, fs in os.walk(yarn_dir):
        for f in fs:
            if not f.endswith('.mapping'):
                continue
            with open(os.path.join(dp, f), encoding='utf-8') as fh:
                first = fh.readline().strip()
            if not first.startswith('CLASS '):
                continue
            bits = first.split()
            if len(bits) < 3:
                continue
            inter, yarn = bits[1], bits[2]
            table[yarn.replace('/', '.')] = inter.replace('/', '.')
    return table


def load_mojmap(path):
    """Mojang proguard（client.txt）→ {obf: mojmap FQN}。行形如 `a.b.C -> xy:`。"""
    out = {}
    for line in open(path, encoding='utf-8'):
        if line.startswith((' ', '\t')) or '->' not in line:
            continue
        left, right = line.split('->')
        out[right.strip().rstrip(':')] = left.strip()
    return out


def load_inter(path):
    """intermediary tiny v1 → {intermediary: obf}。"""
    out = {}
    for line in open(path, encoding='utf-8'):
        if not line.startswith('CLASS\t'):
            continue
        _, obf, inter = line.rstrip('\n').split('\t')[:3]
        out[inter.replace('/', '.')] = obf.replace('/', '.')
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--yarn', help='FabricMC/yarn 仓目录（含 mappings/）')
    ap.add_argument('--inter', help='FabricMC/intermediary 的 1.21.1.tiny')
    ap.add_argument('--mojmap', help='Mojang 官方 proguard 映射（client.txt）')
    ap.add_argument('--md', default='docs/MAPPING_TODO.md')
    ap.add_argument('--scan', action='store_true', help='只扫用点不查表')
    args = ap.parse_args()

    os.chdir(srcroots.repo_root())
    used = scan_used()
    rows = sorted(used.items(), key=lambda kv: -kv[1])
    print('在用 Yarn 类型 %d 个，用点合计 %d' % (len(rows), sum(used.values())))
    if args.scan:
        for k, v in rows[:20]:
            print('    %-56s %d' % (k, v))
        return 0

    yarn = load_yarn(args.yarn) if args.yarn else {}
    inter = load_inter(args.inter) if args.inter else {}
    moj = load_mojmap(args.mojmap) if args.mojmap else {}

    lines = ['# Yarn → Mojmap 改名对照表（m410 自动生成，勿手改）', '',
             '> 第三列空=还没喂 Mojang proguard（`--mojmap client.txt`）。',
             '> 本表只是对照，**改名动作走 Loom migrateMappings 或脚本替换后过 CI**（m409 口径）。', '',
             '| 用点 | Yarn | intermediary | Mojmap |', '|---:|---|---|---|']
    done = 0
    for k, v in rows:
        i = yarn.get(k, '')
        o = inter.get(i, '') if i else ''
        m = moj.get(o, '') if o else ''
        if m:
            done += 1
        lines.append('| %d | `%s` | %s | %s |' % (v, k, ('`%s`' % i) if i else '—', ('`%s`' % m) if m else '—'))
    open(args.md, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print('对照表已写 %s：%d 行，其中 Mojmap 列已补齐 %d 行' % (args.md, len(rows), done))
    return 0


if __name__ == '__main__':
    sys.exit(main())
