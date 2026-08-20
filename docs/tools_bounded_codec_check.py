#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m291 回归尺：所有 C2S 注册的 payload，其 CODEC 里不得出现裸 PacketCodecs.STRING /
PacketCodecs.collection / .collect(PacketCodecs.toList())——必须走 Bounded.*（解码期有界）。
S2C 包不查（服务端可信）。新增 C2S 包忘挂界=退出码 1。自锚定路径。"""
import re, sys, os
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
main = open(os.path.join(ROOT, 'src/main/java/com/sdzjz/Sdzjz.java'), encoding='utf-8').read()
# m402 网络口收进 Net 漏斗后注册写法从 playC2S().register(...) 变成 Net.c2s(...)——尺子跟着改口径，
# 两种写法都认（老口径留着：将来别的世代/加载器源集可能还是原写法）。
c2s = re.findall(r'(?:playC2S\(\)\.register|(?:com\.sdzjz\.net\.)?Net\.c2s)\((?:com\.sdzjz\.net\.)?(\w+)\.ID', main)
bad = []
for name in c2s:
    p = os.path.join(ROOT, 'src/main/java/com/sdzjz/net/%s.java' % name)
    s = open(p, encoding='utf-8').read()
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)  # 剥块注释（含 javadoc）——注释提旧 API 不算罪
    s = re.sub(r'//[^\n]*', '', s)                 # 剥行注释
    if re.search(r'PacketCodecs\.STRING|PacketCodecs\.collection|collect\(PacketCodecs\.toList', s):
        bad.append(name)
if bad:
    print('有界Codec回归尺 ✗ C2S 包存在无界字符串/列表解码: %s' % bad)
    sys.exit(1)
print('有界Codec回归尺 ✓ %d 个 C2S 包全部有界(或纯定长)' % len(c2s))
