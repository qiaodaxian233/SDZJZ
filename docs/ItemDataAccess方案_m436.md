# ItemDataAccess 双实现方案（m436 普查稿，1.20.1 格开工钥匙——拍板前不动刀）

> 背景：四锚点 m402 拍板、Create 兼容 m434 记档为 1.20.1/1.21.1 承诺范围；六漏斗接口化
> m433~m435 收口后，1.20.1 格只剩本件前置。m401 预估"成本≈其余三格之和"，本稿把账拆开算实。

## 一、普查结果（机器化盘点，2026-08-21，基于 0.1.435）

### 1. 物品附加数据（本稿主角）

- **CUSTOM_DATA：109 触点 / 22 文件**（SCBE 33、TerminalItem 14、ChunkScanner 8、AutoFeeder 7、
  ChunkVault/ChunkRemover/TradeCenter 各 6、DataPanelScreenHandler 5、NodeTags 3、其余长尾）。
  m401"只有 NodeTags 一个口"仅对**节点状态**成立——各物品（终端/区块工具/契约/随身仓）直摸
  组件 API 的占大头，**修正记档**。
- **好消息=惯用形态三式统一**（CustomData.of ×58 / CustomData.EMPTY ×29 全数落在三式里）：
  ①拷贝读 `getOrDefault(CUSTOM_DATA, EMPTY).copyTag()`；②只读视图 `.getUnsafe()`；
  ③回写 `set(CUSTOM_DATA, CustomData.of(n))`。NodeTags 的 nbtOf/nbtView/setNbt 就是这三式的
  既有样板——SPI 面天然只有三四个口。
- **零散组件另册（7 处）**：STORED_ENCHANTMENTS×2、FOOD×2、CUSTOM_NAME×2、POTION_CONTENTS×1、
  INSTRUMENT×1、ENCHANTMENT_GLINT_OVERRIDE×1。

### 2. 物品数据之外的 1.20.5+ 类型面（1.20.1 格的另两座山，账必须一起算）

- **网络：xplat 25 文件**用 CustomPacketPayload/StreamCodec/RegistryFriendlyByteBuf——这套类型
  是 1.20.5 网络重写的产物，**1.20.1 上不存在**。Net 门面（m433）的接口签名本身就版本耦合。
- HolderLookup.Provider 4 文件 / ItemStack.parse 2 文件（NBT⇄栈编解码，1.20.1 对位
  ItemStack.of/save 旧签名）。

## 二、SPI 设计（ItemData 门面，三式+判空）

`xplat/item/ItemData`（m433 同范式：门面+嵌套 Impl+入口安装+未装硬失败）：

- `CompoundTag copyOf(ItemStack)`＝三式①（无数据返回新空表，写回才生效——沿 NodeTags 现约定）
- `CompoundTag view(ItemStack)`＝三式②（只读不写，热路径省拷贝；无数据返回共享空表）
- `void write(ItemStack, CompoundTag)`＝三式③（**空表=清除**：组件侧 remove(CUSTOM_DATA)、
  1.20.1 侧 setNbt(null)——"不混堆不变裸"红线的判空归一点，双实现必须同判）
- `boolean has(ItemStack)`（快速判定，省 view+isEmpty）

**双实现**：`Fabric121ItemData`＝现三行组件式原样；`Fabric120ItemData`＝getNbt/getOrCreateNbt/
setNbt 对位（copyOf=null 判定+copy；view=null 时共享空表）。

**大红利（写死进档）**：原版 DFU 在 1.20.5 升档时把整个物品 tag 自动收进 `minecraft:custom_data`
——玩家 1.20.1 存档升 1.21.1，我们的数据被原版搬运，**键布局零改动零迁移代码**。

**零散组件对位表**：STORED_ENCHANTMENTS→`StoredEnchantments` NBT 列表；POTION_CONTENTS→`Potion`
字符串；CUSTOM_NAME→`display.Name`；INSTRUMENT→NBT 键；FOOD→1.20.1 无组件，判定退化为
原版 `isEdible()`；GLINT→1.20.1 无对位，**装饰性降级**（包展示流光不显示，功能零影响）。
七处各自小，随用随对位，不进 ItemData 主口。

## 三、路线（分四段，每段独立可验收）

- **P-A 收口刀（推荐：现在就打，1.21.1 版本内零风险）**：立 ItemData 门面，全仓 109 触点
  机械化切门面（NodeTags 三口改走它；逐文件计数断言+CI GameTest 判官，绞杀者线同套工艺）。
  打完之后组件触点=1 个 Impl 文件，1.20.1/后续任何版本的物品数据差异被钉死在一处。
- **P-B 1.20.1 bootstrap + 存储网络 + Create 对接（作者承诺项的最早交付点）**：
  versions/1.20.1 子构建（loom 经典线+mojmap——1.20.1 的 loom 官方支持 mojang 映射，
  m422 迁移红利直接吃到）、common 挂载、ItemData120/Xfer120/Env120/Hooks120 四实现、
  存储网络域移植、**Create 传送带↔数据线互通=本段验收线**。
- **P-C 画布/机器全量**：19 个 payload 在 1.20.1 世代内按 FabricPacket/PacketByteBuf 对位
  重写（网络面 25 文件是 1.20.1 格最大单项，占 m401"≈其余三格之和"估算的大头）+ 四屏适配。
- **P-D JEI/Create 深度联动**（配方分类共存+对接打磨），与 1.21.1 侧共用设计。

## 四、拍板问题（缺省=按推荐）

1. **P-A 现在打还是随 1.20.1 一起？** 推荐现在（版本内零行为、判官现成、越晚触点越多）。
2. **P-B 交付"1.20.1 精简版=存储网络+Create 物流对接"可否作为该格首个可玩里程碑？**
   推荐可（Create 承诺最早兑现；画布/机器随 P-C 补齐）。
3. **无对位组件按 §二 降级表处理可否？**（FOOD 退 isEdible、GLINT 不显示，均功能零影响。）

## 修订（m437 落刀时）

- write(空表)=清除 的判空归一**推迟**为独立里程碑（保零行为：原生 set 空组件与 setNbt 空表在
  两代同样"不与裸物品混堆"，语义平行）；显式清除单独成口 clear（对位原 remove）。SPI 实为五口。
- 普查基线"set 形态 33"系 head -5 截断坏尺（实为 52），落刀 regex 逐点计数为准；已入 DEVLOG。
