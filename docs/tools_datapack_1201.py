#!/usr/bin/env python3
"""第 22 闸（m527）：1.20.1 `data/` 目录 = 主线 `data/` 的**机械转换**，不许手写、不许漂。

作者拍板（m527）「按 1.21.1 的来，做成一样」——配方/战利品表/标签在 1.20.1 一份都不缺，
但两代数据包格式有四处机械差（对照表「非 API 类世代差」有行）：
  ① 目录名：`recipe/`→`recipes/`、`loot_table/`→`loot_tables/`（1.20.5 起去复数）
  ② 配方结果键：`result.id`→`result.item`（1.20.5 起改 id）
  ③ 标签目录：`tags/block/`→`tags/blocks/`、`tags/item/`→`tags/items/`、`tags/entity_type/`→`tags/entity_types/`……（1.21 起去复数）
  ④ 引用过滤：结果件/材料/标签值里 **本世代没注册的 id 整条跳过**（1.20.1 加载到未知物品会报 "Failed to parse recipe"，
     战利品表/标签指向不存在的方块虽不炸但是脏数据）。本世代已注册 = Machines 反射的机器 + RetroBlocks `reg("…")` + RetroItems 注册句；
     原版 id 用「1.20.3+ 新增原版物品」拒绝表（与 RetroBenchTests.KNOWN_GAP_INGREDIENTS 同源，加条目两处一起加）。

用法：`python3 docs/tools_datapack_1201.py`          校验模式：重新生成到内存与磁盘逐文件比对，缺/多/异即红（CI 跑这个）
      `python3 docs/tools_datapack_1201.py --write`  生成模式：写盘（主线加了配方/注册了新件后跑一次，然后 commit）
      `python3 docs/tools_datapack_1201.py --list`   只列本次会生成/跳过哪些（不写盘不判红）
m109 家法：生成器自身是尺子——跳过清单必须打印出来，不许静默少文件。
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/resources/data"
DST = ROOT / "versions/1.20.1/src/main/resources/data"

# 与 RetroBenchTests.KNOWN_GAP_INGREDIENTS 的原版部分同源（1.20.3+ 才有的原版物品）
VANILLA_NOT_IN_1201 = {
    "minecraft:breeze_rod", "minecraft:copper_bulb", "minecraft:copper_grate", "minecraft:crafter", "minecraft:heavy_core",
    "minecraft:ominous_bottle", "minecraft:trial_key", "minecraft:trial_spawner", "minecraft:tuff_bricks", "minecraft:vault",
}
DIR_MAP = {"recipe": "recipes", "loot_table": "loot_tables", "advancement": "advancements", "structure": "structures",
           "tags/block": "tags/blocks", "tags/item": "tags/items", "tags/entity_type": "tags/entity_types",
           "tags/fluid": "tags/fluids", "tags/game_event": "tags/game_events"}


def registered_1201():
    ids = set()
    m = (ROOT / "common/src/main/java/com/sdzjz/machine/Machines.java").read_text(encoding="utf-8")
    # 机器 id：RetroMachineItems 反射的是 `public static final MachineDef X = new MachineDef("id"…)/def("id"…)/defMulti("id"…)/defConsume("id"…)`
    # ——四种建定口第一个字符串实参都是 id（m453 同源）；数量与 docs/tools_docs_sync.py 的机器数对表，少了当场红
    ids |= set(re.findall(r'public static final MachineDef\s+\w+\s*=\s*(?:new MachineDef|def\w*)\(\s*"([a-z0-9_]+)"', m))
    n_def = len(re.findall(r'public static final MachineDef\s+\w+\s*=', m))
    if len(ids) != n_def:
        print(f"❌ 生成器尺子坏：Machines 里 {n_def} 个 MachineDef 字段只解析出 {len(ids)} 个 id（建定口写法变了？）"); sys.exit(1)
    rb = (ROOT / "versions/1.20.1/src/main/java/com/sdzjz/retro/RetroBlocks.java").read_text(encoding="utf-8")
    ids |= set(re.findall(r'\breg\("([a-z0-9_]+)"', rb))
    ri = ROOT / "versions/1.20.1/src/main/java/com/sdzjz/retro/RetroItems.java"
    if ri.exists():
        t = ri.read_text(encoding="utf-8")
        # 注册句形：`plain("id")` / `Registry.register(…, id("id"), …)`——m528 自证：注册出的 id 数须等于 `public static Item` 声明的字段数
        got = set(re.findall(r'\b(?:plain|id)\("([a-z0-9_]+)"\)', t))
        fields = sum(len(re.findall(r'\b[A-Z][A-Z0-9_]*\b', decl)) for decl in re.findall(r'public static Item\s+([^;]+);', t))
        if len(got) != fields:
            print(f"❌ 生成器尺子坏：RetroItems 声明 {fields} 个 Item 字段只解析出 {len(got)} 条注册句（注册写法变了？改本正则）"); sys.exit(1)
        ids |= got
    return {"sdzjz:" + i for i in ids}


def item_ok(item_id, reg):
    if item_id.startswith("minecraft:"):
        return item_id not in VANILLA_NOT_IN_1201
    return item_id in reg


def ingredient_ids(obj):
    """配方 key/ingredients 里出现的 item id（tag 引用放行，不检查）。"""
    out = []
    if isinstance(obj, dict):
        if "item" in obj and isinstance(obj["item"], str):
            out.append(obj["item"])
        for v in obj.values():
            out += ingredient_ids(v)
    elif isinstance(obj, list):
        for v in obj:
            out += ingredient_ids(v)
    return out


def convert(reg):
    """返回 {目标相对路径: 文本}, [(源相对路径, 跳过原因)]"""
    out, skipped = {}, []
    for src in sorted(SRC.rglob("*.json")):
        rel = src.relative_to(SRC).as_posix()          # e.g. sdzjz/recipe/core_module.json
        ns, rest = rel.split("/", 1)
        d = json.loads(src.read_text(encoding="utf-8"))
        # 目录映射（最长前缀优先）
        mapped = rest
        for k in sorted(DIR_MAP, key=len, reverse=True):
            if rest.startswith(k + "/"):
                mapped = DIR_MAP[k] + rest[len(k):]
                break
        if rest.startswith("recipe/"):
            r = d.get("result")
            if isinstance(r, dict) and "id" in r:
                r = dict(r); r["item"] = r.pop("id")
                d["result"] = {k: r[k] for k in ("item", "count") if k in r} | {k: v for k, v in r.items() if k not in ("item", "count")}
            res_id = d["result"]["item"] if isinstance(d.get("result"), dict) else None
            bad = [i for i in ([res_id] if res_id else []) + ingredient_ids(d.get("key")) + ingredient_ids(d.get("ingredients")) if not item_ok(i, reg)]
            if bad:
                skipped.append((rel, "本世代无 " + ",".join(sorted(set(bad)))))
                continue
        elif rest.startswith("loot_table/"):
            names = re.findall(r'"name":\s*"((?:minecraft|sdzjz):[a-z0-9_/]+)"', json.dumps(d))
            bad = [n for n in names if not item_ok(n, reg)]
            if bad:
                skipped.append((rel, "本世代无 " + ",".join(sorted(set(bad)))))
                continue
        elif rest.startswith("tags/"):
            vals = d.get("values", [])
            keep = [v for v in vals if not isinstance(v, str) or item_ok(v, reg)]
            dropped = [v for v in vals if v not in keep]
            if not keep:
                skipped.append((rel, "标签值全为本世代无的 id"))
                continue
            if dropped:
                d["values"] = keep
        out[f"{ns}/{mapped}"] = json.dumps(d, ensure_ascii=False, separators=(", ", ": ")) + "\n"
    return out, skipped


def main():
    reg = registered_1201()
    gen, skipped = convert(reg)
    mode = sys.argv[1] if len(sys.argv) > 1 else "--check"
    print(f"主线 data {sum(1 for _ in SRC.rglob('*.json'))} 文件 → 1.20.1 生成 {len(gen)}，跳过 {len(skipped)}（本世代已注册 sdzjz id {len(reg)} 个）")
    for rel, why in skipped:
        print(f"   跳过 {rel}：{why}")
    if mode == "--list":
        for k in sorted(gen): print("   生成", k)
        return
    if mode == "--write":
        for old in list(DST.rglob("*.json")):
            old.unlink()
        for rel, text in gen.items():
            p = DST / rel; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text, encoding="utf-8")
        for dp in sorted([d for d in DST.rglob("*") if d.is_dir()], key=lambda x: -len(x.parts)):
            if not any(dp.iterdir()): dp.rmdir()
        print(f"✅ 已写盘 {len(gen)} 文件 → {DST.relative_to(ROOT)}")
        return
    # --check：逐文件比对
    bad = 0
    on_disk = {p.relative_to(DST).as_posix(): p.read_text(encoding="utf-8") for p in DST.rglob("*.json")} if DST.exists() else {}
    for rel, text in gen.items():
        if rel not in on_disk:
            print(f"❌ 缺文件 {rel}（主线有、本世代没生成——跑 --write）"); bad += 1
        elif on_disk[rel] != text:
            print(f"❌ 内容漂移 {rel}（手改了或主线变了——跑 --write，别手写）"); bad += 1
    for rel in on_disk:
        if rel not in gen:
            print(f"❌ 多余文件 {rel}（生成器不认——手写的？删掉或改生成器）"); bad += 1
    if bad:
        print(f"❌ 1.20.1 数据包闸红：{bad} 处。1.20.1 data/ 只能由本脚本 --write 生成。"); sys.exit(1)
    print(f"✅ 1.20.1 数据包闸绿：{len(gen)} 文件与主线机械转换逐字节一致")


if __name__ == "__main__":
    main()
