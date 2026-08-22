#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m291 回归尺：所有 C2S 注册的 payload，其 CODEC 里不得出现裸 PacketCodecs.STRING /
PacketCodecs.collection / .collect(PacketCodecs.toList())——必须走 Bounded.*（解码期有界）。
S2C 包不查（服务端可信）。新增 C2S 包忘挂界=退出码 1。自锚定路径。"""
import re, sys, os
import srcroots  # m406 源根解析（路径逻辑唯一出口）
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
main = srcroots.read('com/sdzjz/Sdzjz.java')
# m402 网络口收进 Net 漏斗后注册写法从 playC2S().register(...) 变成 Net.c2s(...)——尺子跟着改口径，
# 两种写法都认（老口径留着：将来别的世代/加载器源集可能还是原写法）。
c2s = re.findall(r'(?:playC2S\(\)\.register|(?:com\.sdzjz\.net\.)?Net\.c2s)\((?:com\.sdzjz\.net\.)?(\w+)\.ID', main)
bad = []
for name in c2s:
    p = srcroots.find('com/sdzjz/net/%s.java' % name, required=False)
    s = open(p, encoding='utf-8').read()
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)  # 剥块注释（含 javadoc）——注释提旧 API 不算罪
    s = re.sub(r'//[^\n]*', '', s)                 # 剥行注释
    # m414 曾并列 Yarn/Mojmap 两套同义名防迁移期尺子失明；m424 收窄回 Mojmap 单套——
    # 主线已换 Mojmap（m422），Yarn 名（PacketCodecs.*）若倒退回来由第 14 道闸
    # tools_yarn_residue_check.py 兜底拦截（黑名单含 PacketCodecs FQN+简名，字符串不豁免），
    # 本尺不再双列，两把尺子各管各的坏模式。
    if re.search(r'ByteBufCodecs\.STRING(_UTF8)?|ByteBufCodecs\.collection'
                 r'|collect\(ByteBufCodecs\.toList|apply\(ByteBufCodecs\.list', s):
        bad.append(name)
if bad:
    print('有界Codec回归尺 ✗ C2S 包存在无界字符串/列表解码: %s' % bad)
    sys.exit(1)
print('有界Codec回归尺 ✓ %d 个 C2S 包全部有界(或纯定长)' % len(c2s))
