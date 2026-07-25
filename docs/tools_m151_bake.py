#!/usr/bin/env python3
"""m151/m152 卫星节点 bbmodel → 贴图 + geo.json 一体化离线烘焙。
用法: 在仓库根 python3 docs/tools_m151_bake.py ；改模型后把新 bbmodel 放 素材/模型/卫星节点/ 重跑。
三处坑位记录:
① 坐标系: bbmodel free 格式=方块中心原点 → MC 模型空间 0..16 角点原点, X/Z 各 +8 (m152 修——
   不加整模漂出方块框西南向)。
② 欧拉序: three.js 'XYZ' = Rx·Ry·Rz(向量先吃 Rz) (m152 修; 馈源臂朝向再不对把 ORDER 换 "zyx" 重跑)。
③ 贴图提取: bbmodel 面引用贴图=数组下标(int)，贴图对象 "id" 字段是字符串——必须按 enumerate
   下标取 (m151 教训: if t["id"] in (0,7) 字符串对整数静默落空一张没写盘 → 游戏 missingno)。
"""
import json, math, os, base64

ORDER = "xyz"   # 快速迭代旋钮: "xyz" 或 "zyx"
SHIFT = 8.0
SRC = "素材/模型/卫星节点/satellite_node.bbmodel"
OUT = "src/main/resources/assets/sdzjz/models/block/satellite_node_geo.json"
TEXDIR = "src/main/resources/assets/sdzjz/textures/block"
TEX = {0: "sdzjz:block/satellite_node_atlas", 7: "sdzjz:block/satellite_dish_joint"}
UVW = {0: (16.0, 16.0), 7: (160.0, 16.0)}

d = json.load(open(SRC))

# ---- 贴图按下标提取 ----
os.makedirs(TEXDIR, exist_ok=True)
for idx, t in enumerate(d["textures"]):
    if idx in TEX:
        raw = base64.b64decode(t["source"].split(",", 1)[1])
        name = TEX[idx].split("/")[-1] + ".png"
        open(f"{TEXDIR}/{name}", "wb").write(raw)
        print("贴图落位:", name, len(raw), "bytes")
assert os.path.getsize(f"{TEXDIR}/satellite_node_atlas.png") > 100
assert os.path.getsize(f"{TEXDIR}/satellite_dish_joint.png") > 100

