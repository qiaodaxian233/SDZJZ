#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""m288 回归尺：@Override 错挂检查（命中退出码 1，可挂 CI）。
冒烟盲区#4：沙箱 javac 缺 MC 依赖时超类不可解析，@Override 校验被整个跳过——m286 把新私有方法
插进了别人的 @Override 和方法体之间，沙箱全绿、作者 gradle 一编就红（"方法不会覆盖或实现超类型的方法"）。
本尺静态兜住能静态判死刑的错挂：@Override 之后（允许隔注释/其他注解）的首个声明若是
private 或 static 方法——这两类在 Java 里永远不可能是覆写，必错。教训同款：str_replace 锚必须
带上方法前的注解行，光锚签名行会把插入物楔进注解和方法之间。自锚定路径（m259 教训）。"""
import os, re, sys
import srcroots  # m406 源根解析（路径逻辑唯一出口）

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_ROOTS = [os.path.join(ROOT, *r.split('/')) for r in srcroots.ROOTS]  # m369 双根 → m406 多源集

bad = []
for dp, _, fs in (x for r in SRC_ROOTS for x in os.walk(r)):
    for f in fs:
        if not f.endswith('.java'):
            continue
        path = os.path.join(dp, f)
        lines = open(path, encoding='utf-8').read().split('\n')
        i = 0
        while i < len(lines):
            if re.match(r'\s*@Override\s*$', lines[i]):
                j = i + 1
                in_block = False
                while j < len(lines):
                    t = lines[j].strip()
                    if in_block:
                        if '*/' in t:
                            in_block = False
                        j += 1
                        continue
                    if t.startswith('/*'):
                        if '*/' not in t:
                            in_block = True
                        j += 1
                        continue
                    if t == '' or t.startswith('//') or t.startswith('@'):
                        j += 1
                        continue
                    break
                if j < len(lines):
                    decl = lines[j].strip()
                    eq = decl.find('='); par = decl.find('(')
                    if re.match(r'(private|static)\b', decl) and par >= 0 and (eq < 0 or par < eq):
                        bad.append('%s:%d: @Override 挂在了 %r' % (os.path.relpath(path, ROOT), j + 1, decl[:60]))
            i += 1

if bad:
    print('@Override 错挂检查 ✗')
    for b in bad:
        print('  ' + b)
    sys.exit(1)
print('@Override 错挂检查 ✓ 全库零命中')
