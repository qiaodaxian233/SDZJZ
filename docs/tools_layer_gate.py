#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m406 分层硬闸（CI 第十三道）——多源集分层的守门人。

规矩两条：
  ① **xplat 零加载器引用**：`xplat/` 下不许出现 `net.fabricmc` / `FabricLoader` / `ModInitializer`
     等任何加载器符号（含注释外的字面）。破了即红——xplat 的全部意义就是"见 MC、不见加载器"。
  ② **待接口化清单（报告不红）**：xplat 里引用加载器层漏斗类（Net/ClientNet/Xfer/Hooks/Env/ClientHooks）
     的文件逐个点名。这些是 m402~m405 收口留下的"静态漏斗"（m433 起逐名接口化销账），将来要改成
     "xplat 定接口 + 各加载器源集给实现 + Platform 定位器取"，改一个销一个。

用法：python3 docs/tools_layer_gate.py   退出码 1=①被破。
"""
import os
import re
import sys

import srcroots

XPLAT = 'xplat/src/main/java'
LOADER_SYMBOLS = re.compile(r'net\.fabricmc|FabricLoader|\bModInitializer\b|\bClientModInitializer\b')
# m433：Net/ClientNet 已接口化销账（门面迁 xplat+Fabric 给 Impl+入口首行安装），从清单摘除。
# m434：Xfer 接口化销账（同 m433 范式+move 双句柄泛化）。
FUNNELS = ['com.sdzjz.loader.Hooks', 'com.sdzjz.loader.Env', 'com.sdzjz.client.ClientHooks']


def strip_comments(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    return re.sub(r'//[^\n]*', '', src)


def main():
    root = srcroots.repo_root()
    base = os.path.join(root, *XPLAT.split('/'))
    if not os.path.isdir(base):
        print('分层硬闸 ✗ 找不到 %s' % XPLAT)
        return 1
    bad, pending, total = [], [], 0
    for dp, _dirs, fs in os.walk(base):
        for f in fs:
            if not f.endswith('.java'):
                continue
            total += 1
            p = os.path.join(dp, f)
            body = strip_comments(open(p, encoding='utf-8').read())
            rel = os.path.relpath(p, base)
            if LOADER_SYMBOLS.search(body):
                bad.append(rel)
            hits = [x.rsplit('.', 1)[-1] for x in FUNNELS if x in body]
            if hits:
                pending.append((rel, hits))
    if bad:
        print('分层硬闸 ✗ xplat 里出现加载器符号（%d 个文件）：' % len(bad))
        for r in bad[:20]:
            print('    %s' % r)
        return 1
    print('分层硬闸 ✓ xplat %d 文件零加载器符号' % total)
    print('    待接口化（引用加载器层漏斗类，报告不红）：%d 文件' % len(pending))
    for r, hits in sorted(pending)[:20]:
        print('      %-52s %s' % (r, '/'.join(hits)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
