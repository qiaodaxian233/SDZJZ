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
    # m522b 血案：1.21 新增的**类**（不是方法）——CraftGridInventory 的 asCraftInput 覆写在作者 1.20.1 构建里"找不到符号 CraftingInput"
    r"\bCraftingInput\b": ("（1.20.1 无此类；CraftingContainer 也没有 asCraftInput）", "不覆写——1.21 接口 default 与 CraftingInput.of(w,h,items) 逐位同义"),
    r"\bPositionedCraftingInput\b": ("（1.20.1 无此类）", "同上"),
    r"\bRecipeHolder\b": ("直接 Recipe 实例（id 走 getId()）", "RecipeAccess 各域实现（m494/m520 样板）"),
    r"\bDataComponents\b": ("CompoundTag 键 / hoverName API", "ItemData 世代口（has/copyOf/write/clearCustomName）"),
    r"\bCustomData\b": ("getTag()/setTag()", "ItemData 世代口"),
    r"\bStreamCodec\b|\bRegistryFriendlyByteBuf\b|\bCustomPacketPayload\b": ("FriendlyByteBuf + FabricPacket", "包类各代各写（协议层是世代壳）"),
}

# m522b 第二件血案：白名单 xplat 文件 import/内联 FQN 引用了 1.20.1 源集里没有的自家类
# （SuperBenchScreenHandler 引 src 的 registry.ModScreenHandlers / registry.ModItems、非白名单的 item.CompressedPackItem）——
# 编译器在作者机上才报"程序包 com.sdzjz.registry 不存在"。判据：白名单 xplat 文件里出现的 com.sdzjz.a.b.C 必须能在
# 白名单 xplat + common 全层 + versions/1.20.1 源集里找到同名文件。
def 自家可见类(wl):
    ok = set()
    for rel in wl:
        ok.add(rel[:-5].replace("/", "."))
    for base in ("common/src/main/java", "versions/1.20.1/src/main/java"):
        for p in (ROOT / base).rglob("*.java"):
            ok.add(str(p.relative_to(ROOT / base))[:-5].replace("/", "."))
    return ok

def 自家引用(码):
    # import com.sdzjz.x.Y; 与 内联 com.sdzjz.x.Y（取到首个大写段为类名，忽略同包简单名——同包引用另有 dead_ref/编译兜底）
    refs = set()
    for m in re.finditer(r"\bcom\.sdzjz\.((?:[a-z_]\w*\.)*)([A-Z]\w*)", 码):
        refs.add("com.sdzjz." + m.group(1) + m.group(2))
    return refs


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
    可见 = 自家可见类(wl)
    for 名, p in 辖区:
        if not 名.startswith("xplat/"):
            continue  # common 层由 common_gate 管；此处只查 1.20.1 白名单里的 xplat 文件
        码 = 剥(p.read_text(encoding="utf-8"))
        for ref in sorted(自家引用(码)):
            if ref not in 可见:
                print("❌ %s 引用了 1.20.1 源集里没有的自家类 `%s`（不在白名单/common/versions/1.20.1）" % (名, ref))
                print("     该走：功能区本世代没有→带默认的宿主口（ExtractPort.Host 样板）；注册表类→静态安装口（ItemData.install 样板）")
                坏 += 1
    if 坏:
        print("\n❌ 世代 API 闸红：%d 处。共用层里留 1.21 专属调用，1.20.1 构建必挂（m487 血案）。" % 坏)
        return 1
    print("\n✅ 世代 API 闸绿：共用层没有 1.21 专属调用。")
    return 0


if __name__ == "__main__":
    sys.exit(主())
