#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m406 尺子共用源根解析器——多源集分层后，Java 源不再只住一个地方。

教训（本笔当场吃到）：此前十一把尺子里散着十几处写死的
`src/main/java/com/sdzjz/....java`，每搬一次家就要满地改路径，改漏一处就是"尺子失明"
（m402 刚栽过一次：有界 Codec 尺按旧写法找目标，静默报"0 个包全部有界"）。
从此路径逻辑只此一份，尺子只说"我要哪个类"，不说"它住哪"。

源根顺序=查找优先级；新增源集只改这一处。
"""
import os

ROOTS = [
    'xplat/src/main/java',            # m406 业务层（见 MC、不见加载器）
    'src/main/java',                  # Fabric 1.21.1 加载器层（结构性接口/入口/漏斗，待迁 versions/）
    'common/src/main/java',           # Core（零 MC）
    'versions/26.2/src/main/java',    # 26.2 世代 bootstrap
]


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def find(rel, required=True):
    """按源根优先级找一个相对包路径（如 'com/sdzjz/registry/ModItems.java'），返回绝对路径。"""
    root = repo_root()
    for r in ROOTS:
        p = os.path.join(root, r, rel)
        if os.path.isfile(p):
            return p
    if required:
        raise FileNotFoundError('源根里找不到 %s（源根=%s）' % (rel, ROOTS))
    return None


def read(rel, required=True):
    p = find(rel, required)
    return open(p, encoding='utf-8').read() if p else None


def java_files(roots=None):
    """遍历全部（或指定）源根下的 .java 绝对路径。"""
    root = repo_root()
    out = []
    for r in (roots or ROOTS):
        base = os.path.join(root, r)
        if not os.path.isdir(base):
            continue
        for dp, _dirs, fs in os.walk(base):
            for f in fs:
                if f.endswith('.java'):
                    out.append(os.path.join(dp, f))
    return out
