#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m282 拼音首字母回归尺（可挂 CI，命中退出码 1）。护三件事：
① 一级字库 23 段边界表逐值=经典表（防手滑改坏）；
② 二级字库硬表：两串等长、字符集=GB2312 二级全集(D8A1..F7FE 经 gbk 可解码)逐位同序、首字母全在 a-z；
③ 端到端：按 Java 同款算法（python 参照实现）对一批 MC 物品名断言首字母输出（含二级字"燧/鹦/鹉"实锤位）。
自锚定路径（m259 教训：不写死沙箱路径）。"""
import re, sys, os
import srcroots  # m406 源根解析（路径逻辑唯一出口）

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = srcroots.find('com/sdzjz/client/PinyinInitials.java')

CLASSIC_B1 = [0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1, 0xB9FE, 0xBBF7, 0xBFA6, 0xC0AC,
              0xC2E8, 0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA, 0xC8BB, 0xC8F6, 0xCBFA, 0xCDDA, 0xCEF4, 0xD1B9, 0xD4D1]
L1 = 'abcdefghjklmnopqrstwxyz'

def fail(msg):
    print('拼音首字母回归尺 ✗ ' + msg)
    sys.exit(1)

s = open(SRC, encoding='utf-8').read()

# ① 边界表
m = re.search(r'B1\s*=\s*\{(.*?)\}', s, re.S)
b1 = [int(x, 16) for x in re.findall(r'0x[0-9A-Fa-f]+', m.group(1))]
if b1 != CLASSIC_B1:
    fail('一级边界表与经典表不符: %s' % b1)

# ② 二级硬表
def grab(name):
    mm = re.search(name + r'\s*=\s*\n(.*?);', s, re.S)
    return ''.join(re.findall(r'"([^"]*)"', mm.group(1)))
t2c, t2i = grab('T2_CHARS'), grab('T2_INITS')
expect = []
for hi in range(0xD8, 0xF8):
    for lo in range(0xA1, 0xFF):
        try:
            expect.append(bytes([hi, lo]).decode('gbk'))
        except Exception:
            pass
if len(t2c) != len(t2i):
    fail('二级两串长度不等 %d/%d' % (len(t2c), len(t2i)))
if list(t2c) != expect:
    fail('二级字符集与 GB2312 二级全集不符（应 %d 字同码位序，实 %d）' % (len(expect), len(t2c)))
bad = [c for c in t2i if not ('a' <= c <= 'z')]
if bad:
    fail('二级首字母越界: %r' % bad[:5])
T2 = dict(zip(t2c, t2i))

# ③ 端到端（与 Java of() 同算法的 python 参照）
def init1(ch):
    try:
        b = ch.encode('gbk')
    except Exception:
        return ''
    if len(b) != 2:
        return ''
    v = (b[0] << 8) | b[1]
    if v < CLASSIC_B1[0] or v > 0xD7F9:
        return ''
    for i in range(len(CLASSIC_B1) - 1, -1, -1):
        if v >= CLASSIC_B1[i]:
            return L1[i]
    return ''

def of(name):
    out, prev = '', False
    for c in name:
        if ord(c) < 128:
            isl = ('a' <= c.lower() <= 'z')
            if isl and not prev:
                out += c.lower()
            prev = isl
            continue
        prev = False
        out += T2.get(c) or init1(c)
    return out

CASES = {'钻石': 'zs', '金锭': 'jd', '红石': 'hs', '绿宝石': 'lbs', '下界合金锭': 'xjhjd',
         '附魔之瓶': 'fmzp', '末影珍珠': 'myzz', '燧石': 'ss', '烈焰棒': 'lyb', '萤石粉': 'ysf',
         '紫水晶碎片': 'zsjsp', '鹦鹉螺壳': 'ywlk', '海晶灯': 'hjd', 'Iron Ingot': 'ii', 'TNT': 't'}
bad = [(k, of(k), v) for k, v in CASES.items() if of(k) != v]
if bad:
    fail('端到端不符: %s' % bad)

print('拼音首字母回归尺 ✓ 边界表/二级硬表(%d字)/端到端%d例 全过' % (len(t2c), len(CASES)))
