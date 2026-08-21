#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m423 坏尺子普查（m109「校验器自身也要先怀疑」制度化）。

背景：mojmap 迁移把全库 MC 成员名换了一遍。校验器里**硬编码的 MC 标识符**若还是 Yarn 名，
它在源码里将永远命中 0 次——**尺子还在绿，但已经什么都不量了**。假绿比红更危险：
m423 当场抓到 tools_platform_scan.py 的 API 族正则整族失真（nbt/component 只剩 1 用点、
text/i18n 整族消失、network 4 用点），而它是 m361 多版本架构的地雷图数据源，数错了要带偏排期。

做法：把每个 tools_*.py 里的字符串字面量拆词（先吃掉 \\b 之类正则转义，防 `\\bXxx` 被读成 `bXxx`），
取像 MC 类名/成员名的（驼峰或全大写下划线），逐个在源码里数命中；0 命中的列为疑似坏尺子模式。

**报告工具，不是闸**（退出码恒 0）：0 命中未必是坏——也可能是"本仓尚未用到的 API"
（如 loader_scan 里的 UseBlockCallback、platform_scan 里将来才有的 SPI 接口名），须人工判。
真正门控的是 tools_yarn_residue_check.py（第 14 闸，判源码里有没有 Yarn 名）。

跑法：python3 docs/tools_gauge_audit.py
"""
import collections
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import srcroots  # m406 源根解析（路径逻辑唯一出口）

ROOT = srcroots.repo_root()

# 源码全文（含注释，命中判定宁宽勿严：宁可漏报坏尺子，不可误杀好尺子）
FILES = srcroots.java_files()
SRC = "\n".join(open(p, encoding="utf-8", errors="replace").read() for p in FILES)

IGNORE = re.compile(
    r"^(True|False|None|utf|encoding|errors|replace|findall|finditer|startswith|endswith|"
    r"readlines|isdigit|isupper|islower|maxsplit|DOTALL|IGNORECASE|MULTILINE|"
    r"__main__|__file__|dirname|abspath|basename|relpath|splitext|walk|join|sorted|append|"
    r"extend|strip|rstrip|lstrip|format|Counter|defaultdict|namedtuple|SystemExit|Exception|"
    r"ValueError|AssertionError|README|DEVLOG|HANDOVER)$", re.I)

# 迁移工装本身的字面量就是 Yarn 名（那是它的原料），普查时跳过
SKIP = {"tools_mapping_bridge.py", "tools_mapping_apply.py", "tools_gauge_audit.py",
        "tools_yarn_residue_check.py"}

def yarn_names():
    """已知 Yarn 旧名（第 14 闸的黑名单）——尺子里留着它们是**预期**（老分支/老文档回读仍能量），
    不算坏尺子，单独分桶报告，免得噪音淹掉真问题。"""
    p = os.path.join(ROOT, "docs", "YARN_BLACKLIST.txt")
    if not os.path.isfile(p):
        return set()
    out = set()
    for ln in open(p, encoding="utf-8"):
        ln = ln.split("#")[0].strip()
        if ln and not ln.startswith("["):
            out.add(ln.rsplit(".", 1)[-1])
    return out


YARN = yarn_names()
LITERAL = re.compile(r"""(['"])(?:\\.|(?!\1).)*\1""")
WORD = re.compile(r"[A-Za-z_][A-Za-z0-9_$]{3,}")
ESCAPE = re.compile(r"\\[bBwWsSdDAZnrt]")

报告 = []
for f in sorted(os.listdir(os.path.join(ROOT, "docs"))):
    if not (f.startswith("tools_") and f.endswith(".py")) or f in SKIP:
        continue
    txt = open(os.path.join(ROOT, "docs", f), encoding="utf-8").read()
    cand = collections.Counter()
    for m in LITERAL.finditer(txt):
        raw = ESCAPE.sub(" ", m.group(0)[1:-1])
        for s in WORD.findall(raw):
            if IGNORE.match(s):
                continue
            if not (re.search(r"[a-z][A-Z]", s) or (s.isupper() and "_" in s)):
                continue
            cand[s] += 1
    死 = sorted(s for s in cand if s not in SRC)
    预期 = [s for s in 死 if s in YARN]
    可疑 = [s for s in 死 if s not in YARN]
    if 死:
        报告.append((f, 可疑, 预期))

print("=== m423 坏尺子普查：校验器硬编码标识符在源码里命中 0 次的 ===")
if not any(x[1] or x[2] for x in 报告):
    print("✓ 全部校验器的硬编码标识符均在源码有命中，无假绿嫌疑")
for f, 可疑, 预期 in 报告:
    if not 可疑 and not 预期:
        continue
    print("\n⚠ %s" % f)
    for s in 可疑:
        print("    命中0  %s   ← 人工判：是坏尺子，还是本仓尚未用到的 API？" % s)
    if 预期:
        print("    命中0（Yarn 旧名 %d 个，预期留存不算坏）：%s" % (len(预期), ", ".join(预期)))
print("\n扫描源码 %d 个 java 文件（源根=%s）；报告供人工判，本脚本不改代码、恒退 0。"
      % (len(FILES), "/".join(srcroots.ROOTS)))
