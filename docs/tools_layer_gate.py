#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m406 分层硬闸（CI 第十三道）——多源集分层的守门人。

规矩两条：
  ① **xplat 零加载器引用**：`xplat/` 下不许出现 `net.fabricmc` / `FabricLoader` / `ModInitializer`
     等任何加载器符号（含注释外的字面）。破了即红——xplat 的全部意义就是"见 MC、不见加载器"。
  ② **待接口化清单（报告不红）**：xplat 里引用加载器层漏斗类（Net/ClientNet/Xfer/Hooks/Env/ClientHooks）
     的文件逐个点名。这些是 m402~m405 收口留下的"静态漏斗"（m433 起逐名接口化销账），将来要改成
     "xplat 定接口 + 各加载器源集给实现 + Platform 定位器取"，改一个销一个。

用法：python3 docs/tools_layer_gate.py   退出码 1=①被破。
"""
import os
import re
import sys

import srcroots

XPLAT = 'xplat/src/main/java'
LOADER_SYMBOLS = re.compile(r'net\.fabricmc|FabricLoader|\bModInitializer\b|\bClientModInitializer\b'
                            r'|net\.minecraftforge|net\.neoforged|\bFMLJavaModLoadingContext\b|\bIEventBus\b')  # m531（F1a）：三加载器都不许进 xplat

# m531（F1a）② **src/ 的 Fabric 胶水白名单**：F 线要让 `src/`（1.21.1 世代业务层）能被 NeoForge/Forge 模块整挂，
# 前提是 Fabric 符号只出现在下面这些"加载器胶水"文件里。白名单外的 src 文件出现 Fabric 符号 → 红（新增胶水先登记再写）。
# 「待拆」组是 F1b/F1c 的欠账（业务文件里嵌着 Fabric 接口），每拆一个删一行——数字在报告里可见。
SRC = 'src/main/java'
SRC_GLUE_OK = {  # 天生加载器层：入口 / 口的 Fabric 实现 / Fabric 专属注册壳
    'com/sdzjz/loader/FabricEntry.java', 'com/sdzjz/loader/FabricEnv.java', 'com/sdzjz/loader/FabricHooks.java',
    'com/sdzjz/loader/FabricNet.java', 'com/sdzjz/loader/FabricXfer.java', 'com/sdzjz/loader/FabricMenus.java',  # m532 菜单数据口
    'com/sdzjz/client/FabricClientEntry.java', 'com/sdzjz/client/FabricClientHooks.java', 'com/sdzjz/client/FabricClientNet.java',
    'com/sdzjz/client/CompressedPackRenderer.java',  # DynamicItemRenderer 壳（m529）
}
SRC_GLUE_PENDING = {  # 待拆：业务文件里嵌 Fabric 接口（F1c 存储传输适配器 / F1d 模型插件·判官）；F1b 六件 m532 已拆
    'com/sdzjz/block/StorageCoreBlockEntity.java': 'F1c Storage<ItemVariant> 提供侧',
    'com/sdzjz/client/SatelliteNodeModel.java': 'F1d ModelLoadingPlugin',
    'com/sdzjz/gametest/SdzjzGameTests.java': 'F1d FabricGameTest + 传输断言',
}
# m433：Net/ClientNet 已接口化销账（门面迁 xplat+Fabric 给 Impl+入口首行安装），从清单摘除。
# m435：六漏斗全员接口化销账收官（m433 Net/ClientNet、m434 Xfer、m435 Env/Hooks/ClientHooks）。
# 清单留空但机制保留：将来再立静态漏斗就填回来（改一个销一个）。
FUNNELS = []


def strip_comments(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    return re.sub(r'//[^\n]*', '', src)


def main():
    root = srcroots.repo_root()
    base = os.path.join(root, *XPLAT.split('/'))
    if not os.path.isdir(base):
        print('分层硬闸 ✗ 找不到 %s' % XPLAT)
        return 1
    bad, pending, total = [], [], 0
    for dp, _dirs, fs in os.walk(base):
        for f in fs:
            if not f.endswith('.java'):
                continue
            total += 1
            p = os.path.join(dp, f)
            body = strip_comments(open(p, encoding='utf-8').read())
            rel = os.path.relpath(p, base)
            if LOADER_SYMBOLS.search(body):
                bad.append(rel)
            hits = [x.rsplit('.', 1)[-1] for x in FUNNELS if x in body]
            if hits:
                pending.append((rel, hits))
    if bad:
        print('分层硬闸 ✗ xplat 里出现加载器符号（%d 个文件）：' % len(bad))
        for r in bad[:20]:
            print('    %s' % r)
        return 1
    print('分层硬闸 ✓ xplat %d 文件零加载器符号' % total)
    # m531 ② src/ Fabric 胶水白名单
    sbase = os.path.join(root, *SRC.split('/'))
    stray, pend_hit, ok_hit = [], [], 0
    for dp, _dirs, fs in os.walk(sbase):
        for f in fs:
            if not f.endswith('.java'):
                continue
            p = os.path.join(dp, f)
            body = strip_comments(open(p, encoding='utf-8').read())
            if not re.search(r'net\.fabricmc|FabricLoader|\bModInitializer\b|\bClientModInitializer\b', body):
                continue
            rel = os.path.relpath(p, sbase).replace(os.sep, '/')
            if rel in SRC_GLUE_OK:
                ok_hit += 1
            elif rel in SRC_GLUE_PENDING:
                pend_hit.append((rel, SRC_GLUE_PENDING[rel]))
            else:
                stray.append(rel)
    stale = [r for r in SRC_GLUE_PENDING if not re.search(r'net\.fabricmc', strip_comments(open(os.path.join(sbase, r), encoding='utf-8').read())) if os.path.exists(os.path.join(sbase, r))] if True else []
    if stray:
        print('分层硬闸 ✗ src/ 里白名单外的文件出现 Fabric 符号（%d）——新胶水先登记 SRC_GLUE_OK，业务文件别直摸加载器：' % len(stray))
        for r in stray:
            print('    %s' % r)
        return 1
    if stale:
        print('分层硬闸 ✗ SRC_GLUE_PENDING 过期（已无 Fabric 符号，删行）：%s' % stale)
        return 1
    print('    src/ Fabric 胶水：白名单 %d 文件 ✓；待拆欠账 %d 文件（F1b/F1c/F1d）：' % (ok_hit, len(pend_hit)))
    for r, why in pend_hit:
        print('        %-50s %s' % (r, why))
    print('    待接口化（引用加载器层漏斗类，报告不红）：%d 文件' % len(pending))
    # m437 记分牌：ItemData 收口进度（P-A 刀，报告不红）——直摸组件 API 的残余触点，改一处销一处
    import glob
    left = []
    for r in ['src/main/java', 'xplat/src/main/java']:
        for p in glob.glob(os.path.join(root, r, '**', '*.java'), recursive=True):
            if p.endswith('ComponentItemData.java') or '/gametest/' in p.replace('\\', '/'):
                continue  # m438：gametest 原生 poke 测组件身份本体，归加载器侧（m404 同理），不计残余
            n = len(re.findall(r'DataComponents\.CUSTOM_DATA', strip_comments(open(p, encoding='utf-8').read())))
            if n:
                left.append((os.path.basename(p), n))
    print('    ItemData 收口残余（直摸 CUSTOM_DATA，报告不红）：%d 触点 / %d 文件' % (sum(n for _, n in left), len(left)))
    for f, n in sorted(left, key=lambda x: -x[1])[:8]:
        print('      %-40s %d' % (f, n))

    for r, hits in sorted(pending)[:20]:
        print('      %-52s %s' % (r, '/'.join(hits)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
