#!/usr/bin/env python3
# m401 加载器耦合面扫描尺（Forge/NeoForge 移植的成本表）——与 m361 的 tools_platform_scan.py 分工：
# 那把尺量的是「离 Minecraft 有多远」（A~E 类 + MC API 族），这把尺量的是「离 Fabric 有多远」，
# 即换加载器时必须重写/抽 SPI 的点。口径：只数 net.fabricmc.* 与 fabric 专属注解/入口，
# 逐族计数 + 逐文件排行 → 决定 SPI 补齐顺序（谁的用点最多，谁先抽口）。
# 报告落 docs/LOADER_MAP.md；非门控（报告尺，不挂 CI）。
import os
import re
import sys
import srcroots  # m406 源根解析（路径逻辑唯一出口）

ROOTS = srcroots.ROOTS + ['versions']  # m406 统一走解析器（versions 额外整目录扫，含 26.2 bootstrap）
# 族名 → 匹配片段（按 FQN 片段判，剥注释后再扫）
FAMILIES = [
    # m402 口径修正：CustomPayload 是**原版**类型（可移植，不该算加载器耦合），首版误收进来虚高了基数；
    # 这一族只数 Fabric 专属符号。
    ('networking 网络包', ['fabricmc.fabric.api.networking', 'fabricmc.fabric.api.client.networking',
                          'ServerPlayNetworking', 'ClientPlayNetworking', 'PayloadTypeRegistry']),
    ('registry 注册', ['fabricmc.fabric.api.itemgroup', 'FabricItemGroup', 'fabricmc.fabric.api.object.builder']),
    ('events 生命周期/事件', ['fabricmc.fabric.api.event', 'ServerTickEvents', 'ServerLifecycleEvents',
                          'UseBlockCallback', 'ClientTickEvents']),
    ('rendering 渲染', ['fabricmc.fabric.api.client.rendering', 'WorldRenderEvents', 'BuiltinItemRendererRegistry',
                       'HudRenderCallback', 'BlockEntityRendererFactories', 'BlockEntityRenderers']),
    ('keybinding 键位', ['KeyBindingHelper', 'fabricmc.fabric.api.client.keybinding']),
    ('transfer 传输API', ['fabricmc.fabric.api.transfer', 'ItemVariant', 'StorageUtil', 'ItemStorage']),
    ('screenhandler 屏', ['fabricmc.fabric.api.screenhandler', 'ExtendedScreenHandlerType']),
    ('gametest 测试', ['fabricmc.fabric.api.gametest', 'FabricGameTest']),
    ('loader 环境/入口', ['fabricmc.loader.api', 'FabricLoader', 'ModInitializer', 'ClientModInitializer',
                       'DedicatedServerModInitializer']),
    ('resource 资源/标签', ['fabricmc.fabric.api.resource', 'fabricmc.fabric.api.tag']),
]


def strip_comments(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    return re.sub(r'//[^\n]*', '', src)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    repo = os.path.dirname(here)
    os.chdir(repo)
    fam_hits = {name: 0 for name, _ in FAMILIES}
    fam_files = {name: set() for name, _ in FAMILIES}
    per_file = {}
    total_files = 0
    for root in ROOTS:
        if not os.path.isdir(root):
            continue
        for dirpath, _dirs, files in os.walk(root):
            for fn in files:
                if not fn.endswith('.java'):
                    continue
                p = os.path.join(dirpath, fn)
                src = strip_comments(open(p, encoding='utf-8').read())
                if 'fabric' not in src.lower():
                    continue
                total_files += 1
                n = 0
                for name, needles in FAMILIES:
                    c = sum(src.count(x) for x in needles)
                    if c:
                        fam_hits[name] += c
                        fam_files[name].add(p)
                        n += c
                if n:
                    per_file[p] = n
    rank = sorted(per_file.items(), key=lambda kv: -kv[1])
    lines = ['# 加载器耦合面地图（m401 自动生成，勿手改）', '',
             '> 口径：只数 `net.fabricmc.*` 与 Fabric 专属入口/注解的用点。换 Forge/NeoForge 时，',
             '> 这些点要么抽进 `platform/` SPI 由各加载器实现，要么在各加载器源集里各写一份。', '',
             f'扫描文件数（含 fabric 字样）：{total_files}；有耦合用点的文件：{len(per_file)}', '',
             '## 按 API 族（决定 SPI 补齐顺序）', '', '| 族 | 用点数 | 涉及文件数 |', '|---|---:|---:|']
    for name, _ in FAMILIES:
        lines.append(f'| {name} | {fam_hits[name]} | {len(fam_files[name])} |')
    lines += ['', '## 按文件排行（前 25，决定改写顺序）', '', '| 文件 | 用点数 |', '|---|---:|']
    for p, n in rank[:25]:
        lines.append(f'| `{p}` | {n} |')
    lines.append('')
    open('docs/LOADER_MAP.md', 'w', encoding='utf-8').write('\n'.join(lines))
    tot = sum(fam_hits.values())
    print(f'✓ 加载器耦合扫描：用点合计 {tot}，涉及文件 {len(per_file)}（报告已写 docs/LOADER_MAP.md）')
    for name, _ in FAMILIES:
        print(f'    {name}: {fam_hits[name]} 用点 / {len(fam_files[name])} 文件')
    return 0


if __name__ == '__main__':
    sys.exit(main())
