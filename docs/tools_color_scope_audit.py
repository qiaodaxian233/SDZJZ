#!/usr/bin/env python3
# m255 画布事件路径裸取色巡检工具（m239 漏洞类回归尺）：
# 漏洞类=StructureCoreScreen 事件路径（鼠标/键盘/init 等，render 帧外）调 term*() 族取色——
# scopeCanvas 只在 render try/finally 内开，事件路径裸读会取到终端主题；再写回配置即 m239 污染。
# 用法：python3 docs/tools_color_scope_audit.py   （命中即退出码 1，可挂 CI）
import re, sys, os

SRC = os.path.join(os.path.dirname(__file__), '..', 'src/main/java/com/sdzjz/client/StructureCoreScreen.java')
EVT = ['init', 'removed', 'mouseScrolled', 'mouseClicked', 'mouseDragged', 'mouseReleased', 'keyPressed', 'charTyped']
SAFE = {'settColorVal'}  # m239 收口：自带 scopedCanvas 保存/恢复，事件路径可用

def main():
    lines = open(SRC, encoding='utf-8').read().split('\n')
    decls = []
    for i, l in enumerate(lines, 1):
        m = re.match(r'    (?:public|private|protected|static)[\w<>\[\], ]*?\b(\w+)\s*\(', l)
        if m and not l.strip().startswith('//') and '=' not in l.split('(')[0]:
            decls.append((i, m.group(1)))
    names = set(n for _, n in decls)
    bodies = {}
    for idx, (ln, n) in enumerate(decls):
        end = decls[idx + 1][0] - 1 if idx + 1 < len(decls) else len(lines)
        bodies.setdefault(n, []).append(lines[ln - 1:end])
    def code_lines(n):
        for b in bodies.get(n, []):
            for cl in b:
                if not cl.strip().startswith('//'): yield cl
    def calls_of(n):
        out = set()
        for cl in code_lines(n):
            for c in re.findall(r'\b(\w+)\s*\(', cl):
                if c in names and c != n: out.add(c)
        return out
    def reads_color(n):
        if n in SAFE: return False
        return any(re.search(r'\bterm[A-Z]\w*\s*\(|SciSkin\.(hex|mix|term\w+)\s*\(', cl) for cl in code_lines(n))
    bad = []
    for e in EVT:
        if reads_color(e): bad.append((e, e, 0))
        seen, frontier, depth = {e}, calls_of(e), 1
        while frontier and depth <= 3:
            nxt = set()
            for f in frontier:
                if f in seen: continue
                seen.add(f)
                if reads_color(f): bad.append((e, f, depth))
                nxt |= calls_of(f)
            frontier, depth = nxt, depth + 1
    # render 作用域完整性：scopeCanvas(true) 必须配 finally 关
    src = '\n'.join(lines)
    r = re.search(r'public void render\(.*?\n    \}', src, re.S)
    assert r and 'scopeCanvas(true)' in r.group(0) and 'finally' in r.group(0) and 'scopeCanvas(false)' in r.group(0), \
        'render 作用域 try/finally 结构被破坏'
    if bad:
        for e, f, d in bad: print(f'!! 事件路径裸取色: {e} →({d}层) {f}')
        sys.exit(1)
    print(f'裸取色巡检 ✓ 事件路径 {len(EVT)} 条×3层调用图零命中；render 作用域 try/finally 完整')

if __name__ == '__main__':
    main()
