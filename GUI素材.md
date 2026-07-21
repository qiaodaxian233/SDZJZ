# GUI 素材清单

> 槽位/文字/按钮由代码绘制（永远对齐），你只做**装饰背景**，糊上去即可，不用抠坐标。
> 做好放 `src/main/resources/assets/sdzjz/textures/gui/`（或先丢 `素材/`，我来归位+缩放）。做好我就把界面从纯色块换成贴图。

## 要的图
| 文件名 | 用途 | 规格 | 内容 |
|---|---|---|---|
| `structure_core_gui.png` | 结构核心全屏面板背景 | 比例≈360:256(16:11)，可 512/720 宽 | 深色科幻仪表盘、边框、隐约分区面板/电路纹。**别画文字和槽格** |
| `data_panel_gui.png` | 数据面板全屏背景(待全屏化) | 同上 | 终端/屏幕墙质感、青色数据感。别画文字槽格 |
| `slot.png` | 槽位格（**已接线，简易稿在库**） | 18×18 | 内凹插槽+四角青色角括号；同名覆盖即换肤 |
| `button.png` | 按钮底（**已接线，简易稿在库**） | 200×32 | 上半常态/下半悬停；左右各 8px 帽区放装饰、中段须均匀可拉伸 |

> **m118 生图工作流**：把 `素材/skin_reference.png`（参考板）连同两张简易稿一起喂给 GPT，
> 用下面的提示词出精修图 → **出大图丢 `素材/` 我来缩放归位** → 进游戏即全 MOD 生效，代码零改动。

## GPT 生图提示词（复制即用）

**开场白模板（每轮都先发这段，附件带 skin_reference.png + 对应简易稿）**
> 你是游戏 UI 像素美术。我发了两张图：第一张 skin_reference.png 是风格参考板——配色（九色 hex）、
> 直角、深海军蓝底、青色角括号这套视觉语言全部以它为准，**不要重画这张板子**，它只是规范。
> 第二张是游戏里在用的简易稿，你要画的是它的精修版：结构/比例/元素位置照抄简易稿，质感细节升级。
> 要求：正视平面、直角无圆角、无文字无图标、不要浮雕、不要金色、不要强光晕，配色只用参考板九色。
> 现在画：【接下面对应条目】

实操：一轮只要一张图（slot/button 分两轮，每轮都带参考板）；跑偏就回"重画：保持直角、去字、
纯平面，其它不变"；出的大图原样丢 素材/ 文件夹，缩放归位我来。

**slot.png（让 GPT 画 512×512，最终缩 18×18）**
> 参考我发的参考板和小图的风格，画一张正方形游戏 UI 槽位贴图：深海军蓝（#0A1626）内凹金属插槽，
> 外圈一圈深蓝细边（#163049），四个角有青色（#2EC4FF）L 形角括号，内部可加极淡电路纹理和磨砂金属
> 质感。直角、扁平游戏 UI、正视无透视、边缘干净。无文字、无图标、无物品、无圆角、不要强光晕。
> 以大色块为主别画细碎纹理——缩到 18×18 后角括号要仍清晰。
> 英文关键词：sci-fi game UI inventory slot, dark navy recessed metal socket, cyan L-shaped corner
> brackets, faint circuitry, flat game UI, sharp square corners, front view, no text, no icons, symmetrical

**button.png（让 GPT 画 1600×128 两张：常态+悬停，我拼 200×32）**
> 横向长条游戏 UI 按钮底图，比例 200:16：深蓝金属面（#0C1E30）、蓝色细边框（#1C5A80）、顶部一条
> 淡高光；左右两端各留一小段装饰帽区（斜角饰纹），中间大段必须均匀纯净（会被横向拉伸）。
> 正视、直角、无文字无图标。悬停态同款：边框换亮青（#2EC4FF）、面板略亮（#123249）、内侧一圈淡青内发光。
> 英文关键词：sci-fi game UI button bar, dark navy metal, thin blue frame, subtle top highlight,
> decorative end caps, clean stretchable middle, flat game UI, no text / hover: bright cyan frame, soft inner glow

**通用红线**：禁止圆角、金色描边、浮雕立体、透视角度、图内文字；配色严格用参考板九色。

## 提示词
```
Sci-fi control panel UI background, dark navy metal with faint cyan circuitry,
subtle recessed sub-panels, decorative frame, no text, no icons, no grid of slots,
flat game-UI style, seamless edges. Aspect ratio 16:11.
```
超大工作台若也想单独底：把配色换成紫/金。

## 结构核心面板布局坐标（面板 360×256，左上为原点；想让装饰对齐可参照）
- 机器区：x16 y38 宽100 高46（槽 2×4，起点 x24 y42，间距20）
- 升级区：x16 y94 宽100 高24（槽 x24 y98，间距20）
- 产出区：x182 y38 宽100 高46（槽 2×4，起点 x190 y42）
- 状态区：x182 y94 宽162 高40（文字读数）
- 背包区：x95 y172 宽170 高78（槽 起点 x99 y176）
- 按钮：开机 x24 y140 宽150 高16；停止 x186 y140 宽150 高16
- 标题“结构核心/超大工作台”在 (24,12)，运行状态在右上

> 装饰底可不严格对齐（槽在上层代码画）；想严丝合缝就按上面留出凹槽。
