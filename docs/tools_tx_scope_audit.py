#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m327 事务作用域手账审计（m323 立的规矩配尺子）。

规矩：**Fabric 事务作用域内禁调手账口**（withdraw / withdrawExact / deposit / depositExact）。
根因（m323 边界立档）：事务的增量 undo 只记自己碰过的键的**前像**；作用域内混入手账改动后
若事务 abort，同键前像会把手账改动整个覆盖——玩家已到手的物品加上被还原的账本 = 复制窗。
异键手账能存活（m278 性质，GameTest 十六号用例判官），但同键/异键在静态尺上不可判，
故一刀切：作用域内一律不许手账，需要混部就先 commit 再手账（m323 用例的串行段就是范式）。

扫描口径：
- src/main/java 全量，**排除 gametest/**（测试有权故意踩边界验语义，十六号用例即是）；
- try-with-resources 打开 Transaction 的块体（try (... Transaction.openOuter/openNested ...)），
  按花括号配平取块体范围；
- 块体内命中 .withdraw( / .withdrawExact( / .deposit( / .depositExact( 即红（先剥注释防误报，
  m291b 教训）；行尾带 `tx手账豁免` 标记可豁免（须注明理由，评审时人工复核）。
- 自证：内置坏样本必须能抓到（m288 口径：尺子先证明自己能抓坏的）。
"""
import os
import re
import sys
import srcroots  # m406 源根解析（路径逻辑唯一出口）

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_ROOTS = [os.path.join(ROOT, *r.split("/")) for r in srcroots.ROOTS]  # m369 双根 → m406 多源集
OPEN_RE = re.compile(r"try\s*\(.*Transaction\s+\w+\s*=\s*Transaction\.open(Outer|Nested)")
BAD_RE = re.compile(r"\.(withdrawExact|withdraw|depositExact|deposit)\s*\(")
WAIVER = "tx手账豁免"


def strip_comments(src: str) -> str:
    """剥块注释与行注释（保行数：注释区间以等长空白顶替，行号报告不漂）。"""
    out = []
    i, n = 0, len(src)
    in_block = in_line = in_str = in_chr = False
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if in_block:
            out.append("\n" if c == "\n" else " ")
            if c == "*" and nxt == "/":
                out.append(" "); i += 2; in_block = False; continue
            i += 1; continue
        if in_line:
            if c == "\n": in_line = False; out.append(c)
            else: out.append(" ")
            i += 1; continue
        if in_str:
            out.append(c)
            if c == "\\": out.append(nxt); i += 2; continue
            if c == '"': in_str = False
            i += 1; continue
        if in_chr:
            out.append(c)
            if c == "\\": out.append(nxt); i += 2; continue
            if c == "'": in_chr = False
            i += 1; continue
        if c == "/" and nxt == "*": in_block = True; out.append("  "); i += 2; continue
        if c == "/" and nxt == "/": in_line = True; out.append("  "); i += 2; continue
        if c == '"': in_str = True; out.append(c); i += 1; continue
        if c == "'": in_chr = True; out.append(c); i += 1; continue
        out.append(c); i += 1
    return "".join(out)


def scan_source(text: str, relpath: str):
    """返回 [(行号, 行内容)] 违规清单。"""
    clean = strip_comments(text)
    raw_lines = text.splitlines()
    lines = clean.splitlines()
    hits = []
    li = 0
    while li < len(lines):
        if OPEN_RE.search(lines[li]):
            # 找到 try 体起始 '{'（可能同行也可能下一行），随后花括号配平到闭合
            depth = 0
            started = False
            lj = li
            while lj < len(lines):
                for ch in lines[lj]:
                    if ch == "{":
                        depth += 1; started = True
                    elif ch == "}":
                        depth -= 1
                if started:
                    m = BAD_RE.search(lines[lj])
                    if m and WAIVER not in raw_lines[lj]:
                        hits.append((lj + 1, raw_lines[lj].strip()))
                if started and depth <= 0:
                    break
                lj += 1
            li = lj + 1
        else:
            li += 1
    return hits


BAD_SAMPLE = """
class Bad {
    void f(StorageCoreBlockEntity c) {
        try (Transaction tx = Transaction.openOuter()) {
            long e = c.fabricStorage().extract(v, 50, tx); // FTA 允许
            int m = c.withdraw("minecraft:dirt", 10); // 应被抓
            tx.commit();
        }
        c.deposit(st); // 作用域外，不该抓
    }
}
"""

GOOD_SAMPLE = """
class Good {
    void f(StorageCoreBlockEntity c) {
        try (Transaction tx = Transaction.openOuter()) {
            long i = t.insert(v, n, tx);
            tx.commit();
        }
        int m = c.withdraw("minecraft:dirt", 10); // 先 commit 再手账=范式
        // try (Transaction tx = Transaction.openOuter()) { c.withdraw(x, 1); } 注释里不算
    }
}
"""


def main() -> int:
    # ① 坏样本自证（m288 口径：先证明尺子能抓坏的，再谈全库干净）
    bad_hits = scan_source(BAD_SAMPLE, "<bad_sample>")
    if len(bad_hits) != 1 or "withdraw" not in bad_hits[0][1]:
        print("✗ 尺子自证失败：坏样本应恰命中 1 处 withdraw，实得", bad_hits)
        return 1
    if scan_source(GOOD_SAMPLE, "<good_sample>"):
        print("✗ 尺子自证失败：好样本误报", scan_source(GOOD_SAMPLE, "<good_sample>"))
        return 1

    # ② 全库扫描（排除 gametest：测试有权故意踩边界验语义）
    bad = []
    files = 0
    for dirpath, _dirs, names in (x for r in SRC_ROOTS for x in os.walk(r)):
        if os.sep + "gametest" in dirpath:
            continue
        for name in names:
            if not name.endswith(".java"):
                continue
            files += 1
            p = os.path.join(dirpath, name)
            with open(p, encoding="utf-8") as f:
                text = f.read()
            for ln, content in scan_source(text, p):
                bad.append((os.path.relpath(p, ROOT), ln, content))
    if bad:
        print("✗ 事务作用域内发现手账调用（m323 规矩：先 commit 再手账，或行尾 tx手账豁免+理由）：")
        for path, ln, content in bad:
            print(f"  {path}:{ln}  {content}")
        return 1
    print(f"事务作用域手账审计 ✓ 自证通过；{files} 个生产文件零命中（gametest 按档排除）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
