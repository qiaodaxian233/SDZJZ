#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m510 裸调用实参个数闸（第 21 闸）：本文件里**只声明了一次、无重载、非变参**的方法，被本文件裸调用
（`name(...)` / `this.name(...)`）时，实参个数必须等于形参个数。

**它补的是无 MC jar 冒烟的第六类盲区**：`chainWants0((Level) level, from, id, 0, new HashSet<>())` 传了 5 个实参给
7 形参的方法，主线 Gradle 自 m481 起恒红 28 刀——而两代冒烟、逐文件单编（m508）全部零真错，因为 javac 对含
**不可解析类型**（`(Level) level`）实参的调用只报 cannot find symbol，**不再做重载适用性判定**，那条
"cannot be applied to given types" 在无 MC jar 时根本不会被报出来。前五类盲区（m487 已删符号/m488 括号/
m491 类型错/m497 import/m508 归因顺序）都是"报了没人看见"，这一类是"**根本不报**"，只能靠数。

判据刻意做窄以求零误报：只认本文件声明恰好一次的方法名（有重载的不数）、跳过变参、跳过 `obj.name(`/`super.name(`/
`new Name(`、剥注释与字符串后按括号深度数逗号（泛型尖括号内的逗号不算）。红=实参数≠形参数。
用法：python3 docs/tools_arity_check.py   （命中即退出码 1，挂 CI）
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DIRS = ["src/main/java", "xplat/src/main/java", "common/src/main/java", "versions/1.20.1/src/main/java",
        "versions/26.2/src/main/java", "versions/26.1/src/main/java"]
KEYWORDS = {"if", "for", "while", "switch", "return", "new", "throw", "catch", "synchronized", "super", "this",
            "else", "do", "try", "case", "assert", "yield", "instanceof"}


def strip(code):
    code = re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), code, flags=re.S)
    code = re.sub(r"//[^\n]*", lambda m: " " * len(m.group(0)), code)
    code = re.sub(r'"(?:\\.|[^"\\\n])*"', lambda m: '"' + " " * (len(m.group(0)) - 2) + '"', code)
    code = re.sub(r"'(?:\\.|[^'\\\n])'", "' '", code)
    return code


def split_top(s):
    """按深度 0 的逗号切分：()[]{} 算深度；<> 只在像泛型时算（`<` 前贴标识符、后贴标识符/`?`），
    `a < b, "msg"` 里的比较符不算——否则 chk(a < b, "…") 会被数成 1 个实参（m510 首跑就是这么误报的）。空串=0 项。"""
    if not s.strip():
        return []
    out, depth, angle, cur = [], 0, 0, []
    for i, ch in enumerate(s):
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "<":
            prev = s[i - 1] if i > 0 else " "
            nxt = s[i + 1] if i + 1 < len(s) else " "
            if (prev.isalnum() or prev == "_") and (nxt.isalnum() or nxt in "_?"):
                angle += 1
        elif ch == ">" and angle > 0 and (i == 0 or s[i - 1] != "-"):
            angle -= 1
        if ch == "," and depth == 0 and angle == 0:
            out.append("".join(cur)); cur = []
        else:
            cur.append(ch)
    out.append("".join(cur))
    return out


def balanced_span(code, open_idx):
    """从 open_idx 处的 '(' 起找到配对的 ')'，返回其下标；找不到返 -1。"""
    depth = 0
    for j in range(open_idx, len(code)):
        c = code[j]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return j
    return -1


# 声明：行首缩进 + 可选注解（匿名类里 `@Override public int nodeCount() {…}` 一行写完是常态）+ 零或多个修饰符 + 可选泛型 + 返回类型 + 名字 + `(`。修饰符**可以没有**（包私有方法，m510 首跑漏认
# StructureCore120.chainWants 五参那份，把两处合法调用当成了对三参重载的误用）。类型位若是关键字（return/new/else…）不算。
DECL = re.compile(r"^[ \t]+(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|private|protected|static|final|synchronized|abstract|default|native)\s+)*"
                  r"(?:<[^>]+>\s+)?([\w.]+(?:<[^;(){}]*>)?(?:\[\])*)\s+(\w+)\s*\(", re.M)
TYPE_KW = {"return", "new", "else", "throw", "case", "yield", "assert", "do", "break", "continue", "goto", "instanceof"}


def main():
    bad = 0
    files = 0
    checked_calls = 0
    for d in DIRS:
        base = ROOT / d
        if not base.is_dir():
            continue
        for p in sorted(base.rglob("*.java")):
            src = p.read_text(encoding="utf-8", errors="replace")
            code = strip(src)
            files += 1
            # 声明：名字→形参个数（变参记 -1；重载记 None）
            decls = {}
            decl_ends = set()  # **只收真被认作声明的位置**——m510 自证第一遍就栽在这：把 DECL 的全部原始命中当声明位置，
                               # `return chainWants0(` 那一行（类型位=return，本该被 TYPE_KW 排除）也进了集合，调用被当声明跳过，
                               # 血案重放居然绿。尺子自证要用它声称能防的那次血案（m506 教训），这回真防住了才算立起来。
            for m in DECL.finditer(code):
                name = m.group(2)
                if name in KEYWORDS or m.group(1).split("<")[0].split("[")[0] in TYPE_KW:
                    continue
                close = balanced_span(code, m.end() - 1)
                if close < 0:
                    continue
                # 声明后必须是 { 或 throws（排除调用被误判成声明的极端形状）
                tail = code[close + 1:close + 60].lstrip()
                if not (tail.startswith("{") or tail.startswith("throws")):
                    continue
                params = code[m.end():close]
                n = -1 if "..." in params else len(split_top(params))
                decls[name] = None if name in decls else n
                decl_ends.add(m.end())
            # 只查小写开头的名字：大写开头=构造器/record 头（调用一律经 new/this/super，不在裸调用里）
            targets = {k: v for k, v in decls.items() if v is not None and v >= 0 and k[:1].islower()}
            if not targets:
                continue
            # 裸调用：前面不是 . 或字母数字（排除 obj.name( / 声明本身已在 decls 里按位置排除）
            for m in re.finditer(r"(?<![\w.])(?:this\s*\.\s*)?(\w+)\s*\(", code):
                name = m.group(1)
                if name not in targets:
                    continue
                if m.end() in decl_ends:      # 声明位置本身
                    continue
                if re.search(r"\bnew\s+$", code[max(0, m.start() - 20):m.start()]):  # new Name(
                    continue
                close = balanced_span(code, m.end() - 1)
                if close < 0:
                    continue
                args = code[m.end():close]
                # lambda 体里含 -> 的实参照常数（深度 0 逗号分隔不受影响）
                n = len(split_top(args))
                checked_calls += 1
                if n != targets[name]:
                    line = code[:m.start()].count("\n") + 1
                    print(f"❌ {p.relative_to(ROOT)}:{line} 调用 {name}(...) 传了 {n} 个实参，声明是 {targets[name]} 个形参"
                          f"（无 MC jar 冒烟对此类错**不报**，这就是 m481 主线断了 28 刀的形状）")
                    bad += 1
    if bad:
        print(f"❌ 共 {bad} 处实参个数不匹配——Gradle 真编译会红")
        sys.exit(1)
    print(f"✅ 裸调用实参个数闸绿：{files} 文件 / {checked_calls} 处单声明方法裸调用逐个对表")


if __name__ == "__main__":
    main()
