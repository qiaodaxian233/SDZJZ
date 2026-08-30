#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m490 世代 API 闸（第 20 闸）：**共用层里不许出现 1.21 专属 API**。

**它防的是 m487 那次构建失败**：把主线账本整段搬进 `xplat/storage/StorageLedger` 时，
`stack.getComponentsPatch().isEmpty()` 跟着一起进来了——那是组件世代专属，1.20.1 没有。
主线编译得过、我的纯语法冒烟（缺 MC jar）也看不出来，直到作者的 1.20.1 Gradle 才炸。

**辖区**：`xplat` 里**已挂进 1.20.1 白名单**的文件（白名单从 versions/1.20.1/build.gradle 现读，
不手抄——m469 手抄名单血案的教训），外加 `common`（那层本来就零 MC 依赖）。
没挂白名单的 xplat 文件是主线专属，不在辖区。

**判据**：辖区文件里出现下表任一 1.21 专属符号即红，并给出 1.20.1 对位与该走的世代口。
名单出处=docs/世代API对照表.md 第二节，每条都踩过或核过。

**加新条目**：往 专属 加一行，同时更新对照表。
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# 1.21 专属符号 → (1.20.1 对位, 该走的世代口)
专属 = {
    r"\.getComponentsPatch\(\)": ("stack.hasTag()", "ItemData.has(s) / StackKey.Kind.dataHash"),
    r"\bisSameItemSameComponents\b": ("isSameItemSameTags", "StackKey.Kind.same"),
    r"\bItemStack\.parse\(": ("ItemStack.of(tag)", "CanvasGraphState.StackCodec.load"),
    r"\bResourceLocation\.parse\(": ("new ResourceLocation(s)", "SciSkin.Gfx.id"),
    r"\bResourceLocation\.fromNamespaceAndPath\(": ("new ResourceLocation(ns,p)", "SciSkin.Gfx.tex"),
    r"\.addVertex\(": ("vertex(...).color(...).endVertex()", "SciSkin.Gfx.quad / quadVC"),
    r"\bHolderLookup\.Provider\b": ("（本世代无此类型）", "不透明代际句柄 Object 透传"),
    r"\.lookupOrThrow\(": ("BuiltInRegistries.XXX.get(rl)", "RecipeAccess 各域实现"),
    r"\bPotionContents\b": ("PotionUtils", "BrewAccess 实现"),
    r"\.potionBrewing\(\)": ("BrewingRecipeRegistry（静态）", "BrewAccess 实现"),
    r"\bgetAnvilCost\(\)": ("（无直接对位，同期是 Rarity.getWeight）", "EnchAccess + 作者拍板 A/B"),
}


def 白名单():
    """从 1.20.1 build.gradle 现读挂载清单（不手抄）。"""
    g = (ROOT / "versions/1.20.1/build.gradle").read_text(encoding="utf-8")
    return set(re.findall(r"include\s+'([^']+\.java)'", g))


def 剥(src):
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', src)


def 主():
    wl = 白名单()
    if not wl:
        raise SystemExit("世代 API 闸：白名单一条都没抓到——先怀疑正则不是先怀疑代码")
    辖区 = []
    for rel in sorted(wl):
        p = ROOT / "xplat/src/main/java" / rel
        if p.exists():
            辖区.append((f"xplat/{rel}", p))
    for p in sorted((ROOT / "common/src/main/java").rglob("*.java")):
        辖区.append((str(p.relative_to(ROOT)), p))
    print("辖区 %d 个文件（1.20.1 白名单 %d 条 + common 全层）" % (len(辖区), len(wl)))

    坏 = 0
    for 名, p in 辖区:
        码 = 剥(p.read_text(encoding="utf-8"))
        for pat, (对位, 口) in 专属.items():
            if re.search(pat, 码):
                行 = 码[:re.search(pat, 码).start()].count("\n") + 1
                print("❌ %s:%d 出现 1.21 专属 API `%s`" % (名, 行, pat.replace("\\", "")))
                print("     1.20.1 对位：%s ｜ 该走：%s" % (对位, 口))
                坏 += 1
    if 坏:
        print("\n❌ 世代 API 闸红：%d 处。共用层里留 1.21 专属调用，1.20.1 构建必挂（m487 血案）。" % 坏)
        return 1
    print("\n✅ 世代 API 闸绿：共用层没有 1.21 专属调用。")
    return 0


if __name__ == "__main__":
    sys.exit(主())
