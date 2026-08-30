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

**m472 三个可选键**（缺省=旧行为逐位不变，m470 首批条目零改动）：
- "整文件": True —— 不取方法体，整个文件（剥注释后）当名单来源。用于名单散在多个小方法里的类
  （如 NodeIdent 六族一族一方法）。
- "豁免": False —— 关掉"本世代已建方块"豁免，蓝本名单必须**逐条**出现在本世代名单里。
  用于与 RetroBlocks 无关的名单（方块豁免表对它是错的尺子——交集恒空=永远虚绿）。
- "正则": r"..." —— 自定义抓取正则（必须带一个捕获组），替代默认的 前缀.大写常量。
  用于方法名类名单（如 NodeTags.isXxx 类型判定表）。此时"蓝本前缀/本世代前缀"两键可省。
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

# 本世代已建方块：RetroBlocks 里 reg("xxx") 的登记表（snake → 常量名）
RETRO_BLOCKS_SRC = "versions/1.20.1/src/main/java/com/sdzjz/retro/RetroBlocks.java"

# m486 退役记录：m473 立的三条「accepts / accepts↔chainWants 成对表」对表项已摘除。
# 理由——**它们是为仿写路线而设的补丁**：当年两代各写一份 accepts/chainWants，靠这把尺子
# 追平名单。m486 起路由脑判定层两代共用同一份代码（xplat node/RouteBrain），"名单漏抄"
# 这种失败模式**按构造不存在**了，尺子没有对象可量。行为改由 m481 的路由域跨代契约
# （RouteDomainAssertions 六条成对判定）压着——从"对表"升级成"对行为"。
# 这正是 m477 定路线时预言的「对表闸可以退役」，第一批兑现。
#
# m498 第二批退役：「拉料循环·吃供料边的逻辑节点五族（m475）」同理摘除——拉料循环已下沉
# 共用（xplat node/SupplyPuller），五族清单只此一份，"漏抄"按构造不存在。
# **规律**：每当一块业务真移植完成，为它设的对表项就同时失去对象——**对表闸的萎缩速度，
# 正好是真移植的进度条**。剩余对表项只剩「已建方块名单」这类真·两代各写的东西。

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
    {
        # m472：两代 NodeIdent 各手抄一份六族常量（Legacy=ModItems.X_NODE / Retro=Machines.X_NODE，
        # 常量名两侧同拼）——与 RetroBlocks 无关，方块豁免表对它是错的尺子，豁免关。
        "名称": "NodeTags 身份口六族（m472 绞杀者第五刀）",
        "蓝本文件": "src/main/java/com/sdzjz/node/LegacyNodeIdent.java",
        "蓝本前缀": "ModItems",
        "本世代文件": "versions/1.20.1/src/main/java/com/sdzjz/retro/RetroNodeIdent.java",
        "本世代前缀": "Machines",
        "整文件": True,
        "豁免": False,
    },
    # m473（⑤c2）三条成对项：accepts 与 chainWants 是同一张节点类型表的两面（m131b→m132-6 血案：
    # 只写 accepts 那面，"仓→过滤器→酿造塔"拉料恒不通拖了整整一刀才实锤）。三条一起看：
    # ①本世代 accepts 有没有漏抄蓝本的类型分支；②③本世代两面互查（双向都跑=集合等价，
    # 少哪面都会红）。抓取正则=NodeTags.isXxx 族判定调用，与方块无关故豁免关。
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
        def 名单(侧):  # m472：整文件/自定义正则两个可选项，缺省=旧行为（方法体+前缀.大写常量）
            src = 读(it[侧 + "文件"])
            体 = 剥注释(src) if it.get("整文件") else 取方法体(src, it[侧 + "方法"], it[侧 + "文件"])
            if it.get("正则"):
                return set(re.findall(it["正则"], 体))
            return 抓名单(体, it[侧 + "前缀"])
        蓝本 = 名单("蓝本")
        本代 = 名单("本世代")
        if not 蓝本:
            print("❌ %s：蓝本名单抓到 0 条，先怀疑正则不是先怀疑代码" % it["名称"])
            坏 += 1
            continue
        免表 = 已建 if it.get("豁免", True) else 蓝本  # 豁免关=蓝本全额都要对上
        应有 = 蓝本 & 免表
        缺 = 应有 - 本代
        豁免 = 蓝本 - 免表
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
