#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m271 重复方法定义回归尺。

冒烟盲区 #3（m268 实锤，前两种见 m123/m180、m257）：方法参数类型是 MC 类（沙箱解析不了）时，
javac 判不出两个声明同签名——"method already defined" 在沙箱**根本不报**，只有 CI 真编译才现形。
m268 的 isForcedNow 定义了两遍，本地冒烟全绿、CI 红了三笔（fc603c6/1488dac/85643e6 连坐）。

本尺按文本层面扫：同一 .java 文件内，同名且参数类型序列相同的方法声明出现 ≥2 次即报。
- 合法重载（同名不同参）不报；不同文件同签名不报（各归各类）。
- 嵌套类里的同签名方法理论上合法但极罕见，本仓零先例——宁可误报人工看一眼，不可漏报 CI 红。
- 自锚定路径（m259 教训：不写死沙箱路径）；命中退出码 1，可挂 CI 校验闸。
用法：python3 docs/tools_dup_method_check.py
"""
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SRC = os.path.join(ROOT, "src", "main", "java")
SRC2 = os.path.join(ROOT, "common", "src", "main", "java")  # m369 双根

# 方法声明行：修饰符开头…返回类型 方法名(参数) …{ ——排除控制流关键字与构造器噪音由签名归一化兜底
DECL = re.compile(
    r'^\s*(?:(?:public|private|protected|static|final|abstract|synchronized|native|default)\s+)+'
    r'[\w<>\[\],.\s?]+?\s+(\w+)\s*\(([^)]*)\)\s*(?:throws\s+[\w.,\s]+)?\s*\{'
)
KEYWORDS = {"if", "for", "while", "switch", "catch", "return", "new", "else", "do", "try"}


def param_types(params: str) -> str:
    """参数串 → 归一化类型序列（去参名/去泛型细节/去 final/去限定包名，保留裸类型名与数组维度）。"""
    params = params.strip()
    if not params:
        return ""
    out = []
    depth = 0
    cur = []
    for ch in params:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        elif ch == "," and depth == 0:
            out.append("".join(cur))
            cur = []
            continue
        if depth == 0 and ch not in "<>":
            cur.append(ch)
    out.append("".join(cur))
    types = []
    for p in out:
        toks = p.replace("final ", " ").split()
        if not toks:
            continue
        t = toks[0] if len(toks) == 1 else " ".join(toks[:-1])  # 掉参名
        t = t.split(".")[-1]  # 掉限定包名：net.minecraft.util.math.ChunkPos → ChunkPos
        types.append(t.strip())
    return ",".join(types)


def main() -> int:
    hits = 0
    for dirpath, _dirs, files in (x for r in (SRC, SRC2) for x in os.walk(r)):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(dirpath, fn)
            seen = {}
            with open(path, encoding="utf-8") as f:
                for ln, line in enumerate(f, 1):
                    m = DECL.match(line)
                    if not m:
                        continue
                    name = m.group(1)
                    if name in KEYWORDS:
                        continue
                    sig = name + "(" + param_types(m.group(2)) + ")"
                    if sig in seen:
                        rel = os.path.relpath(path, ROOT)
                        print(f"重复方法定义: {rel}:{seen[sig]} 与 :{ln} 同签名 {sig}")
                        hits += 1
                    else:
                        seen[sig] = ln
    if hits:
        print(f"共 {hits} 处重复定义（CI 真编译必红，冒烟盲区#3 看不见它）")
        return 1
    print("重复方法定义检查 ✓ 全库零命中")
    return 0


if __name__ == "__main__":
    sys.exit(main())
