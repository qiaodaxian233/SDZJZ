# 贴图清单（用 GPT 做图后按此放置）

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
