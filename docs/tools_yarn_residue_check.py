#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m423 Yarn 名残留闸——主线自 m422 换 Mojmap 口径后，防新代码倒退回 Yarn 名。

为什么要这把尺：m414~m421 七轮迁移全靠**编译器**当判官，因为 Yarn 名在 Mojmap 环境里编译不过。
但有三类地方编译器管不到，写错了要么静默失效、要么等到运行期才炸：
  ① mixin 的 method= 靶点字符串；② 反射/字符串形类名；③ 注释和文档里的"照抄示例"被后人当真。
外加一条现实：作者与 AI 的肌肉记忆都是 Yarn（本仓 400+ 里程碑都写在 Yarn 时代），
倒退是**必然**会发生的，不是会不会的问题。故立此闸，让倒退在 CI 就红，而不是在游戏里红。

口径：
  A. **FQN 段**：Yarn 全限定名，源码里出现即红。
  B. **简名段**：按词边界判，出现即红。
  两段都住在 docs/YARN_BLACKLIST.txt（可审可改），由 m423 机器生成自 docs/mapping_used.json：
  取 167 个 Yarn FQN 及其简名，**只留迁移后源码里 0 命中的**——
  两边同名的（FQN 如 net.minecraft.server.MinecraftServer / net.minecraft.nbt.NbtIo，
  简名如 ItemStack/BlockPos/Item 共 44 个）Mojmap 侧仍在用，自动落选，不会误伤。
  C. **注释豁免**：剥注释后再扫。DEVLOG 式的"当年 Yarn 叫 xxx"留痕有考据价值（m422 已定调），
     只要不进代码就不算倒退。字符串字面量**不豁免**——mixin 靶点就住在字符串里。

跑法：python3 docs/tools_yarn_residue_check.py     退出码 0=绿 1=红（CI 第 14 闸）
维护：Mojmap 侧若确需用到某个同名类（极少），把该名从 YARN_BLACKLIST.txt 删掉并在本注释记里程碑。
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import srcroots  # m406 源根解析（路径逻辑唯一出口）

ROOT = srcroots.repo_root()
USED = os.path.join(ROOT, 'docs', 'mapping_used.json')
BLACK = os.path.join(ROOT, 'docs', 'YARN_BLACKLIST.txt')


def strip_comments(src):
    """剥块注释与行注释；字符串字面量保留（mixin 靶点住在字符串里，必须扫得到）。"""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'^\s*//.*$', '', src, flags=re.M)
    return re.sub(r'(?<![:"\'/])//(?![/*]).*$', '', src, flags=re.M)


def load_blacklist():
    """读两段名单：[FQN] 全限定名段 / [SIMPLE] 简名段。"""
    fq, sn, cur = [], [], None
    if not os.path.isfile(BLACK):
        return fq, sn
    for ln in open(BLACK, encoding='utf-8'):
        ln = ln.split('#')[0].strip()
        if not ln:
            continue
        if ln == '[FQN]':
            cur = fq
            continue
        if ln == '[SIMPLE]':
            cur = sn
            continue
        if cur is not None:
            cur.append(ln)
    return fq, sn


def main():
    fqns, names = load_blacklist()
    if not names or not fqns:
        print('✗ 缺 docs/YARN_BLACKLIST.txt（或为空）——尺子会静默放行，按坏尺子处理')
        return 1

    name_re = re.compile(r'(?<![\w$.])(' + '|'.join(re.escape(n) for n in names) + r')(?![\w$])')
    hits = []
    n_files = 0
    for p in srcroots.java_files():
        n_files += 1
        body = strip_comments(open(p, encoding='utf-8', errors='replace').read())
        rel = os.path.relpath(p, ROOT)
        for fq in fqns:
            if fq in body:
                hits.append((rel, 'FQN', fq))
        for m in name_re.finditer(body):
            line = body.count('\n', 0, m.start()) + 1
            hits.append((rel, '简名 L%d' % line, m.group(1)))

    if hits:
        print('✗ Yarn 名残留 %d 处（主线口径=Mojmap，见 docs/MAPPING_MEMBERS.tsv 查名）：' % len(hits))
        for rel, kind, s in hits[:40]:
            print('    %-58s %-10s %s' % (rel, kind, s))
        if len(hits) > 40:
            print('    …… 另 %d 处' % (len(hits) - 40))
        return 1
    print('✓ Yarn 名残留闸：%d 个源文件 ×（%d 个 FQN + %d 个简名）全部零命中'
          % (n_files, len(fqns), len(names)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
