#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m477 双写普查闸（第 18 闸，真移植路线的看门人）。

**它管什么**：C2 这条 1.20.1 线一直是**仿写**——同一个东西两边各写一份，靠对表闸追平名单。
作者 m477 拍板改走**真移植**：业务代码一份两代共用，世代差收进世代口（Env/Hooks/ItemData/
NodeTags.Ident/StorageAccess/RecipeAccess/CanvasGraphState.StackCodec 已验证七次）。

这把闸做两件事：
1. **登记账**：把还没合一的双写件逐个登记，附「对位的共用件」与「合一刀号」。合一一个划掉一个，
   账面上永远看得见还欠多少——**不许默默增加**。
2. **拦新增**：retro 侧出现**未登记**的 `Xxx120` 类，或未登记地引用被判定已合一的类，一律红。
   防的是「随手再写一份仿品」——m477 之前那种做法。

**判据**：retro 侧所有 `*120.java` 与 `Retro*.java` 必须在下表登记；标 `已合一=True` 的必须
真的不存在了（文件删干净）；标 `世代壳=True` 的是**该各写一份**的（方块/BE/屏/网络包/菜单——
MC API 密集，两代形状本就不同），不算欠账。

**加新文件**：往 登记表 加一行，写清它是世代壳还是待合一的双写件。
"""
import re
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RETRO = "versions/1.20.1/src/main/java/com/sdzjz/retro"

# 文件名 → (世代壳?, 已合一?, 说明/对位共用件)
登记表 = {
    # ===== 世代壳：MC API 密集，两代形状本就不同，该各写一份（不算欠账）=====
    "RetroBlocks.java": (True, False, "方块与 BE 类型注册——注册 API 两代形状不同"),
    "RetroMachineItems.java": (True, False, "机器物品批量注册（反射 Machines 唯一数据源，零名单）"),
    "RetroBootstrap.java": (True, False, "加载器入口：装配各世代口"),
    "RetroClientBootstrap.java": (True, False, "客户端入口"),
    "Net120.java": (True, False, "网络通道注册（1.20.1 无 CustomPacketPayload）"),
    "ClientNet120.java": (True, False, "客户端收包"),
    "CanvasPayloads120.java": (True, False, "画布包体编解码"),
    "StoragePayloads120.java": (True, False, "存储包体编解码"),
    "PanelPayloads120.java": (True, False, "面板包体编解码"),
    "NodePayloads120.java": (True, False, "节点包体编解码"),
    "TagItemData.java": (True, False, "ItemData 五口的 1.20.1 实现（m451）"),
    "TagStackKey.java": (True, False, "栈键的 1.20.1 实现"),
    "RetroNodeIdent.java": (True, False, "NodeTags.Ident 的 1.20.1 实现（m472）"),
    "RetroStackCodec.java": (True, False, "CanvasGraphState.StackCodec 的 1.20.1 实现（m477）"),
    "RetroXfer.java": (True, False, "m502：Xfer 五口的 1.20.1 实现（FabricXfer 原文照搬）——"
                         "与主线 loader/FabricXfer 对位的世代口血肉，本来就该各世代一份，非双写"),
    "RetroStackKind.java": (True, False, "StackKey.Kind 的 1.20.1 实现（m478）"),
    "RetroSkinGfx.java": (True, False, "SciSkin.Gfx 的 1.20.1 实现（m483，纹理 id 构造+顶点写法两处）"),
    "RetroRecipeAccess.java": (True, False, "RecipeAccess 的 1.20.1 实现（m494 熔炼域实装，其余三域随 ⑤d1）"),
    "CableEnd120.java": (True, False, "数据线端点枚举（方块状态属性，注册期类型）"),
    "DataCableRenderer120.java": (True, False, "BER 渲染（渲染 API 两代形状不同）"),
    "StorageCoreRenderer120.java": (True, False, "BER 渲染"),
    "StructureCoreMenu120.java": (True, False, "菜单/容器（Menu API 两代形状不同）"),
    "CanvasScreen120.java": (True, False, "画布屏：输入处理与布局壳。m483 卡面工艺、m484 整张节点卡"
                                         "（骨架+六族读数行）已走共用件；仍待搬：连线缎带(m136)+归并徽章、"
                                         "小地图、悬停详情浮层、面板屏工艺"),
    "DataPanelScreen120.java": (True, False, "面板屏：同上"),

    # ===== 待合一的双写件：业务逻辑两边各写一份，欠账 =====
    "StructureCore120.java": (True, False, "结构核心**世代壳**：m486/m495/m496/m498/m499 起"
                                          "路由脑·分发·均分·拉料·逻辑节点六分支全部走共用件；"
                                          "本文件只剩 BE 注册/存档签名/tick 编排骨架/宿主面/机器生产分支"),
    "StorageCore120.java": (False, False, "对位 block/StorageCoreBlockEntity 的账本核心"
                                          "（普通账/精确账/类型闸/修订号）——B 阶段"),
    "DataCable120.java": (True, False, "数据线 BE **世代壳**：m502 起抽取口业务全走共用件"
                         "（ExtractPort：逐模板抽/游标轮转/回收拍/六面视图/核心 40t 缓存）+ RetroXfer 五口内脏；"
                         "本文件只剩 BE 注册与 1.20.1 存档签名、tick 相位闸与 m469 端点自愈、m449 持物右键过滤器交互"),
    "DataCableBlock120.java": (False, False, "对位 xplat/block/DataCableBlock（endFor 名单已有对表闸看着）"),
    "DataPanel120.java": (True, False, "数据面板**世代壳**：m500/m501 起业务全走共用件"
                         "（PanelAggregator：聚合/精确并账/排序/过滤/开窗/取物回账），本文件只剩 BE 开屏工厂"
                         "（ExtendedScreenHandlerFactory 两代签名不同）、虚拟列表菜单 PanelMenu120（主线是 54 展示槽+合成网格，协议形状差）、"
                         "展示栈物化、申报量钳位与 payload 处理——都是本世代协议侧该各写一份的东西"),

    # ===== 判官：D 阶段照 RecipeDomainAssertions 的样子合一（断言一份，两代各喂自己的实现）=====
    "RetroTickTests.java": (False, False, "生产/路由判官——D 阶段合一"),
    "RetroStorageTests.java": (False, False, "存储判官——D 阶段合一"),
    "RetroCanvasTests.java": (False, False, "画布判官——D 阶段合一"),
    "RetroPanelTests.java": (False, False, "面板判官——D 阶段合一"),
    "RetroNetTests.java": (False, False, "网络判官——D 阶段合一"),

    # ===== 已合一：文件必须不存在 =====
    "CanvasGraphState120.java": (False, True, "m477 已合一 → xplat/node/CanvasGraphState（两代共用）"),
    "TagStackKey.java": (False, True, "m478 已合一 → xplat/storage/StackKey（两代共用，相等与哈希走 Kind 口）"),
}


判官文件 = {"RetroTickTests.java", "RetroStorageTests.java", "RetroCanvasTests.java",
            "RetroPanelTests.java", "RetroNetTests.java"}
主线判官 = "src/main/java/com/sdzjz/gametest/SdzjzGameTests.java"


def 用例名(路径):
    """抓 GameTest 用例名（public void xxx(GameTestHelper）。"""
    p = ROOT / 路径 if isinstance(路径, str) else 路径
    if not p.exists():
        return set()
    return set(re.findall(r"public void (\w+)\s*\(\s*GameTestHelper", p.read_text(encoding="utf-8")))


def 主():
    d = ROOT / RETRO
    if not d.exists():
        raise SystemExit("双写闸：找不到 retro 目录 " + RETRO)
    实存 = {p.name for p in d.glob("*.java")}
    坏 = 0

    未登记 = sorted(实存 - set(登记表))
    if 未登记:
        print("❌ 未登记的 retro 文件 %d 个：%s" % (len(未登记), "、".join(未登记)))
        print("   往 登记表 加一行，写清它是**世代壳**（该各写一份）还是**待合一的双写件**（欠账）。")
        坏 += 1
    else:
        print("✅ retro 侧 %d 个文件全部已登记" % len(实存))

    没删干净 = sorted(k for k, (_, 合, _d) in 登记表.items() if 合 and k in 实存)
    if 没删干净:
        print("❌ 登记为「已合一」但文件还在：%s" % "、".join(没删干净))
        print("   合一=仿写件删除、引用改共用件。留着就是两份代码继续漂移。")
        坏 += 1
    else:
        print("✅ 登记为已合一的仿写件都已删除")

    壳 = [k for k, (s, _c, _d) in 登记表.items() if s]
    合 = [k for k, (_s, c, _d) in 登记表.items() if c]
    欠 = sorted(k for k, (s, c, _d) in 登记表.items() if not s and not c)
    print("\n【真移植进度】世代壳 %d 个（该各写一份，不算欠账）｜已合一 %d 个｜**待合一 %d 个**"
          % (len(壳), len(合), len(欠)))
    if 欠:
        主线用例 = 用例名(主线判官)
        总行 = 0
        同名总数 = 0
        for k in 欠:
            p = d / k
            n = len(p.read_text(encoding="utf-8").split("\n")) if p.exists() else 0
            if k in 判官文件:
                # m482 口径修正：判官文件按**与主线同名的用例数**算双写，不按行数——
                # 行数会被"本世代独有覆盖"（cable_*/tick_* 等主线没有的用例）冲掉，收了双写反而看着涨，不诚实。
                同名 = sorted(用例名(p) & 主线用例)
                同名总数 += len(同名)
                print("   欠 %2d 条同名用例  %s —— %s%s"
                      % (len(同名), k, 登记表[k][2],
                         ("（" + "、".join(同名) + "）") if 同名 else "（本世代独有覆盖，非双写）"))
            else:
                总行 += n
                print("   欠 %4d 行  %s —— %s" % (n, k, 登记表[k][2]))
        print("   合计欠账 %d 行业务双写 + %d 条判官同名双写用例。" % (总行, 同名总数))

    if 坏:
        print("\n❌ 双写闸红：%d 条判据不过。" % 坏)
        return 1
    print("\n✅ 双写闸绿：登记 %d 个文件，账目清楚。" % len(登记表))
    return 0


if __name__ == "__main__":
    sys.exit(主())
