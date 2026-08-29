#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m474 两代节点栈 NBT 键位登记表闸（第 17 闸）。

**它管什么**：节点栈（ItemStack）的 NBT 键是**两代共享的命名空间**——xplat 的 NodeTags 是
一代人攒下来的键表（is 六族/传感/抽取/垃圾桶/作物/分组…几十个两字母键），1.20.1 世代的
retro 侧屏幕/菜单/判官也往同一批栈上写键。没有闸的时候，两边各写各的，撞了**不报错、
判官照绿、游戏也照跑**——只有在某个键同时被两边读写时才会以最难查的形式炸出来。

**m474 血案**（本闸的由来）：1.20.1 画布把节点坐标写在 `xc`/`yc`，而 `xc` 在 NodeTags 里是
m159 **抽取节点累计抽取量**（long）。后果①抽取节点卡面「已抽取」读的是自己的画布 X 坐标；
后果②⑤c3 抽取泵一写累计就把节点弹飞到 x=已抽取件数的位置。另外蓝本坐标键叫 `nx`/`ny`，
键不同名还破 m443 DFU 红利（存档升 1.21.1 后坐标全丢回默认位）。三个坑，零报错。

**判据三条**：
1. 本世代 retro 源码里对**物品栈** NBT 用到的每个键，都必须在下方 登记表 里登记（新增键
   必须写一行说明它是什么、谁读谁写）——挡住"随手起个键名"。
2. 登记表里标 `共用=True` 的键，必须在 NodeTags 里真的存在（防本世代自造一个蓝本不认的键，
   却在登记表里声称两代共用）。
3. **核心判据**：本世代物品栈键 ∩ NodeTags 键 中，凡没在登记表标 `共用=True` 的，一律红
   ——这正是 `xc` 那种"同键异义"的形状。要么改名，要么登记成共用并写清同义。

**加新键**：往 登记表 加一行即可，不改逻辑。
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

NODETAGS = "xplat/src/main/java/com/sdzjz/node/NodeTags.java"
RETRO_DIR = "versions/1.20.1/src/main/java/com/sdzjz/retro"

# 键 → (共用?, 说明)。共用=True 表示两代同键同义（NodeTags 里也有，语义一致）。
登记表 = {
    "nx": (False, "m474 画布坐标 X（int）。蓝本 SCBE.nodeX 同键同义；NodeTags 不碰。"),
    "ny": (False, "m474 画布坐标 Y（int）。同上。"),
    "gp": (True, "m191 画布分组 id（int）。两代同键同义，NodeTags.nodeGroup 读写。"),
    "xc": (True, "m159 抽取节点累计抽取量（long，NodeTags.extractorCount）——两代同键同义。"
                 "m474 起本世代只以此义使用：自愈/洗净路径清旧档残值、判官测累计读写；"
                 "**画布坐标已改用 nx**（m474 血案原址：坐标占了这个键，卡面读数与坐标互相污染）。"),
    "yc": (False, "m474 旧档残留键（史前画布坐标 Y），同上只做清理。NodeTags 不认此键。"),
    "so": (True, "开关节点开合（boolean）。判官预置用，NodeTags.switchOn 读。"),
    "np": (True, "m110b 节点暂停（boolean）。判官预置用，NodeTags.nodePaused 读。"),
    "si": (True, "传感器监测物品 id（string）。判官预置用，NodeTags.sensorItem 读。"),
    "sv": (True, "传感器阈值（long）。判官预置用，NodeTags.sensorThreshold 读。"),
    "fl": (True, "过滤/机器白名单（ListTag of String）。判官预置用，NodeTags.filterPasses/"
                 "machineFilterAllows 读。"),
    "k": (False, "判官造「精确件」样品的区分键（int，RetroPanelTests/RetroStorageTests）——"
                 "存储域账本样品，不是节点栈语义键；NodeTags 不认此键。"),
}


def 读(rel):
    p = ROOT / rel
    if not p.exists():
        raise SystemExit("键位闸：找不到文件 " + rel)
    return p.read_text(encoding="utf-8")


def 剥注释(s):
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
    return re.sub(r"//[^\n]*", "", s)


def nodetags键():
    """NodeTags 里所有直接字面量键（viewOf/n.getXxx("k") 与 putXxx("k")）。"""
    src = 剥注释(读(NODETAGS))
    return set(re.findall(r"\.(?:get|put|contains|remove)[A-Za-z]*\(\s*\"([a-z][a-z0-9]{0,3})\"", src))


def 本世代物品栈键():
    """retro 侧对**物品栈** NBT 的键使用：getOrCreateTag()/getTag()/hasTag() 链上的字面量键。
    只认这条链——BE 存档子表（storEdges 里的 m/p/r/d 之类）不是节点栈，不在本闸辖区。"""
    出 = {}
    for p in sorted((ROOT / RETRO_DIR).glob("*.java")):
        src = 剥注释(p.read_text(encoding="utf-8"))
        for m in re.finditer(r"(?:getOrCreateTag|getTag|stackTag)\(\)?\s*\.\s*[A-Za-z]+\(\s*\"([a-z][a-z0-9]{0,3})\"", src):
            出.setdefault(m.group(1), set()).add(p.name)
    return 出


def 主():
    nt = nodetags键()
    if not nt:
        raise SystemExit("键位闸：NodeTags 里一个键都没抓到——正则该修了（m109 坏尺子家法：先怀疑尺子）")
    用键 = 本世代物品栈键()
    if not 用键:
        raise SystemExit("键位闸：retro 侧一个物品栈键都没抓到——同上，先怀疑尺子")
    print("NodeTags 键 %d 个；本世代物品栈用键 %d 个：%s"
          % (len(nt), len(用键), "、".join(sorted(用键))))
    坏 = 0

    未登记 = sorted(set(用键) - set(登记表))
    if 未登记:
        print("\n❌ 判据①未登记的键 %d 个：%s" % (len(未登记), "、".join(未登记)))
        for k in 未登记:
            print("     %s ← %s（往 登记表 加一行说明它是什么、谁读谁写）" % (k, "、".join(sorted(用键[k]))))
        坏 += 1
    else:
        print("\n✅ 判据①本世代用到的键全部已登记")

    假共用 = sorted(k for k, (共用, _) in 登记表.items() if 共用 and k not in nt)
    if 假共用:
        print("❌ 判据②登记为两代共用、但 NodeTags 里根本没有的键：%s" % "、".join(假共用))
        坏 += 1
    else:
        print("✅ 判据②登记的共用键在 NodeTags 里都真的存在")

    撞键 = sorted(k for k in 用键 if k in nt and not 登记表.get(k, (False, ""))[0])
    if 撞键:
        print("❌ 判据③**同键异义**（本世代物品栈在用 + NodeTags 也在用 + 没登记为共用）：")
        for k in 撞键:
            print("     %s ← %s；NodeTags 语义：%s" % (k, "、".join(sorted(用键[k])), 登记表.get(k, (False, "未登记"))[1]))
        print("     要么给本世代的用法改名，要么登记成 共用=True 并写清两代同义（m474 血案的形状）。")
        坏 += 1
    else:
        print("✅ 判据③没有同键异义（两代共享命名空间干净）")

    if 坏:
        print("\n❌ 键位闸红：%d 条判据不过。节点栈 NBT 是两代共享的命名空间，撞了不报错只会以最难查的形式炸（m474 教训）。" % 坏)
        return 1
    print("\n✅ 键位闸绿：登记 %d 个键，三条判据全过。" % len(登记表))
    return 0


if __name__ == "__main__":
    sys.exit(主())
