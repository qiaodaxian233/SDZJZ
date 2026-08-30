#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m491 冒烟报告筛选器：把 javac 无依赖冒烟的输出分成「缺 MC jar 噪音」与「必须看的真错」。

**为什么要有它**：我做纯语法冒烟时一直手写 grep，筛两类——语法错
（expected/illegal/unclosed…）与「自家新符号 cannot find symbol」。这个筛法漏了整整一类：
**类型错误**。m491 就是这么漏的：`int mcx = (cond ? dragCx : nodeCx(m))`（double 三元赋给 int），
javac **明明报了** `incompatible types: possible lossy conversion`，就躺在 1515 条
`cannot find symbol` 噪音里，我的 grep 名单里没有它，于是一路绿到作者的 Gradle 才炸。

**判据**：一条错误是噪音，当且仅当它是 `cannot find symbol` / `package ... does not exist` /
`does not override`（缺 MC 基类导致），**且**病灶行涉及 MC/Fabric/JOML 的类型。
其余一律进「必须看」清单——包括类型错、重复定义、非法引用、递归构造、record 问题等等。
**宁可多报也不漏**：噪音清单是白名单式的，新出现的错误类别默认落进「必须看」。

用法：
    javac ... 2> /tmp/err.txt && python3 docs/tools_smoke_filter.py /tmp/err.txt
退出码 1 = 有必须看的真错。
"""
import pathlib
import re
import sys

# 只有这三类可能是缺 MC jar 的噪音（其余一律要看）
噪音类 = (
    "cannot find symbol",
    "package ",
    "does not override or implement a method from a supertype",
    "找不到符号",
    "程序包",
)

# 病灶里出现这些前缀 = 确实是外部依赖缺失
外部前缀 = ("net.minecraft", "net.fabricmc", "com.mojang", "org.joml", "org.slf4j",
            "io.netty", "it.unimi", "com.google", "org.jetbrains", "minecraft", "fabricmc")


def 是外部(块):
    return any(p in 块 for p in 外部前缀)


# MC/Fabric 常见类型名（symbol: 行只写简名时用）——不全也没关系，兜不住的会落进"必须看"
外部简名 = re.compile(r"\b(?:class|变量|方法|类)?\s*(?:ItemStack|BlockPos|BlockState|Block|BlockEntity|"
                      r"Level|ServerLevel|CompoundTag|ListTag|Tag|StringTag|Item|Items|Blocks|"
                      r"GuiGraphics|Font|Screen|AbstractContainerScreen|AbstractContainerMenu|Slot|"
                      r"ResourceLocation|BuiltInRegistries|Registry|Direction|ChunkPos|Component|"
                      r"MinecraftServer|ServerPlayer|Player|Inventory|Container|MenuType|"
                      r"BlockEntityType|VertexConsumer|Matrix4f|RenderType|Minecraft|"
                      r"FabricGameTest|GameTest|GameTestHelper|ItemVariant|Transaction|Storage|"
                      r"EditBox|MutableComponent|FriendlyByteBuf|CustomPacketPayload|PoseStack|"
                      r"MultiBufferSource|BlockEntityRenderer|Logger|LoggerFactory)\b")


def 外部符号(块):
    return bool(外部简名.search(块))


def 主(路径):
    txt = pathlib.Path(路径).read_text(encoding="utf-8", errors="replace")
    # 按 "文件:行: error/错误:" 切块（块内含 symbol:/location: 明细行——判外部就靠它们）
    块 = re.split(r"\n(?=[A-Za-z0-9_./\\:-]+\.java:\d+: (?:error|错误))", txt)
    噪音 = 0
    真错 = []
    for b in 块:
        m = re.search(r"(?:error|错误): *(.+)", b)
        if not m:
            continue
        头 = m.group(1).strip()
        # 判据（m491 定稿，简单可靠优先）：缺依赖那三类一律记噪音，**其余一律要看**。
        # 「自家符号找不到」这一类不靠本工具兜——它有专门的第 19 闸（悬空引用）在管，
        # 在这里混判只会把底噪抬到没人看的高度（试过按 symbol: 行判外部，底噪仍 476 条）。
        if any(k in 头 for k in 噪音类):
            噪音 += 1
            continue
        # cannot find symbol 但符号是自家的 → 真错（下沉后悬空引用就是这形状）
        真错.append((b.split("\n")[0].strip(), 头))
    # 已知底噪：缺 MC jar 时 record 的紧凑构造器/方法引用解析不了参数类型，会报成这几种。
    # 它们**不随本仓改动增减**，登记在此以免每次都要人工分辨（新增的一律进"必须看"）。
    底噪形状 = ("invalid canonical constructor in record", "recursive constructor invocation",
                "invalid method reference",
                "static import only from classes and interfaces")  # 缺 MC jar 时 Commands 类解析不了
    真错 = [(位, 头) for 位, 头 in 真错 if not any(k in 头 for k in 底噪形状)]

    print("冒烟报告：噪音（缺 MC jar）%d 条（另含已登记底噪若干）" % 噪音)
    if not 真错:
        print("✅ 没有必须看的真错")
        return 0
    print("❌ 必须看的错误 %d 条：" % len(真错))
    seen = set()
    for 位, 头 in 真错[:40]:
        k = (位, 头)
        if k in seen:
            continue
        seen.add(k)
        print("   %s\n       → %s" % (位, 头))
    if len(真错) > 40:
        print("   …… 还有 %d 条" % (len(真错) - 40))
    return 1


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("用法：tools_smoke_filter.py <javac 错误日志>")
    sys.exit(主(sys.argv[1]))
