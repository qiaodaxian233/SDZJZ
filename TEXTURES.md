# 贴图清单（用 GPT 做图后按此放置）

> ⚠️ 本清单只含 **MOD 自己的物品/方块**（sdzjz:）。机器产出的原版物品（线/圆石/骨头/末影珍珠等）用原版贴图，**无需制作**。提示词里的 “emitting X” 只是机器图标的区分标志，不是要重画原版物品。


全部 **16×16 PNG、透明背景、MC 像素风**。文件名必须与下面一致，丢进对应目录即可，**无需改代码**（模型已指向 sdzjz:item/xxx、sdzjz:block/xxx）。缺图时游戏显示品红/黑格占位，不影响运行。

## 物品贴图 → `src/main/resources/assets/sdzjz/textures/item/`
| 文件名 | 是什么 | 建议画面 |
|---|---|---|
| core_module.png | 核心模块 | 蓝色电路核心/芯片 |
| speed_upgrade.png | 速度升级 | 向上箭头/齿轮，青色 |
| count_upgrade.png | 数量升级 | 叠加方块，紫色 |
| parallel_upgrade.png | 并发升级 | 分叉/树状，绿色 |
| capture_cage.png | 抓物笼子 | 铁笼子 |
| wire_brusher.png | 刷线机 | 机器+线团 |
| cobble_maker.png | 刷石机 | 机器+圆石 |
| bone_farm.png | 刷骨机 | 机器+骨头 |
| gunpowder_farm.png | 刷火药机 | 机器+火药 |
| flesh_farm.png | 刷腐肉机 | 机器+腐肉 |
| pearl_farm.png | 刷珍珠机 | 机器+末影珍珠 |
| slime_farm.png | 刷史莱姆机 | 机器+粘液球 |
| iron_farm.png | 刷铁机 | 机器+铁锭 |
| tree_farm.png | 全自动树场 | 机器+树 |
| sugarcane_farm.png | 甘蔗机 | 机器+甘蔗 |
| bamboo_farm.png | 竹子农场 | 机器+竹子 |
| sand_maker.png | 刷沙机 | 机器+沙 |
| ice_maker.png | 刷冰机 | 机器+冰 |
| obsidian_maker.png | 黑曜石机 | 机器+黑曜石 |
| swamp_spawner.png | 沼泽刷怪塔 | 机器+多种掉落 |
| witch_tower.png | 女巫塔 | 机器+女巫帽/药水 |
| guardian_farm.png | 守卫者农场 | 机器+海晶碎片 |

## 方块贴图 → `src/main/resources/assets/sdzjz/textures/block/`
（方块用 cube_all，一张图铺满六面；这张图同时当方块物品图标）
| 文件名 | 是什么 |
|---|---|
| structure_core.png | 结构核心（深色科幻方块，青色发光核心） |
| super_bench.png | 超大工作台（更华丽，紫/金核心） |
| data_panel.png | 数据面板（屏幕/终端质感） |

> GPT 出图提示可用：「16x16 pixel art, Minecraft item icon style, transparent background, <描述>」。
> 想要更精细可后续给方块做多面贴图（顶/侧/底分开），到时我改模型。

---

## 提示词（复制即用）

> 注意：图像模型出的是"像素风"大图，不是真 16×16。生成后按**最近邻**缩到 16×16 再放进目录。

**万能模板**（每张都用这个开头，把 Subject 换掉）：
```
Minecraft item icon, pixel art, single object centered, thick clean outline,
limited flat palette, simple shading, transparent background, no text, no drop shadow.
Subject: <描述>
```
**机器统一机身**：`a dark navy sci-fi machine cube with a glowing cyan core, emitting <产物>`

### 物品 Subject
- core_module: a blue circuit chip core, glowing cyan lines
- speed_upgrade: an upgrade chip with an upward double arrow, cyan
- count_upgrade: an upgrade chip with stacked cubes, purple
- parallel_upgrade: an upgrade chip with a branching fork icon, green
- capture_cage: a small empty iron barred cage
- wire_brusher: machine cube emitting a white string ball
- cobble_maker: machine cube emitting gray cobblestone
- bone_farm: machine cube emitting a white bone
- gunpowder_farm: machine cube emitting gray gunpowder
- flesh_farm: machine cube emitting pink-brown rotten flesh
- pearl_farm: machine cube emitting a green ender pearl
- slime_farm: machine cube emitting a green slime ball
- iron_farm: machine cube emitting an iron ingot
- tree_farm: machine cube emitting a small green tree
- sugarcane_farm: machine cube emitting green sugar cane
- bamboo_farm: machine cube emitting bamboo stalks
- sand_maker: machine cube emitting yellow sand
- ice_maker: machine cube emitting a pale-blue ice block
- obsidian_maker: machine cube emitting a purple-black obsidian block
- swamp_spawner: machine cube with mixed drops, swampy green tint
- witch_tower: machine cube with a purple witch hat and potion
- guardian_farm: machine cube emitting a teal prismarine shard

### 方块 Subject（一张铺满六面）
- structure_core: dark navy sci-fi machine block face, glowing cyan hex core, metal frame
- super_bench: ornate dark machine block face, glowing purple-gold core
- data_panel: dark terminal block face, glowing cyan holographic data grid
