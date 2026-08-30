#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m487 悬空引用闸（第 19 闸）：下沉/合一之后，**已经删掉的字段与方法是否还被本文件其它地方引用**。

**它补的是纯语法冒烟的盲区**。javac 无 MC jar 冒烟时，`cannot find symbol` 会有成千上万条
（全是缺 net.minecraft.* 的噪音），自家符号的真错就淹在里面——m485/m486 我按"自家新符号定向
grep"筛，只筛了**新增**的符号，没筛**删除**的符号还有没有人在用。结果 1.20.1 Gradle 真编译
15 个错误：构造器、connectedCores、loadedCoreAt、acceptsPlainType 被按行号切段时连带删掉，
FabricLedger120 内部类还在用 exactTpl，存档还在写 xpBank。

**判据**：对每个"做过下沉"的文件，列出本刀删掉的符号名；若该名在本文件里**已无声明**却仍被
`名.` / `名(` / `名[` 引用（排除 `ledger.名` 这类合法转发），即红。

**加新文件/新符号**：往 目标 表加一行。合一做完之后应当把对应条目留着——它是回归网。
"""
import re, sys, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
目标 = {
 "src/main/java/com/sdzjz/block/StorageCoreBlockEntity.java":
   ["exactTpl","exactN","exactIdx","store","xpBank","tier","exactIndexOf","typeGate","satAdd",
    "exactIdxAppended","exactIdxRemoved","storeRev","exactRev"],
 "versions/1.20.1/src/main/java/com/sdzjz/retro/StorageCore120.java":
   ["exactTpl","exactN","exactIdx","store","xpBank","exactIndexOf","typeGate",
    "exactIdxAppended","exactIdxRemoved","storeRev","exactRev"],
 "src/main/java/com/sdzjz/block/StructureCoreBlockEntity.java": [],
 "versions/1.20.1/src/main/java/com/sdzjz/retro/StructureCore120.java": [],
}
坏=0
for rel, 名单 in 目标.items():
    src=(ROOT/rel).read_text(encoding="utf-8")
    码=re.sub(r"/\*.*?\*/", " ", src, flags=re.S)          # 块注释
    码=re.sub(r"//[^\n]*", " ", 码)                          # 行注释（含行尾）
    码=re.sub(r'"(?:\\.|[^"\\])*"', '""', 码)                # 字符串字面量（NBT 键名等）
    # 本文件里声明了哪些字段/方法
    声明=set(re.findall(r"(?:private|public|protected|static|final|transient|\s)+[\w<>,.\[\]\s]+\s(\w+)\s*[=;(]", 码))
    for n in 名单:
        if n in 声明: continue          # 还声明着=没删，不管
        # m487 修尺子：原正则只认 `名.` / `名(` / `名[`，漏掉了**裸标识符**用法
        # （new ArrayList<>(exactTpl)、putLong("k", xpBank) 都是裸用），自证时没红就是这个原因。
        # 改为认任何非声明位置的标识符出现（前后不是字母数字/点/引号）。
        用=[m.start() for m in re.finditer(r"(?<![\w.\"])"+re.escape(n)+r"(?![\w\"])", 码)]
        # 排除 ledger.xxx 这类合法转发
        真用=[p for p in 用 if not 码[max(0,p-8):p].rstrip().endswith("ledger.")]
        if 真用:
            行=码[:真用[0]].count("\n")+1
            print(f"❌ {rel}: 已删符号 `{n}` 仍被引用（首处约第 {行} 行，共 {len(真用)} 处）")
            坏+=1
print("✅ 悬空引用普查干净" if not 坏 else f"\n❌ {坏} 个悬空引用——Gradle 真编译会红")
sys.exit(1 if 坏 else 0)
