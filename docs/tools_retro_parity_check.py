#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m470 两代手抄名单对表闸（第 16 闸）。

**它管什么**：C2 这条 1.20.1 线是**仿写**不是移植（Common 还没抽干净，业务代码两边各写一份）。
仿写的头号漏点不是方法体——方法体抄漏了编不过；是**名单/常量/判定表这类"数据"**：
漏一条不报错、判官照绿、只有眼睛能看出来。m469 就是这么丢的（数据线 PLUG 名单六块只抄了一块，
结构核心/数据面板怼上去不伸插头，而 BFS 只认方块类型所以逻辑还是通的，全部判官零反应）。

**判据**：对每个登记的对表项，从蓝本方法体里抓一组名字（如 `ModBlocks.X`），
从本世代方法体里抓对应的一组（`RetroBlocks.X`），
断言 **蓝本名单 ∩ 本世代已建方块 ⊆ 本世代名单**。
本世代还没造出来的方块（无线节点/卫星节点/交易所…）不算漏抄，自动豁免并在日志里列出。

**加新对表项**：往 ITEMS 里加一条即可，不改逻辑。
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

# 本世代已建方块：RetroBlocks 里 reg("xxx") 的登记表（snake → 常量名）
RETRO_BLOCKS_SRC = "versions/1.20.1/src/main/java/com/sdzjz/retro/RetroBlocks.java"

ITEMS = [
    {
        "名称": "数据线端点 PLUG 自家方块名单（m469 血案）",
        "蓝本文件": "xplat/src/main/java/com/sdzjz/block/DataCableBlock.java",
        "蓝本方法": "endFor",
        "蓝本前缀": "ModBlocks",
        "本世代文件": "versions/1.20.1/src/main/java/com/sdzjz/retro/DataCableBlock120.java",
        "本世代方法": "endFor",
        "本世代前缀": "RetroBlocks",
    },
]


def 读(rel):
    p = ROOT / rel
    if not p.exists():
        raise SystemExit("对表闸：找不到文件 " + rel)
    return p.read_text(encoding="utf-8")


def 剥注释(s):
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
    return re.sub(r"//[^\n]*", "", s)


def 取方法体(src, 方法名, 文件名):
    """从方法名后的第一个 { 起做花括号配对，返回方法体（已剥注释）。"""
    m = re.search(r"\b" + re.escape(方法名) + r"\s*\([^)]*\)\s*\{", src)
    if not m:
        raise SystemExit("对表闸：%s 里找不到方法 %s（改名了就同步改 ITEMS）" % (文件名, 方法名))
    i = m.end() - 1
    深 = 0
    for j in range(i, len(src)):
        if src[j] == "{":
            深 += 1
        elif src[j] == "}":
            深 -= 1
            if 深 == 0:
                return 剥注释(src[i:j + 1])
    raise SystemExit("对表闸：%s 的 %s 花括号不配对" % (文件名, 方法名))


def 抓名单(体, 前缀):
    return set(re.findall(r"\b" + re.escape(前缀) + r"\.([A-Z][A-Z0-9_]*)\b", 体))


def 本世代已建方块():
    src = 剥注释(读(RETRO_BLOCKS_SRC))
    # 形如 STORAGE_CORE = reg("storage_core", ...)
    名 = set(re.findall(r"\b([A-Z][A-Z0-9_]*)\s*=\s*reg\s*\(", src))
    if not 名:
        raise SystemExit("对表闸：RetroBlocks 里一条 reg( 登记都没抓到——正则该修了（m109 坏尺子家法：先怀疑尺子）")
    return 名


def 主():
    已建 = 本世代已建方块()
    print("本世代已建方块 %d 个：%s" % (len(已建), "、".join(sorted(已建))))
    坏 = 0
    for it in ITEMS:
        蓝本 = 抓名单(取方法体(读(it["蓝本文件"]), it["蓝本方法"], it["蓝本文件"]), it["蓝本前缀"])
        本代 = 抓名单(取方法体(读(it["本世代文件"]), it["本世代方法"], it["本世代文件"]), it["本世代前缀"])
        if not 蓝本:
            print("❌ %s：蓝本名单抓到 0 条，先怀疑正则不是先怀疑代码" % it["名称"])
            坏 += 1
            continue
        应有 = 蓝本 & 已建
        缺 = 应有 - 本代
        豁免 = 蓝本 - 已建
        print("\n【%s】" % it["名称"])
        print("  蓝本 %d 条：%s" % (len(蓝本), "、".join(sorted(蓝本))))
        print("  本世代 %d 条：%s" % (len(本代), "、".join(sorted(本代))))
        if 豁免:
            print("  豁免（本世代还没造出来）：%s" % "、".join(sorted(豁免)))
        if 缺:
            print("  ❌ 漏抄 %d 条：%s" % (len(缺), "、".join(sorted(缺))))
            坏 += 1
        else:
            print("  ✅ 本世代已建的都在名单里")
    if 坏:
        print("\n❌ 对表闸红：%d 个对表项漏抄。仿写路线的名单必须逐条对齐蓝本（m469 教训）。" % 坏)
        return 1
    print("\n✅ 对表闸绿：%d 个对表项全对齐。" % len(ITEMS))
    return 0


if __name__ == "__main__":
    sys.exit(主())
