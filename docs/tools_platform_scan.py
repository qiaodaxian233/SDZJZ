# -*- coding: utf-8 -*-
"""m361 Phase 0 平台耦合扫描（多版本代际架构·地雷图）——外部顾问方案 Phase 0 落地。

五类口径（顾问原文）：
  A = Common-safe（零 Minecraft/Fabric/Mojang 依赖，可直迁 common/）
  B = Legacy-coupled（服务端业务，触 MC API——迁 common 前需 Platform SPI 剥离，量化到 API 族）
  C = Modern-only（现阶段=空，26.x 适配落地后才有）
  D = Client-only（client 包或 import net.minecraft.client——留在版本侧/代际侧）
  E = Mixin（@Mixin 文件——按顾问§6 做代际隔离 legacy.mixins.json / modern.mixins.json）

用法：python3 docs/tools_platform_scan.py [--md docs/PLATFORM_MAP.md]
退出码恒 0（分析工具非闸）。B 类按 API 族计数，族=将来 Platform SPI 的接口清单。
"""
import os
import re
import sys
import srcroots  # m406 源根解析（路径逻辑唯一出口）
from collections import defaultdict

ROOTS = srcroots.ROOTS  # m369 双根 → m406 多源集统一走解析器

# API 族 → 正则（FQN 内联用法极多，必须扫全文而非只扫 import）
FAMILIES = {
    # m423 迁移后口径修正：mojmap 名为准（旧 Yarn 名全部命中归零 → 尺子曾静默失真，
    # nbt/component 只剩 1 用点、text/i18n 整族消失、network 4 用点，全是假数）。
    # 两边名都留着：Yarn 名保作**残留告警**（新代码若冒出 Yarn 名，会被 tools_yarn_residue_check 拦下，
    # 这里留名只为老分支/老存档文档回读时仍能量得出来）。
    "nbt/component":  r"CompoundTag|ListTag|IntArrayTag|StringTag|net\.minecraft\.nbt|CustomData|DataComponents"
                      r"|NbtCompound|NbtList|NbtElement|NbtComponent|DataComponentTypes",
    "item":           r"\bItemStack\b|net\.minecraft\.world\.item|BuiltInRegistries\.ITEM"
                      r"|net\.minecraft\.item|Registries\.ITEM",
    "recipe":         r"RecipeHolder|RecipeManager|getRecipeManager|RecipeType|CraftingRecipe|CraftingInput"
                      r"|RecipeEntry",
    "network":        r"CustomPacketPayload|PayloadTypeRegistry|ServerPlayNetworking|ClientPlayNetworking"
                      r"|FriendlyByteBuf|StreamCodec|CustomPayload|PacketByteBuf",
    "registry":       r"\bBuiltInRegistries\.|\bRegistries\.|\bResourceKey\b|ResourceLocation\.of"
                      r"|RegistryKeys|RegistryKey|Identifier\.of",
    "world/block":    r"\bServerLevel\b|\bLevel\b|BlockPos|BlockEntity|BlockState"
                      r"|\bServerWorld\b|\bWorld\b",
    "screen":         r"AbstractContainerMenu|\bSlot\b|ContainerData|\bMenuType\b"
                      r"|ScreenHandler|SyncedGuiData|PropertyDelegate",
    "gametest":       r"GameTest|GameTestHelper|TestContext",
    "text/i18n":      r"Component\.literal|Component\.translatable|Text\.literal|Text\.translatable",
    "client-render":  r"GuiGraphics|\bMinecraft\b|net\.minecraft\.client|DrawContext|MinecraftClient",
    "fabric-api":     r"net\.fabricmc\.fabric|FabricLoader",
}
MC_ANY = re.compile(r"net\.minecraft|net\.fabricmc|com\.mojang")


def classify(path, body):
    if "/mixin/" in path or "@Mixin(" in body:
        return "E"
    if "/client/" in path or "net.minecraft.client" in body:
        return "D"
    if not MC_ANY.search(body):
        return "A"
    return "B"


def scan():
    rows = []  # (cls, path, {family: hits}, total_hits, loc)
    for base in ROOTS:
     for root, _, files in os.walk(base):
        for fn in sorted(files):
            if not fn.endswith(".java"):
                continue
            p = os.path.join(root, fn).replace("\\", "/")
            body = open(p, encoding="utf-8").read()
            cls = classify(p, body)
            fam = {}
            for name, pat in FAMILIES.items():
                n = len(re.findall(pat, body))
                if n:
                    fam[name] = n
            rows.append((cls, p, fam, sum(fam.values()), body.count("\n") + 1))
    return rows


