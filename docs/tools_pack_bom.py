#!/usr/bin/env python3
# mC 工程款全量过账工具（m247 落盘）：litematic 实测 json → bomPacked Java 片段。
# 归一化口径（m245 定档）：方块态→物品（红石线→红石、壁挂→手持…）、水=64 桶打水税、
# 岩浆=一桶一源全量、技术方块/生存不可获得/仪式产物剔除、实体不计料（笼子生物走 mobs 参数）。
# 取整（m247 修正 m236 策略）：二级仅溢价≤15% 用；否则一级向上取整；<64 散件原数。
# 断言与 Java 建器同式双算：64 整倍 / 4096 整倍 / 保守槽位 ≤144。
# 用法：python3 docs/tools_pack_bom.py "<litematic键名>" <sdzjz:机器id> [mobsCsv]
import json, math, sys, os

SKIP = {
    'minecraft:piston_head', 'minecraft:moving_piston',      # 技术方块
    'minecraft:player_head', 'minecraft:player_wall_head',   # 生存不可获得
    'minecraft:nether_portal', 'minecraft:end_portal',       # 仪式/场地产物
    'minecraft:end_portal_frame', 'minecraft:bedrock',       # 场地自带
    'minecraft:bubble_column',                                # 水柱=水的形态
    'minecraft:fire', 'minecraft:soul_fire',                  # 点火产物
    'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air',
    'minecraft:repeating_command_block', 'minecraft:command_block',
    'minecraft:chain_command_block', 'minecraft:structure_void', 'minecraft:jigsaw',
    'minecraft:tripwire',                                     # 放置态（线=拌线钩挂绳，绳另计）
}
RENAME = {
    'minecraft:redstone_wire': 'minecraft:redstone',
    'minecraft:tripwire_hook': 'minecraft:tripwire_hook',
    'minecraft:water_cauldron': 'minecraft:cauldron',
    'minecraft:lava_cauldron': 'minecraft:cauldron',
    'minecraft:carrots': 'minecraft:carrot', 'minecraft:potatoes': 'minecraft:potato',
    'minecraft:beetroots': 'minecraft:beetroot_seeds', 'minecraft:wheat': 'minecraft:wheat_seeds',
    'minecraft:cocoa': 'minecraft:cocoa_beans', 'minecraft:sweet_berry_bush': 'minecraft:sweet_berries',
    'minecraft:melon_stem': 'minecraft:melon_seeds', 'minecraft:pumpkin_stem': 'minecraft:pumpkin_seeds',
    'minecraft:attached_melon_stem': 'minecraft:melon_seeds', 'minecraft:attached_pumpkin_stem': 'minecraft:pumpkin_seeds',
    'minecraft:bamboo_sapling': 'minecraft:bamboo', 'minecraft:kelp_plant': 'minecraft:kelp',
    'minecraft:tall_seagrass': 'minecraft:seagrass', 'minecraft:big_dripleaf_stem': 'minecraft:big_dripleaf',
    'minecraft:powder_snow': 'minecraft:powder_snow_bucket',
}
def rename(k):
    if k in RENAME: return RENAME[k]
    for suf in ('_wall_torch', '_wall_sign', '_wall_hanging_sign', '_wall_banner', '_wall_fan', '_wall_head', '_wall_skull'):
        if k.endswith(suf):
            return k[:-len(suf)] + suf.replace('_wall', '')
    return k

def pack(n):
    if n < 64: return n, 'loose'
    p1 = math.ceil(n / 64); p2 = math.ceil(n / 4096)
    # m247 策略修正：去掉"一级超32格→全二级"强制（其前提=1格1件排包，已被包堆叠64/格淘汰，
    # 中等量级会造 46%~96% 虚溢价）；二级仅在向上取整溢价≤15% 时用，否则一级向上取整。
    if (p2 * 4096 / n - 1) <= 0.15: return p2 * 4096, f't2x{p2}'
    return p1 * 64, f't1x{p1}'

def main():
    key, machine = sys.argv[1], sys.argv[2]
    mobs = sys.argv[3] if len(sys.argv) > 3 else ''
    d = json.load(open(os.path.join(os.path.dirname(__file__), 'litematic_实测_2026-08.json'), encoding='utf-8'))
    e = d[key]
    m = {}
    for k, v in e['blocks'].items():
        if k in SKIP: continue
        if not k.startswith('minecraft:'):
            print(f'// !! 他模组方块剔除: {k}×{v}（只开原版料账）'); continue
        if k == 'minecraft:water':
            m['minecraft:water_bucket'] = m.get('minecraft:water_bucket', 0) + 64; continue
        if k == 'minecraft:lava':
            m['minecraft:lava_bucket'] = m.get('minecraft:lava_bucket', 0) + v; continue
        k2 = rename(k)
        m[k2] = m.get(k2, 0) + v
    ents = e.get('entities', {})
    if ents: print('// 实体（不计料，刷怪种走 mobs；载具类如 boat/minecart 需人工决定是否入料）:', ents)
    rows, slots, raw, bomtot = [], 0, 0, 0
    for k, v in sorted(m.items(), key=lambda x: -x[1]):
        n, how = pack(v)
        raw += v; bomtot += n
        slots += (n + 15) // 16 if how == 'loose' else (n // 64 + 63) // 64  # 槽位按一级最密口径保守计
        rows.append((k, v, n, how))
        print(f'// {k:44s} 实测{v:6d} → {n:6d} {how}')
    core_slots = (4 + 15) // 16 + (1 if mobs else 0)
    print(f'// 计料实测 {raw} BOM {bomtot} 溢价 {bomtot/raw-1:.1%} 保守槽位 {slots + core_slots}')
    assert all(n < 64 or n % 64 == 0 for _, _, n, _ in rows), '64整倍断言'
    assert slots + core_slots <= 144, '槽位断言'
    items = ',\n                '.join(f'"{k}", {n}' for k, _, n, _ in rows)
    print(f'        bomPacked("{machine}", "{mobs}",\n                {items});')

if __name__ == '__main__':
    main()
