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
| `SciSkin.Gfx` | 纹理 id（`gfxTex` 门面 m509）、任意物品 id（`gfxItem`）、顶点四边形（两种） | m483/m484/m488/m509 |
| `RouteBrain.Host` | 路由脑要的画布状态与传感器库存源 | m486 |
| `ProductRouter.Tail` | 产物分发的**兜底**（主线输出缓存+断网喷射 vs 本世代落仓回吐） | m495 |
| `NodeCardRenderer.Host` | 节点卡要的状态灯/阻塞原因/出线数/运行态 | m484 |
| `StorageLedgerProbe` | 账本十三口（测试用宽面） | m480 |
| `GroupFrameRenderer.View` | 画布屏状态归一：pan/zoom 记法、节点坐标（含拖动覆盖）、卡高、组元数据；`pushWorld` 世界矩阵（m511 放开给机器线层） | m507/m511 |
| `WireBundler.StoragePorts` | m193 归并的存储端接口位置（主线总线放置卡 x+14 / x+bw-14 vs 1.20.1 24×24×zoom 存储节点卡 0.25w/0.75w，卡底+2） | m511 |
| `ZoomAnim.Host` | 画布视图状态（pan 记法读三口 + `view(panX,panY,zoom)` 一次落写）：主线 panX/panY/zoom 直存直取，1.20.1 由 viewX/viewY+float zoom 换算 | m512 |
| `CanvasSettingsPanel`（无 Host，三参 init） | 画布设置面板整件：宿主只给字体/屏幕尺寸（init）与开窗前清场；`EditBox` 两代同签名 | m515 |
| `SuperBenchScreenHandler.installType` / `.Host` | 超大工作台菜单 handler 的两件出户：菜单类型（注册住各世代注册表类，注册完 `installType`）；压缩材料包/抓物笼两族主线专属物品的十口 default 宿主（`packsAvailable/isPack/tier1/tier2/packInnerId/packRawCount/packRatio/packOf/cagedType`，默认=本世代没有这件东西；主线 `LegacySuperBenchHost` 原句照搬，1.20.1 空宿主）。**默认关的口配「装了没」判官**（`super_bench_host_pack_roundtrip`） | m523 |

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
| `CraftingContainer.asCraftInput()` 覆写（返回 `CraftingInput`） | 1.20.1 **无 `CraftingInput` 类、接口无此方法** | **不覆写**——1.21.1 源码核过：接口 default `asCraftInput()=asPositionedCraftInput().input()`，`asPositionedCraftInput()=CraftingInput.ofPositioned(w,h,items)`，`CraftingInput.of(w,h,items)=ofPositioned(...).input()`，删覆写逐位同义 | m522b 血案→m523 核 |
| `CraftingContainer.getItems()` 靠父类 `SimpleContainer.getItems()` 满足（**不自声明**） | 1.20.1 **`SimpleInventory` 无此方法**（yarn 1.20.1 无 method_54454，`items` 私有无 getter），接口 `method_51305 getItems()` 抽象 → `does not override abstract method` | **本类自声明** `@Override NonNullList<ItemStack> getItems()`（返回型钉 `NonNullList`：1.21.1 才能与父类同签名覆写，1.20.1 协变满足接口 `List`）；体=`NonNullList.withSize`（两代同名 method_10213）逐格装 `getItem(i)`。第 20 闸第三类判据「抽象必自声」盯着 | m523 CI 红→m523b |
| `Slot.setByPlayer(ItemStack)` 单参 / `Slot.tryRemove(min,max,player)` / `AbstractContainerMenu.clickMenuButton/canTakeItemForPickAll/clearContainer` / `MenuType(MenuSupplier,FeatureFlagSet)` | 同名同签名（yarn 1.20.1 `Slot.setStack` method_48931 / `tryTakeStackRange` / `onButtonClick` / `canInsertIntoSlot(ItemStack,Slot)` / `dropInventory`；1.21 另有双参 `setByPlayer(ItemStack,ItemStack)` 1.20.1 无，别用） | 直接用（菜单 handler 可整文件共用） | m523 核过 |
| 白名单 xplat 文件引 `com.sdzjz.registry.*`（注册表类）/ 非白名单 xplat 物品类 | 1.20.1 只编白名单子集，**包不可见** | 注册表类→静态安装口（`installType` / `ItemData.install` 样板）；功能区物品→带默认值的宿主口（`Host` / `ExtractPort.Host` 样板）；第 20 闸自家类可见性判据（m522b）+ `--candidate` 先闸后挂（m523） | m522b 血案→m523 |

