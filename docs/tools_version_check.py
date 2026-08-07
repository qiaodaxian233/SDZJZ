#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m317 版本号回归尺——作者定版本方案：mod_version = 0.1.<里程碑号>，一笔一跳。

规则：gradle.properties 的 mod_version 第三段（数字）必须等于 DEVLOG.md 全部
`## mNNN` 标题里的最大 NNN。热修类字母尾号（m316b 之类）不抬版本（数字段不变）。
本尺挂 CI：忘跳版本=红，逼下一个会话补上。命中退出码 1。

跑法：python3 docs/tools_version_check.py
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

props = open(os.path.join(ROOT, 'gradle.properties'), encoding='utf-8').read()
m = re.search(r'^mod_version=(\d+)\.(\d+)\.(\d+)\s*$', props, re.M)
if not m:
    print('✗ gradle.properties 缺 mod_version=X.Y.Z 行（三段数字）')
    sys.exit(1)
ver = (int(m.group(1)), int(m.group(2)), int(m.group(3)))

devlog = open(os.path.join(ROOT, 'DEVLOG.md'), encoding='utf-8').read()
nums = [int(n) for n in re.findall(r'^## m(\d+)', devlog, re.M)]
if not nums:
    print('✗ DEVLOG.md 找不到任何 "## mNNN" 里程碑标题')
    sys.exit(1)
latest = max(nums)

if ver != (0, 1, latest):
    print(f'✗ 版本号没跳：mod_version={ver[0]}.{ver[1]}.{ver[2]}，DEVLOG 最新里程碑=m{latest}，'
          f'应为 0.1.{latest}（m317 方案：一笔一跳）')
    sys.exit(1)
print(f'✓ 版本号对表：mod_version=0.1.{latest} = DEVLOG 最新里程碑 m{latest}')