# ---- 几何烘焙 ----
def rot_mat(rx, ry, rz):
    cx, sx = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    cy, sy = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    cz, sz = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    Rx = [[1,0,0],[0,cx,-sx],[0,sx,cx]]
    Ry = [[cy,0,sy],[0,1,0],[-sy,0,cy]]
    Rz = [[cz,-sz,0],[sz,cz,0],[0,0,1]]
    def mul(A,B): return [[sum(A[i][k]*B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(Rx, mul(Ry, Rz)) if ORDER == "xyz" else mul(Rz, mul(Ry, Rx))

def apply(m, o, v):
    x, y, z = v[0]-o[0], v[1]-o[1], v[2]-o[2]
    return [m[0][0]*x+m[0][1]*y+m[0][2]*z+o[0]+SHIFT,
            m[1][0]*x+m[1][1]*y+m[1][2]*z+o[1],
            m[2][0]*x+m[2][1]*y+m[2][2]*z+o[2]+SHIFT]

def norm3(a, b, c):
    u = [b[i]-a[i] for i in range(3)]; v = [c[i]-a[i] for i in range(3)]
    n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
    l = math.sqrt(sum(x*x for x in n)) or 1
    return [x/l for x in n]

FACES = {
 "down":  (lambda f,t:[(f[0],f[1],t[2]),(f[0],f[1],f[2]),(t[0],f[1],f[2]),(t[0],f[1],t[2])]),
 "up":    (lambda f,t:[(f[0],t[1],f[2]),(f[0],t[1],t[2]),(t[0],t[1],t[2]),(t[0],t[1],f[2])]),
 "north": (lambda f,t:[(t[0],t[1],f[2]),(t[0],f[1],f[2]),(f[0],f[1],f[2]),(f[0],t[1],f[2])]),
 "south": (lambda f,t:[(f[0],t[1],t[2]),(f[0],f[1],t[2]),(t[0],f[1],t[2]),(t[0],t[1],t[2])]),
 "west":  (lambda f,t:[(f[0],t[1],f[2]),(f[0],f[1],f[2]),(f[0],f[1],t[2]),(f[0],t[1],t[2])]),
 "east":  (lambda f,t:[(t[0],t[1],t[2]),(t[0],f[1],t[2]),(t[0],f[1],f[2]),(t[0],t[1],f[2])]),
}

quads = []
def emit(tex, pts, uvs, double=False):
    n = norm3(pts[0], pts[1], pts[2])
    uw, uh = UVW[tex]
    u16 = [(u/uw*16.0, v/uh*16.0) for u, v in uvs]
    q = [TEX[tex]] + [round(v, 4) for v in n]
    for (p, (u, v)) in zip(pts, u16):
        q += [round(p[0],4), round(p[1],4), round(p[2],4), round(u,4), round(v,4)]
    quads.append(q)
    if double:
        n2 = [-x for x in n]
        q2 = [TEX[tex]] + [round(v,4) for v in n2]
        for (p,(u,v)) in zip(reversed(pts), list(reversed(u16))):
            q2 += [round(p[0],4), round(p[1],4), round(p[2],4), round(u,4), round(v,4)]
        quads.append(q2)

for e in d["elements"]:
    rot = e.get("rotation", [0,0,0]) or [0,0,0]
    org = e.get("origin", [0,0,0]) or [0,0,0]
    M = rot_mat(*rot)
    if e.get("type", "cube") == "cube":
        f, t = e["from"], e["to"]
        for dirn, fc in e.get("faces", {}).items():
            if fc.get("texture") not in TEX: continue
            uv = fc["uv"]; r = int(fc.get("rotation", 0) or 0)
            corners = FACES[dirn](f, t)
            uvc = [(uv[0],uv[1]),(uv[0],uv[3]),(uv[2],uv[3]),(uv[2],uv[1])]
            for _ in range(r // 90): uvc = uvc[1:] + uvc[:1]
            emit(fc["texture"], [apply(M, org, c) for c in corners], uvc)
    else:
        vs = e["vertices"]
        for fc in e.get("faces", {}).values():
            if fc.get("texture") not in TEX: continue
            keys = fc["vertices"]
            if len(keys) == 3: keys = keys + [keys[2]]
            pts = [apply(M, org, vs[k]) for k in keys]
            c = [sum(p[i] for p in pts)/4 for i in range(3)]
            n = norm3(pts[0], pts[1], pts[2])
            ref = [pts[0][i]-c[i] for i in range(3)]
            def ang(p):
                v = [p[i]-c[i] for i in range(3)]
                dot = sum(ref[i]*v[i] for i in range(3))
                det = sum(n[i]*(ref[(i+1)%3]*v[(i+2)%3]-ref[(i+2)%3]*v[(i+1)%3]) for i in range(3))
                return math.atan2(det, dot)
            order = sorted(range(4), key=lambda k: ang(pts[k]))
            uvm = fc.get("uv", {})
            emit(fc["texture"], [pts[k] for k in order],
                 [tuple(uvm.get(keys[k], [0,0])) for k in order], double=True)

json.dump(quads, open(OUT, "w"))
xs=[q[4+i*5] for q in quads for i in range(4)]; ys=[q[5+i*5] for q in quads for i in range(4)]; zs=[q[6+i*5] for q in quads for i in range(4)]
print(f"quad {len(quads)} | x {min(xs):.1f}..{max(xs):.1f} y {min(ys):.1f}..{max(ys):.1f} z {min(zs):.1f}..{max(zs):.1f} (方块=0..16)")
