# -*- coding: utf-8 -*-
"""m367 Common 硬闸（顾问 Phase 1.5 ①②③）——退出码非零=CI 红。

① Common 名册（docs/common_manifest.txt）内文件：剥注释/字符串后禁止任何
   net.minecraft / net.fabricmc / com.mojang 字面（import 与内联 FQN 一并抓）。
   盲区第五案的教训：沙盒 javac 缺 MC 类会整文件归错，本闸让"平移漏点"第一时间爆。
② Object 句柄边界：Common 名册内（platform/ 包除外）禁止 Object 类型**字段**
   （句柄只许作形参/返回值/record 组件透传，存进字段=Common 开始持有 MC 对象身份）。
③ Platform 防膨胀：platform/Platform.java 服务字段数硬顶 4（现=2：recipes/configDir）。
   超顶=先去顾问方案 §"Platform 只做 bootstrap/registry" 报到再说。
附：名册文件必须存在（防搬包后名册漂移成空话）。
"""
import re
import sys

MC = re.compile(r"net\.minecraft|net\.fabricmc|com\.mojang")
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)
STRING = re.compile(r'"(?:\\.|[^"\\])*"')
OBJ_FIELD = re.compile(r"^\s{4}(?:private|protected|public|static)[\w\s]*?\bObject(?:\[\])?\s+\w+\s*[;=]", re.M)  # 字段必带修饰符+类级缩进；方法体局部与纯值元组（Object[] 候选三元组）天然豁免


def strip(body):
    return STRING.sub('""', COMMENT.sub("", body))


def main():
    bad = []
    manifest = [l.strip() for l in open("docs/common_manifest.txt", encoding="utf-8")
                if l.strip() and not l.startswith("#")]
    plat_fields = 0
    for p in manifest:
        try:
            body = open(p, encoding="utf-8").read()
        except FileNotFoundError:
            bad.append("名册文件不存在（搬包后未更新名册？）: " + p)
            continue
        code = strip(body)
        for m in MC.finditer(code):
            ln = code[:m.start()].count("\n") + 1
            bad.append("①MC 依赖泄入 Common: %s:%d 「%s」" % (p, ln, m.group()))
        if "/platform/" not in p:
            for m in OBJ_FIELD.finditer(code):
                bad.append("②Object 句柄存字段（只许形参/返回/record 透传）: %s 「%s」" % (p, m.group().strip()))
        if p.endswith("platform/Platform.java"):
            plat_fields = len(re.findall(r"private static \w[\w.<>]* \w+;", code))
    if plat_fields > 4:
        bad.append("③Platform 服务字段=%d 超硬顶 4：Platform 只做 bootstrap/registry，新 SPI 各归各模块" % plat_fields)
    if bad:
        for b in bad:
            print("✗ " + b)
        print("✗ Common 硬闸红：%d 项（名册 %d 文件）" % (len(bad), len(manifest)))
        sys.exit(1)
    print("✓ Common 硬闸：%d 文件零 MC/Fabric/Mojang 依赖，句柄零存字段，Platform 字段 %d/4" % (len(manifest), plat_fields))


if __name__ == "__main__":
    main()