### 资源与注册表
| 1.21 | 1.20.1 | 处理 | 出处 |
|---|---|---|---|
| `ResourceLocation.parse(s)` | `new ResourceLocation(s)` | client 走 `SciSkin.Gfx.id`；**服务端/共用逻辑走 `ItemData.id(s)` / `ItemData.itemById(s)`**（m522：ScreenHandler 不能引 client 类） | m484/m522 |
| `stack.remove(DataComponents.CUSTOM_NAME)` | `stack.resetHoverName()` | 走 `ItemData.clearCustomName` | m522 |
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
| `Util.getMillis()` | 同名（yarn `getMeasuringTimeMs`） | 直接用 | m509 核过 |
| `SimpleSoundInstance.forUI(Holder<SoundEvent>,float)` + `SoundEvents.UI_BUTTON_CLICK` | 同名同签名（yarn 1.20.1 `PositionedSoundInstance.master(RegistryEntry,float)` method_47978；`UI_BUTTON_CLICK` 两代同为 `Holder.Reference`） | 直接用 | m509 核过 |
| `new EditBox(font,x,y,w,h,Component)` / `setMaxLength/setValue/getValue` | 同签名（yarn `TextFieldWidget`） | 直接用 | m508/m509 核过 |
| `widget.setX(int)/setY(int)` | 同名（1.19.4+ `Widget`/`LayoutElement` 接口，method_46421/46419） | 直接用 | m508/m509 核过 |
| `screen.setFocused(GuiEventListener)` / `widget.setFocused(boolean)` | 同名（yarn `ParentElement.setFocused(Element)` / `Element.setFocused(Z)`） | 直接用 | m508/m509 核过 |
| `Screen.hasShiftDown()` | 同名 | 直接用 | m508/m509 核过 |
| `mouseScrolled(x,y,dx,dy)` 四参 / `renderBackground(ctx,mx,my,delta)` 四参 | 三参 / 单参（1.20.1 客户端签名差） | **屏是世代壳各写**，不下沉 | m448/m456 |

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
| **存储端卡片形状** | 主线是总线带上的放置卡（屏幕坐标 `snx/sny`，`bw()/bh()` 随 busScale），本世代是画布上的 24×24 存储节点卡（世界坐标乘 zoom）——接口位置不同，画线共用件只吃四个屏幕坐标（`WireBundler.StoragePorts`） | m511 |
| **机器线层的矩阵** | 主线机器↔机器线在 `translate(panX,panY)+scale(zoom)` 世界矩阵下画、pxScale=zoom（m197 线宽封顶按世界单位）；本世代 m488~m510 在屏幕坐标层（sx/sy）传 zoom 是半搬——线宽随放大变细、脉冲间距不随缩放。m511 起本世代机器线层用 `GroupFrameRenderer.pushWorld` 包成同一形状 | m511 |
| **画布配色作用域** | 主线 `render()` 整帧 `SciSkin.scopeCanvas(true)`（m214 主题分家，term*() 读画布 7 色）；本世代 m483~m510 没开，共用件在 1.20.1 一直读终端配色。m511 起同开 | m511 |
| **悬停聚焦** | 主线 m164b：悬停某节点时无关线压暗（`lit` 假→`mix(termInk, wire, 0.30)`）；本世代 m511~m517 无悬停聚焦，lit 恒真。**m518 起两代同律**（主线原文搬进 1.20.1 `renderBg`）：世代差只有三处记法——节点命中 `wnx(i)/wny(i)`（主线 `wnx(be,nodes,i)`）、卡高 `NH`（主线 `NH+26` 含升级格行，本世代无升级系统）、存储端悬停 `storageAt(mouseX,mouseY)`（主线 m265 总线放置卡 `snx/sny+bw/bh`，本世代无总线） | m511 记→m518 搬 |
| **线色** | 主线走 m198 配置 `wireOut()/wireIn()`（默认薰衣草紫/柔绿 m207）；本世代 m458~m510 写死 Palette ON/GOLD/ACCENT（产出线绿、供料线金）。m511 起同走配置 | m511 |
| **滚轮缩放手感** | 主线 m185/m186：因子 1.1/0.9、范围走配置 `canvasZoomMin/Max`（默认 5%~800%）、`canvasSmoothZoom` 指数缓动；本世代 m456~m511 硬编码 1.15 倍 / 0.35~2.5 瞬时跳变。m512 起同走共用件与配置 | m512 |
| **卡片层与缩放** | **m517 起两代同律**：机器卡在世界矩阵下随 zoom 缩（1.20.1 节点卡层挪进机器线层那份 `pushWorld`，命中框 `NW*zoom` m507 已改）；**存储卡两代都不随 zoom**——主线放置卡是总线卡（`bw()/bh()` 只随总线倍率），本世代 24×24 屏幕实占；m458 起本世代线口按 `24*zoom` 算与实际画的卡不一致（缩放≠1 时线口悬空），m517 改回 24 | m512 记→m517 搬 |
| **存储连线包语义** | 主线 `StorageLinkPayload(pos, machine, endpoint, dir, dim)` 按方向切换（断某一向发一次）；本世代 `StorageLink(pos, machine, endpoint)` 三态循环（无→产出0→供料1→断）——"断开全部连线"要按当前态转到断：产出态连发两次、供料态一次（服务端按序处理、每包推快照，中间帧短暂显供料线）。**m519 拖线落靶同法**：`linkStorageTo(machine,endpoint,want)` 按快照当前态沿环（产出→供料→无）算连发 1~2 次到主线 toggle 语义的目标态；本世代一对只存一条边（主线产出/供料可并存）是数据模型差 | m513/m519 |
| **连线交互** | 主线始终是**拖线**：出口柱起手（m122 抓取半径 `max(7,10/zoom)`）/进口柱起手反向（m342）/总线放置卡供料口起手（m265），松手落靶（落点外扩 `6/zoom`）。本世代 m456~m518 是我自造的「顶栏连线钮切模式 + 两次点击」；**m519 起同主线拖线**，点击模式与钮退役；存储卡供料口在 24×24 卡底 `x+0.75w`（StoragePorts 同口），抓取半径按卡宽收 6×8 | m519 |
| **节点菜单条目面** | 主线 `openNodeMenu` 三十余条目背后是暂停/融合拆解/目标拾取/名单/监测/信标/区块器等机制；本世代机制面=四操作+存储连线+分组，只装：断开全部连线 / 组合所选 / 组合相连 / 取出机器 / 取消。存储菜单不装"收回总线"（无总线停靠） | m513 |
| **画布底/网格/暗角层** | 主线 `canvasBg()`（m203 主题/m217 配置覆盖）+ m220 装饰底图 + m188 双色网格（屏幕 32px 定距、世界相位、`termGridMajor/Minor`）+ 网格浓度/暗角强度。**m516 起两代同一份 `CanvasBackdrop`**（两口：底色+装饰 / 网格+暗角，主线专有的顶/底终端浅带夹在两口之间不搬）；差只剩布局参数：网格起始 y（主线 34 / 本世代 16）、暗角区（主线 (0,23)~(workRight,botTop) / 本世代 (0,16)~(width-侧栏,height)）；装饰底图贴图拷进 1.20.1 资源目录（m489 律） | m515 记→m516 搬 |

## 四、用法

**整段搬之前**：把要搬的原文过一遍第二节的左列，命中的就查有没有现成世代口（第一节），
没有就新建一个口——**不要在共用层留 1.21 专属调用**。
搬完跑 `python3 docs/tools_gen_api_check.py`（第 20 闸）复核。
