# 世代 API 对照表（1.20.1 ↔ 1.21.1）

> **这张表是为「整段搬」服务的**。真移植的动作是把主线原文粘进共用层，
> 而粘之前必须回答一个问题：**这段代码里有没有 1.20.1 没有的 API？**
> m487 就是漏了这一步（`getComponentsPatch()` 混进共用账本），害得 1.20.1 构建挂掉。
>
> 表里每一行都是**踩过或核过的**，不是凭印象写的。加新行时注明出处（哪一刀撞的）。
> 配套闸：`docs/tools_gen_api_check.py`（扫共用层是否出现右列的 1.21 专属符号）。

## 一、已建的世代口（先查这里——多半已经有人替你收过了）

| 世代口 | 收的是什么 | 刀号 |
|---|---|---|
| `Env` / `Hooks` | 加载器事件、配置目录、模组探测 | m435 |
| `ItemData`（五口） | 栈附加数据读写（组件 ↔ tag）、**「有没有附加数据」** | m437/m451 |
| `NodeTags.Ident` | 节点/机器身份判定（六族 + `defOf`） | m472 |
| `StorageAccess` | 存储核心四口（生产侧窄契约） | m464 |
| `RecipeAccess`（四域八口） | 配方/熔炼/酿造/附魔查询 | m368/m373 |
| `CanvasGraphState.StackCodec` | 整栈 ↔ NBT 编解码 | m477 |
| `StackKey.Kind` | 栈身份相等与哈希 | m478 |
| `SciSkin.Gfx` | 纹理 id、任意物品 id、顶点四边形（两种） | m483/m484/m488 |
| `RouteBrain.Host` | 路由脑要的画布状态与传感器库存源 | m486 |
| `ProductRouter.Tail` | 产物分发的**兜底**（主线输出缓存+断网喷射 vs 本世代落仓回吐） | m495 |
| `NodeCardRenderer.Host` | 节点卡要的状态灯/阻塞原因/出线数/运行态 | m484 |
| `StorageLedgerProbe` | 账本十三口（测试用宽面） | m480 |

## 二、API 逐项对照（**1.21 专属 → 1.20.1 对位**）

### 物品与数据
| 1.21 | 1.20.1 | 处理 | 出处 |
|---|---|---|---|
| `stack.getComponentsPatch().isEmpty()` | `!stack.hasTag()` | **走 `ItemData.has(s)`** | m487 血案 |
| `ItemStack.isSameItemSameComponents(a,b)` | `ItemStack.isSameItemSameTags(a,b)` | 走 `StackKey.Kind.same` | m478 |
| `stack.getComponentsPatch().hashCode()` | `stack.getTag().hashCode()`（null→0） | 走 `StackKey.Kind.dataHash` | m478 |
| `ItemStack.parse(lookup, tag)` | `ItemStack.of(tag)` | 走 `StackCodec.load` | m477 |
| `stack.save(lookup)` | `stack.save(new CompoundTag())` | 走 `StackCodec.save` | m477 |
| `stack.copyWithCount(n)` | 同名同签名 | 直接用 | m475 核过 |

### 资源与注册表
| 1.21 | 1.20.1 | 处理 | 出处 |
|---|---|---|---|
| `ResourceLocation.parse(s)` | `new ResourceLocation(s)` | 走 `SciSkin.Gfx.id` | m484 |
| `ResourceLocation.fromNamespaceAndPath(ns,p)` | `new ResourceLocation(ns,p)` | 走 `SciSkin.Gfx.tex` | m483 |
| `BuiltInRegistries.ITEM.getKey(item)` | 同名同签名 | 直接用 | m485 核过 |
| `level.dimension().location()` | 同名同签名 | 直接用 | m480 核过 |
| `level.registryAccess()` | 同名（但无 `HolderLookup.Provider` 参数的下游用法） | 句柄走 `Object` 透传 | m477 |

### 渲染
| 1.21 | 1.20.1 | 处理 | 出处 |
|---|---|---|---|
| `vc.addVertex(mat,x,y,z).setColor(c)` | `vc.vertex(mat,x,y,z).color(r,g,b,a).endVertex()` | 走 `Gfx.quad` / `Gfx.quadVC` | m483/m488 |
| `GuiGraphics.bufferSource()` / `RenderType.gui()` | 同名可用 | 直接用 | m483 编译核过 |
| `ctx.blit(tex,...)` | 同名（重载略有差异，用四参与十参那两种） | 直接用 | m489 |

### 方块实体与存档
| 1.21 | 1.20.1 | 处理 | 出处 |
|---|---|---|---|
| `saveAdditional(tag, lookup)` | `saveAdditional(tag)` | **留世代壳各写**，不下沉 | m485 |
| `loadAdditional(tag, lookup)` | `load(tag)` | 同上 | m485 |
| `BlockEntity(type,pos,state)` | 同签名 | 直接用（但**构造器别跟着账本一起删**） | m487 血案 |

### 附魔 / 药水（⑤d 用得上，尚未落地）
| 1.21 | 1.20.1 | 处理 | 出处 |
|---|---|---|---|
| `Holder<Enchantment>` + `registryAccess().lookupOrThrow(...)` | `BuiltInRegistries.ENCHANTMENT.get(rl)` → `Enchantment` | 待建 `EnchAccess` 实现 | m476 普查 |
| `Enchantment.getAnvilCost()` | **无直接对位**（同期是 `Rarity.getWeight()`） | **语义缺口，待作者拍板 A/B** | m476 普查 |
| `PotionContents` 组件 + `level.potionBrewing()` | `PotionUtils` + `BrewingRecipeRegistry`（静态） | 待建 `BrewAccess` 实现 | m476 普查 |

## 三、非 API 类的世代差（同样会咬人）

| 事项 | 说明 | 出处 |
|---|---|---|
| **资源文件** | 共用件 blit 的贴图，1.20.1 资源目录里可能根本没有（槽位画成紫黑格）。编译器与冒烟都管不着 | m489 |
| **节点栈 NBT 键** | 两代共享命名空间，同键异义不报错（`xc` 撞抽取累计） | m474 |
| **Java 版本** | 1.20.1 用 JDK 17，共用层不许出现 Java 21 独有语法 | `tools_common_gate` |
| **输出缓存 / 断网喷射** | 主线有，本世代无（改回灌持料）。**m495 起已收进 `ProductRouter.Tail` 口**——真去对了才发现两代只差最后三行，循环体逐字相同 | m473/m475→m495 |
| **默认主存储** | 主线 `resolveInputSource` 有，本世代无（未连线按 0 计） | m486 |

## 四、用法

**整段搬之前**：把要搬的原文过一遍第二节的左列，命中的就查有没有现成世代口（第一节），
没有就新建一个口——**不要在共用层留 1.21 专属调用**。
搬完跑 `python3 docs/tools_gen_api_check.py`（第 20 闸）复核。
