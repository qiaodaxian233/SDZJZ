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
# m488 并入括号配平普查：按行号切段搬代码时漏带闭括号已经犯了三次（m487 StorageCore120、
# m488 WireRenderer 各一次），它在纯语法冒烟里会报成一堆莫名其妙的 "illegal start of expression"，
# 而括号一数就知道。全库扫，零成本。
括号坏 = 0
for rel in sorted(set(list(目标) + [str(x.relative_to(ROOT)) for x in
        list((ROOT / "xplat/src/main/java/com/sdzjz").rglob("*.java"))
        + list((ROOT / "versions/1.20.1/src/main/java/com/sdzjz").rglob("*.java"))])):
    p = ROOT / rel
    if not p.exists():
        continue
    t = p.read_text(encoding="utf-8")
    t = re.sub(r"/\*.*?\*/", " ", t, flags=re.S)
    t = re.sub(r"//[^\n]*", " ", t)
    t = re.sub(r"'(?:\\.|[^'\\])'", "' '", t)
    t = re.sub(r'"(?:\\.|[^"\\])*"', '""', t)
    d = t.count("{") - t.count("}")
    if d:
        print(f"❌ {rel}: 花括号不配平（多 {d} 个开括号）——多半是按行号切段时漏带闭括号")
        括号坏 += 1
if not 括号坏:
    print("✅ 括号配平普查干净")
# m489 并入：**共用件引用的资源，本世代资源目录里有没有**。
# SciSkin.slotTex()/buttonTex() 会 blit textures/gui/{slot,button}.png——主线有，1.20.1 原来没有，
# 挂上共用件之后槽位会画成紫黑格。编译器管不着（是运行期资源），冒烟也管不着，只有开屏才看得见。
资源坏 = 0
共用要的资源 = ["assets/sdzjz/textures/gui/slot.png", "assets/sdzjz/textures/gui/button.png"]
资源根 = {"主线": ROOT / "src/main/resources", "1.20.1": ROOT / "versions/1.20.1/src/main/resources"}
for 代, 根 in 资源根.items():
    for r in 共用要的资源:
        if not (根 / r).exists():
            print(f"❌ {代} 缺共用件要用的资源：{r}（SciSkin 会 blit 它，缺了画成紫黑格）")
            资源坏 += 1
if not 资源坏:
    print("✅ 共用件资源普查干净")
# m497 并入：**用到了却没 import**。作者的 Gradle 报 `找不到符号: 变量 Items`——
# 我的筛选器把 cannot find symbol 一律归噪音（m491 判据，缺 MC jar 时噪音上千条），
# 而本闸原来只查「已删符号还有没有人用」，查不到「新写的代码引用了本文件没 import 的类型」。
# 判据：只认下面这张**常用 MC 类型白名单**（准，零误报）——用了 `Xxx.` 或 `new Xxx(` 却
# 既没 import、又不是全限定名、又不在同包，即红。
常用类 = ["Items", "Blocks", "ItemStack", "BlockPos", "BlockState", "Block", "Component",
          "CompoundTag", "ListTag", "StringTag", "Tag", "Direction", "ChunkPos",
          "BuiltInRegistries", "ResourceLocation", "GameTestHelper", "GuiGraphics",
          "Minecraft", "Level", "ServerLevel", "Slot", "EditBox", "Optional", "List", "Map"]
导入坏 = 0
for 根 in [ROOT / "versions/1.20.1/src/main/java/com/sdzjz", ROOT / "xplat/src/main/java/com/sdzjz"]:
    for p2 in sorted(根.rglob("*.java")):
        原 = p2.read_text(encoding="utf-8")
        码 = re.sub(r"/\*.*?\*/", " ", 原, flags=re.S)
        码 = re.sub(r"//[^\n]*", " ", 码)
        码 = re.sub(r'"(?:\\.|[^"\\])*"', '""', 码)
        导入 = set(re.findall(r"^import\s+(?:static\s+)?[\w.]*?(\w+);", 原, re.M))
        本包 = {q.stem for q in p2.parent.glob("*.java")}
        自定义 = set(re.findall(r"\b(?:class|interface|enum|record)\s+(\w+)", 码))
        for c in 常用类:
            if c in 导入 or c in 本包 or c in 自定义:
                continue
            # 用了 `C.` 或 `new C(`，且前面不是 `.`（排除全限定名 a.b.C.）
            if re.search(r"(?<![\w.])" + c + r"\s*(?:\.|\b\s*\w+\s*=)|new\s+" + c + r"\s*\(", 码):
                行 = 码[:re.search(r"(?<![\w.])" + c + r"\s*(?:\.|\b)", 码).start()].count("\n") + 1
                print(f"❌ {p2.relative_to(ROOT)}:{行} 用到 `{c}` 却没 import（Gradle 会报「找不到符号」）")
                导入坏 += 1
if not 导入坏:
    print("✅ import 完整性普查干净")
坏 += 括号坏 + 资源坏 + 导入坏
print("✅ 悬空引用普查干净" if not 坏 else f"\n❌ {坏} 个问题——Gradle 真编译会红")
sys.exit(1 if 坏 else 0)
