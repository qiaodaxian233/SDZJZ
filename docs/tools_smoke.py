#!/usr/bin/env python3
"""m508 冒烟跑法（把 DEVLOG 里散落的 javac 命令固化成一条）——两代全量 + 改动文件逐文件单编。

为什么要有第二趟（m508 撞出来的尺子盲区）
--------------------------------------
javac 无 MC jar 冒烟时，**类体是否被归因取决于源文件在命令行里的先后顺序**：`find` 无序清单下，
`NodeUpgrades.java` 排在 `StructureCoreScreen.java` 之前，主线屏整个类体（3100 行）一个错都不报——
不止它，78 个文件（全部主线屏、大半物品/包类）都少归因，总错误数 5493 vs 排序后 7633。
should-stop/compilePolicy 开关全无效，只有顺序有效；排序后"看起来"全了，但排序只是碰巧，
没有任何保证。**单文件单编（其余走 -sourcepath）的归因是完整的**（对拍过：主线屏单编 5473 条 vs 全量无序 77 条），
所以改动文件一律再单编一遍，用筛选器（tools_smoke_filter.py）只看该文件的块。

用法：
    python3 docs/tools_smoke.py            # 全量两趟 + git 改动文件逐文件单编
    python3 docs/tools_smoke.py --full-only
退出码 1 = 任一趟有必须看的真错。全量趟的 err 落 /tmp/smoke/err_{main,retro}.txt 供人工 grep 自家符号。
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TMP = pathlib.Path("/tmp/smoke")
FILTER = ROOT / "docs" / "tools_smoke_filter.py"

MAIN_DIRS = ["src/main/java", "xplat/src/main/java", "common/src/main/java"]
RETRO_DIRS = ["common/src/main/java", "versions/1.20.1/src/main/java"]


def main_files():
    out = []
    for d in MAIN_DIRS:
        out += sorted(str(p) for p in (ROOT / d).rglob("*.java"))
    return out


def retro_files():
    bg = (ROOT / "versions/1.20.1/build.gradle").read_text(encoding="utf-8")
    wl = re.findall(r"include\s+'(com/sdzjz/[^']+\.java)'", bg)
    out = []
    for w in wl:
        p = ROOT / "xplat/src/main/java" / w
        if not p.exists():
            print(f"❌ 1.20.1 白名单文件不存在：{w}")
            sys.exit(1)
        out.append(str(p))
    for d in RETRO_DIRS:
        out += sorted(str(p) for p in (ROOT / d).rglob("*.java"))
    return out


def javac(files, out_dir, err_path, sourcepath=None):
    lst = TMP / (err_path.stem + ".files")
    lst.write_text("\n".join(files) + "\n", encoding="utf-8")
    cmd = ["javac", "-J-Xmx2g", "-Xmaxerrs", "100000", "-Xmaxwarns", "0", "-proc:none", "-d", str(out_dir)]
    if sourcepath:
        cmd += ["-sourcepath", sourcepath, "-implicit:none"]
    cmd += ["@" + str(lst)]
    with open(err_path, "w", encoding="utf-8") as ef:
        subprocess.run(cmd, cwd=ROOT, stdout=subprocess.DEVNULL, stderr=ef)


def run_filter(err_path, only_file=None):
    """跑筛选器；only_file 给出时先把 err 裁成只含该文件的块（单编趟：sourcepath 拉进来的别的文件不看）。"""
    src = err_path
    if only_file:
        blocks = re.split(r"(?=^\S+\.java:\d+: )", err_path.read_text(encoding="utf-8", errors="replace"), flags=re.M)
        keep = [b for b in blocks if b.startswith(only_file) or b.startswith(str(ROOT / only_file))]
        src = err_path.with_suffix(".only.txt")
        src.write_text("".join(keep), encoding="utf-8")
    r = subprocess.run([sys.executable, str(FILTER), str(src)], capture_output=True, text=True)
    return r.returncode, r.stdout.strip().splitlines()[-1] if r.stdout.strip() else "(筛选器无输出)"


def changed_java():
    r = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True, text=True)
    out = []
    for l in r.stdout.splitlines():
        path = l[3:].strip().strip('"')
        if path.endswith(".java") and (ROOT / path).exists():
            out.append(path)
    return out


def gen_of(path):
    if path.startswith("versions/1.20.1/"):
        return ["retro"]
    if path.startswith("xplat/"):
        in_wl = str(ROOT / path) in set(retro_files())
        return ["main", "retro"] if in_wl else ["main"]
    return ["main"]


def main():
    TMP.mkdir(exist_ok=True)
    bad = 0
    passes = [("main", main_files(), ":".join(MAIN_DIRS)),
              ("retro", retro_files(), None)]
    for name, files, _ in passes:
        err = TMP / f"err_{name}.txt"
        javac(files, TMP / f"out_{name}", err)
        code, line = run_filter(err)
        print(f"[{name} 全量 {len(files)} 文件] {line}")
        bad |= code
    if "--full-only" in sys.argv:
        sys.exit(1 if bad else 0)
    # 第二趟：改动文件逐文件单编（归因完整），其余走 -sourcepath
    retro_sp = ":".join(RETRO_DIRS + ["xplat/src/main/java"])  # 单编 1.20.1 文件时 xplat 白名单类可见即可，非白名单类若被引用会在 gen_api_check 露头
    for path in changed_java():
        for gen in gen_of(path):
            sp = ":".join(MAIN_DIRS) if gen == "main" else retro_sp
            err = TMP / f"err_single_{gen}_{pathlib.Path(path).stem}.txt"
            javac([path], TMP / f"out_single_{gen}", err, sourcepath=sp)
            code, line = run_filter(err, only_file=path)
            n = sum(1 for l in err.read_text(encoding="utf-8", errors="replace").splitlines() if l.startswith(path) and ": error" in l)
            print(f"[{gen} 单编] {path}  该文件报错 {n} 条 → {line}")
            bad |= code
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