def report(rows, out):
    by = defaultdict(list)
    for r in rows:
        by[r[0]].append(r)
    w = out.write
    w("# SDZJZ 平台耦合地雷图（m361 Phase 0，工具自动生成：docs/tools_platform_scan.py）\n\n")
    w("双端锚点=1.21.1(Legacy) + 26.2(Modern)；本图指导 Phase 1 Common 剥离顺位。\n\n")
    w("## 总量\n\n")
    for c, label in [("A", "Common-safe 可直迁"), ("B", "Legacy-coupled 需 SPI 剥离"),
                     ("D", "Client-only"), ("E", "Mixin(代际隔离)")]:
        lst = by.get(c, [])
        w("- **%s %s**: %d 文件 / %d 行\n" % (c, label, len(lst), sum(x[4] for x in lst)))
    w("- C Modern-only: 0（26.x 适配落地后产生）\n\n")
    w("## B 类 API 族分布（=Platform SPI 接口清单的量化依据）\n\n")
    famf, famh = defaultdict(int), defaultdict(int)
    for _, _, fam, _, _ in by.get("B", []):
        for k, v in fam.items():
            famf[k] += 1
            famh[k] += v
    w("| API 族 | 文件数 | 用点数 | 对应 SPI |\n|---|---|---|---|\n")
    spi = {"nbt/component": "NbtAdapter/DataComponentAdapter", "item": "ItemPlatform/ItemView",
           "recipe": "RecipeAccess", "network": "NetPlatform", "registry": "IdResolver",
           "world/block": "WorldAdapter", "screen": "VaultScreenPlatform/ScreenAdapter",
           "gametest": "版本测试层", "text/i18n": "MsgPlatform", "client-render": "(D 类专属)",
           "fabric-api": "loader 层"}
    for k in sorted(famh, key=lambda x: -famh[x]):
        w("| %s | %d | %d | %s |\n" % (k, famf[k], famh[k], spi.get(k, "?")))
    w("\n## 耦合最重 TOP15（m368 耦合分排序：分=API 族数²×log(用点)——迁移难度看\"同时依赖几个 SPI 面\"而非用点绝对值，顾问⑥轮⑤）\n\n")
    import math
    def score(x):
        return len(x[2]) ** 2 * math.log(max(2, x[3]))
    for cls, p, fam, tot, loc in sorted(by.get("B", []), key=lambda x: -score(x))[:15]:
        w("- 耦合分 %.0f｜%s（%d 行 / %d 用点 / %d 个 SPI 面：%s）\n" % (score((cls, p, fam, tot, loc)),
              p.replace("common/src/main/java/com/sdzjz/", "common:").replace("src/main/java/com/sdzjz/", ""), loc, tot, len(fam),
              ", ".join("%s×%d" % kv for kv in sorted(fam.items(), key=lambda x: -x[1])[:5])))
    w("\n## A 类清单（Phase 1 第一批直迁 common/）\n\n")
    for _, p, _, _, loc in sorted(by.get("A", [])):
        w("- %s（%d 行）\n" % (p.replace("common/src/main/java/com/sdzjz/", "common:").replace("src/main/java/com/sdzjz/", ""), loc))
    w("\n## E 类 Mixin 清单（§6 代际隔离对象）\n\n")
    for _, p, fam, _, _ in sorted(by.get("E", [])):
        w("- %s（%s）\n" % (p.replace("common/src/main/java/com/sdzjz/", "common:").replace("src/main/java/com/sdzjz/", ""), ", ".join(fam) or "纯注入"))
    w("\n## D 类 Client 清单\n\n")
    for _, p, _, tot, loc in sorted(by.get("D", []), key=lambda x: -x[3]):
        w("- %s（%d 行 / %d 用点）\n" % (p.replace("common/src/main/java/com/sdzjz/", "common:").replace("src/main/java/com/sdzjz/", ""), loc, tot))


def main():
    rows = scan()
    md = None
    if "--md" in sys.argv:
        md = sys.argv[sys.argv.index("--md") + 1]
    if md:
        with open(md, "w", encoding="utf-8") as f:
            report(rows, f)
        print("✓ 地雷图已写 %s" % md)
    cnt = defaultdict(int)
    for r in rows:
        cnt[r[0]] += 1
    print("✓ 平台扫描：A=%d B=%d D=%d E=%d（共 %d 文件）"
          % (cnt["A"], cnt["B"], cnt["D"], cnt["E"], len(rows)))


if __name__ == "__main__":
    main()
