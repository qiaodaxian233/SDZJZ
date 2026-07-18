# GUI 素材生成指南（m50）

> 设计稿(线框)在本目录 4 张 PNG，照稿子里的分区去生成装饰背景。
> **关键规则**：槽位/文字/按钮/列表全部由代码画在上层 —— 你只出"装饰底"，**不要画文字、不要画格子**。
> ✅ 4 张已于 m51 全部落位：数据面板/交易所/超大工作台各用专属背景，画布接入全屏科幻边框。

## 需要的素材（4 张，按优先级）

### 1. `data_panel_gui.png` — 数据面板专属背景
- 比例 360:256（出 1080×768 或更大都行）
- 终端/仓储质感：深色面板 + 青色数据网格；右上留搜索条区、右侧大片是格子区（画隐约的凹陷底板即可）、左侧竖条装饰区可放 LOGO 纹样
- 提示词:
```
Sci-fi storage terminal UI background, dark navy metal panel, faint cyan data grid,
subtle recessed inventory area on the right, decorative side column on the left,
no text, no icons, no slot grid, flat game-UI style. Aspect ratio 360:256.
```

### 2. `trade_center_gui.png` — 村民交易所专属背景
- 比例 360:256
- 交易/绿宝石质感：深色底 + 淡绿宝石绿点缀 + 羊皮纸质感的列表区暗示
- 提示词:
```
Sci-fi trading post UI background, dark panel with subtle emerald green accents,
faint parchment-textured list area in the center, small slot alcove upper-left,
no text, no icons, no grid, flat game-UI style. Aspect ratio 360:256.
```

### 3. `super_bench_gui.png` — 超大工作台背景
- 比例约 300:330（竖向）
- 紫金工作台质感（对应方块外观）：左侧大网格凹陷区、右中一个结果圆槽、右侧配方栏暗示
- 提示词:
```
Ornate crafting station UI background, dark metal with purple and gold glowing accents,
large recessed square crafting area on the left, circular result socket middle-right,
narrow recipe column on the far right, no text, no icons, no grid. Aspect ratio 300:330.
```

### 4. `canvas_frame.png` — 结构核心画布装饰边框
- 比例 16:10 左右，**中间必须大面积留空/纯深色**（画布内容代码画）
- 只要四周科幻边框 + 四角装饰 + 顶部窄条
- 提示词:
```
Sci-fi HUD frame overlay, decorative dark navy border with cyan circuit corners,
large empty dark center area, thin status bar strip at top,
no text, no icons, frame only. Aspect ratio 16:10.
```

## 落位
生成后发我：1/2/3 我落到 `textures/gui/` 并把对应界面切到专属背景；4 我接到画布四周。
