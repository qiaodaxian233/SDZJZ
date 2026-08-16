# 生电终结者 · 项目交接文档（HANDOVER）

> 给接手的人（或新对话里的 AI）。**新对话开局标准动作**：作者贴仓库地址+一次性 PAT → clone →
> 读本文件 + DEVLOG.md 末尾几节 → 直接接活。不用翻聊天记录。
> 仓库：https://github.com/qiaodaxian233/SDZJZ · Fabric 1.21.1 · Yarn · 纯 Java · 前置仅 Fabric API。

## ⭐ 开发守则（置顶必守）

1. 不猜接口，先查文档；拿不准的照仓库现有用法抄。
2. 不糊里糊涂干活，先把边界问清楚。
3. 不臆想业务，先跟人类对齐并留痕。
4. 不造新接口，先复用已有。
5. 不跳过验证。
6. 不动架构红线。
7. 不装懂，坦白不会。
8. 不盲改，谨慎重构。

**协作风格（作者明确要求，见其 doubaox/对话记忆.md）**：
- 用户反馈是第一信号，不是"待评估输入"。说了就照做，别拿"原则/安全/最佳实践"包装拖延。
- 有担忧先把活干完，末尾一句话提醒即可，**不重复、不展开**。
- 多文件交付走 **git push**，不甩一堆 attachment。
- 别装看过资料；抓不全就说抓不全。

## ⭐⭐ 工作流铁律（血泪换的，m99 之前丢过整轮工作）

1. **做一步推一步**：每完成一个独立改动就 commit + push，绝不在沙箱里攒。沙箱随时重置。
2. PAT 由作者在对话里现贴，**绝不写进任何提交文件**；git 身份沿用仓库既有提交者。
3. 沙箱编不了 Fabric/Mojang 依赖 → 新 API 一律标「待编译验证」，作者本地 IDEA+JDK21 编译，报错贴回逐个修。
   沙箱可 `apt-get update && apt-get install openjdk-21-jdk-headless` 装 javac 做**纯语法冒烟检查**
   （grep "expected|illegal|reached end|not a statement|unclosed"，其余报错都是缺 MC 依赖，正常）。
4. **注册六件套逐项计数断言**（m92b 教训）：新物品 = MachineDef + ModItems(reg+创造栏两处) +
   SuperBenchRecipes/data 配方 + 中英 lang + 模型 json + 贴图 png，改完逐项 grep 验数，JSON 过 json.load。
5. 每里程碑写 DEVLOG（现象→根因→修法→教训），提交信息带 mNN 编号。
6. 升级/封顶类公式改动必问："到顶之后玩家再投入会怎样"——静默无效比数值弱更伤（m99 教训）。
7. **版本号一笔一跳**（m317 作者拍板）：每笔里程碑提交前把 gradle.properties 的
   mod_version 改成 **0.1.<里程碑号>**（热修字母尾号不抬数字段）；CI "版本号对表"闸忘跳即红。

## 核心调度公平性：已收官（m302~m309，实测验证通过）——评审复评 9.4

评审③复评结论（2026-08-07 作者转达）：定性 **anti-starvation scheduler**（防饥饿，非严格
fair-share）正确且够用；**明确不要现在重写 Round Robin**（默认预算极高平时不触顶，89 行状态
简单胜过中央运行队列）；工作量欠账不丢+burst 上限的处理获认可；"下一步应该测，而不是继续
凭感觉重构"。压测矩阵：1/10/50/100 核 × 64/256/512 节点，cap 压到 100 满载跑 5~10 分钟，
判据=无长期零吞吐核心且最低最高吞吐只差几倍。工具链：/sdzjz profile sched（m304）+
GameTest 七八号用例（m305/m309）+ 一键压测 /sdzjz bench（m306~m308）。
**实测终局（2026-08-08 第五份报告）**：100核×64节点+产线核心（101>cap100 最坏形），
零吞吐=0、分布 1.0×、预算账分毫不差——防饥饿+拍龄有界轮转在真实 BE tick 序成立，销账。
过程中抓获并修复：铺场鸡生蛋死锁(m307)/看门狗占空比噪声(m308)/k>cap 恒饿真 bug(m309)。

外部评审文档③唯一方向性建议。m302 把 maxRecipesPerNetworkTick 真接线成全服共享预算
（对表发现原"全服预算"实为 m270 单核心预算，NetworkTick 键此前未接线）+ 饥饿名单保底
（没吃到的核心下 tick 持保底 1 周期先食权，幽灵核心一拍自然过期），默认 1M 不束缚=行为零变化。
**远期候选（要更强公平再立项）**：方案②轮转相位（hash(pos)+serverTick 相位段轮"优先日"，
按需比例公平）+ dirty core 队列（存储/图变更才入队，空闲核心零 CPU）。

## NBT 读写铁律（m353 立/m357 入档，外部审计⑤轮原话：读→view，写→copy，不要为省 GC 把写路也改 view）

- **读**：NodeTags.viewOf（NbtComponent.getNbt 零拷贝内部实包）——绝对只读，DEFAULT 空件全局共享一份，写它=全服中毒。
- **写**：NodeTags.nbtOf 拷贝 → 改 → s.set(CUSTOM_DATA, NbtComponent.of(n)) 三段缺一不可（缺 set=丢写，垃圾桶 tc 死数三百个里程碑没人发现的教训）。

## 多版本代际架构（m361 立项，顾问方案全盘采纳）

- **目标**：Common(业务)/Legacy(1.21.x)/Modern(26.x) 代际结构，双端锚点 1.21.1+26.2；五阶段 P0 地雷图(m361 已交)→P1 建 Common→P2 Legacy 参考实现(行为逐位不变)→P3 26.2→P4 补 1.21.4/5/8/11→P5 发布流水线。
- **地雷图**：docs/PLATFORM_MAP.md（工具 docs/tools_platform_scan.py 可复跑）。A=9文件/843行可直迁；B=93文件/15501行需 SPI；D=20 client；E=6 mixin。RecipeAccess 最便宜(57用点/7文件)先动，SCBE(3517行/732用点)压轴。
- **不要做**：每版本一套源码/长期分支/Common里if(version)/Mixin全版本共用/为兼容重写Planner/巨型Platform接口/26.2 API反向污染Common。
- **积压重定位**：bigStacks/portableVault 升格为 SPI 模块（BigStackService/VaultScreenPlatform，§7/§8），随 Phase 1 落位不再单刀。

## 当前状态（m361：多版本代际架构Phase0地雷图=新docs/tools_platform_scan.py(A~E五类+11 API族全文FQN计数)+docs/PLATFORM_MAP.md,实测A=9/B=93(15501行)/D=20/E=6,B族排行world1195>item922>nbt411>registry278>screen239>text173>network159>gametest93>recipe57(RecipeAccess=最便宜第一刀),SCBE 732用点压轴,Phase1顺位定档,bigStacks/portableVault升格SPI模块,零Java零新键,版本0.1.361）；（m360：craft_chain深链+mixed混布+矩阵工况化(拍板A收官)=每站7×九节点深链(原木→木板→木棍→梯子,双过滤前置chainWants真递归2-3层,级间节点边流转末级回仓),MIXED按站序25%×4轮布siteWl逐站定型+activeCrafters按型累计,idle站豁免防哑账+MIXED专用判据(跨型倍数不适用),矩阵默认MIXED带工况参,命令全五档,零新键,版本0.1.360）；（m359：bench工况系统+craft_fed真产线(作者拍板A第一笔:证明m349/m350/chainWants在真实合成网络兑现)=Workload三档(IDLE零节点基线判据豁免/COBBLE回归/CRAFT_FED=仓预灌1000万木板→32×过滤翻黑名单→合成机工作台真配方→回仓),新WIRE装配相位(known闸须首扫入表故铺完等5t再连三种边,stopNow补WIRE分支),SUB_ACCEPTS新桶+accepts壳,报告新合成机口径行(类型账ns/台·tick+exec+chainWants/plans总+分配摊台),命令第五参工况,旧四参与矩阵零改动,m360接craft_chain+mixed+矩阵工况化,零新键,版本0.1.359）；（m358纯文档：三档矩阵第二轮战果对表(m356三刀实锤:核tick均44.2→34.6µs-22%,通用机器652→479ns-27%,分配-46%至65KB/核·tick三轮连降226→122→65)+两判决(①StorageCore revision聚合缓存数据判不做:聚合段仅0.039µs/核·tick全消也省不出毛;②通用机器479ns含~90ns观测自重真实~390ns,本工况边际收益递减凭经验的刀收手)+剩余榜=待作者拍板:A换工况矩阵(合成机带料/过滤链让供料planner路径吃真负载)或B积压四件,版本0.1.358）；（m357：审计⑤轮五连响应=①②勘误留证(plans/wants/Brew/Ench本就全长期CHM缓存,DemandCache判不做;唯一真缺口=手选wantsOf每拍现建→新wantsOfCached双口径长期缓存),③规划器分桶(SUB_PLANNER混账比审计说的还糟:m349 exec整个挂它名下4509ns是假象,拆SUB_P_EXEC/BREW/ENCH/SMELT四桶+三壳),⑤扫描三段账(发现/聚合/排序分桶为StorageCore revision决策供数),④distributeEven0 ok表→evenOk scratch,⑥读view写copy铁律入档,矩阵对表勘误(0017批次跑在m355构建m356未入账),零新键,版本0.1.357）；（m356：空转路径三刀(三档矩阵数据指认:通用机器94%占比654ns/节点·tick空转)=①Math.pow每节点每tick现算→per-BE速率查表rateOf(失效=gain/mult值快照比对,配置单例原地改identity靠不住,同参同级与pow逐位一致);②workAcc Map<Integer,Double>→double[](拆装箱,每tick每节点Double put是分配账常客,写时扩容索引语义同构);③循环头三次组件查找→一次viewOf三级齐读(K_SPD/K_CNT/K_PAR常量与读者同源),零新键,矩阵战报=核tick均44.2/45.9/46.0µs完美线性+m353战果实锤(74→44.2µs -40%,226→122KB/核·tick),版本0.1.356）；（m355：三档矩阵一键连跑+审计④轮①对源勘误=①InventorySnapshot判不做留证(四路输入每id本就恰一次count+一次withdraw已是快照理论下限,酿造双账早已联合钳制,快照对象化=平添两表分配零访问收益回退m350;统一抽象由m349 StockView承担);②/sdzjz bench matrix=100/300/500×64自动串跑(IDLE相位接力+档间200t冷却回稳,每档捕获MSPT/核tick均µs/GC/分配/类型前三,末档落matrix汇总文件,stop全停),核数硬顶200→500,CoreProfiler新avgCoreTickUs/typeTop3两口,零新键,版本0.1.355）；（m354b热修：计时壳实体distribute0/distributeEven0签名漏改int[]（沙盒javac缺MC类盲区第三次,CI抓获）,教训=签名连锁改必grep同名+0尾缀壳体对；m354：机器类型账+执行计划数组化(外部审计④轮②③双销)=CoreProfiler新八桶(逻辑/合成/酿造/附魔/交易/复制/机器/其他)大循环"上一笔"式计时(continue众多改下一节点头部结账+循环后末笔,PHASES闸内平时零成本)报告直给µs/核·tick前三大类型贡献;planOutT→int[][](null槽=旧Map缺席同义,编译两趟connections原序逐位一致)18处get(i)→[i]直取+六签名连锁int[]化,零新键,版本0.1.354）；（m353：NBT读路免拷贝+垃圾桶丢写修复(作者首份GC账447MB/s顺藤摸瓜)=火源坐实copyNbt每次属性读深拷贝整包(生产大循环每节点每tick十来次),yarn官方mapping核名NbtComponent.getNbt(method_57463=mojmap getUnsafe)免拷贝口,NodeTags新viewOf(铁律绝对只读,DEFAULT共享写=全服中毒)全15读者+3内联+SCBE热读3处+TradeCenter4读者换装,写路17处拷贝→set三段全留,顺藤潜伏bug=垃圾桶tc全库唯一写点改拷贝不回写自组件化即死数→新addTrashCount三段修复,零新键,GameTest卅三号四断言,版本0.1.353）；（m352：节点双侧进出口+升级计数进格(作者截图两连点)=卡左右两缘各一对进出柱(出上进下柱心NH/2∓7,m184两侧智能选缘终于有柱可贴),八处锚点消费面分高换装(边/双预览/仓线机器端/双判定,进口起手升级为随鼠标选缘,判定纵向容差min(pR,6)防上下柱抢点,落点整卡矩形不动),升级计数改格内右下角原版堆叠数样式(阴影+z抬200,邻格永远盖不住,fmtNum防四位溢),nodeDualSidePorts默认开v46关=回m341/m342单侧(swap键仅关时生效),客户端纯渲染无GameTest面判官=实机,版本0.1.352）；（m351：GC/分配压测账(外部审计③轮四件套收官)=新debug.GcAccount快照类(GC次数/停顿=全JVM收集器求和,分配字节=当前线程走com.sun.management.ThreadMXBean,非HotSpot/被禁优雅降级allocOk位随行),BenchRunner铺场完成起账/writeReport结账出行(测窗秒/GC次/停顿ms占窗%/服务器线程MB+MB每秒+KB每tick+口径注释行防误读),两快照同在END_SERVER_TICK=同线程可比,零新键(bench纯报告增强m306先例),GameTest卅二号(32MB冷代码真分配,单调不减+allocOk稳定+HotSpot下分配差≥8MB),版本0.1.351）；（m350：供料热路径零/低分配(外部审计③轮②销账)=每5t清运/泵料十处ArrayList(entrySet/keySet)防御拷贝下岗,BE挂grow-only双数组scratch(fillDrain,m218d同族,不跨节点不可重入),三口径=整锅转存再清(六处)/转存不清残量原样(抽取在岗)/只借键值快照+withdraw当场实扣绝不虚记账(泵/熔炉,聚合视图陈旧红线),crafterNeeds外层表复用(值集=planner共享缓存严禁clear),StorageCore FTA iterator撤键拷贝(views建完才外泄),精确支路拷贝刻意保留(withdrawExact当场删模板=必需品),零新键无GameTest(机械变换判官=既有31用例+实机),版本0.1.350）；（m349：CraftExecutionPlan+StockSnapshot(外部审计③轮①③销账)=合成机"重复算三遍"(pick逐候选maxCrafts+中选再全量+takeFor再建表,各自逐id回调存储)合并单趟exec→Exec(plan,crafts,taken,remainders),快照物化全候选去重id各查存储恰一次后全程内存算,StockView命名接口防将来聚合实现被放大成网络级访问,选配方/封顶/贪心序/手选/合计/残留口径逐点同旧,公共三口保留(测试面+回退),SCBE双路换装,零新键,GameTest卅一号五断言含每id恰查一次计数器判官,版本0.1.349）；（m348：停机核心降频(外部审计P1销账)=①端点扫描分档40t→停机+无观众200t慢拍保底(200%40=0日历拍无缝),toggleRunning停→开与addCanvasViewer两转变沿哨兵强刷陈旧窗清零反而比旧版快;②m115看门狗销"ticks冻结在%20==0每tick扫实体"坑改running&&日历拍,配置coreIdleScanRelief默认开v45,GameTest三十号四契约+观测口endpointScanPending,版本0.1.348）；（m347：孤儿强加载声明渐进核销(外部审计销账)=restoreClaims开服重发票不验核心还在,核心消失于区块未加载态即声明成孤儿永久钉死区块,修=每维度每200t声明表对运行时FORCED引用计数,零登记连续三击(≥30s)才撤票删声明出声,开服600t宽限+活核心≤20t边沿重登记(m296既有)误杀追不上且误销自愈,清态并clearAll,配置chunkClaimReconcile默认开v44,GameTest廿九号六断言,顺手勘误=审计DataPanel重复解析条已有m108c等效缓存改分拣,版本0.1.347）；（m346：万能熔炼表稳定选序(外部审计销账)=SmeltPlanner同输入多配方putIfAbsent先到先得随RecipeManager遍历序掷骰(原版无同输入重复配方故原版逐字节不变,数据包下重启间产物漂),build改两趟收全候选+稳定选序(minecraft排前+配方id字典序=m234同口径)抽pickStable纯函数直测,resultOf签名/缓存形制不动四消费点零改,零新配置键,GameTest廿八号六断言,按库存挑输出仍待拍板,版本0.1.346）；（m345：外部审计②余账对表登记(纯文档)=P0/P1两笔已销(m343/m344),其余逐条分拣进待办池第0条(属实11项+已有等效5项注出处防重做,机制向三项标待拍板),审计"sleepWhenIdle"系笔误已勘,版本0.1.345）；（m344：画布观众登记表(外部审计P1销账)=flushCanvasSnapshot/m89端点包/hasCanvasViewer三处此前每tick扫全服玩家表(没人看也扫,100核×50人=5000谓词/tick),改handler开屏挂号+onClosed销号(照DataPanel先例),查表逐人仍过原谓词失配即销号自愈,无观众零成本早退,销号顺清该人snapshotSent重开必得首包,O(玩家×核心)→O(观众),零新配置键(m279先例),GameTest廿七号四断言,版本0.1.344）；（m343：合成机槽位替代材料(外部审计P0销账)=CraftPlanner原对每Ingredient只取matching[0]("任意木板"拍死成橡木板,仓里云杉看得见吃不着且经pick/wants/chainWants/accepts全链传导),Plan增groups候选组为计数/扣料唯一权威(组内按原版候选序贪心取用,跨组共享候选虚拟扣减不重复计数,无共享=精确/有共享=可行性二分),四新口maxCrafts/takeFor(返实际消耗)/remaindersOf(残留按真消耗物结算)/firstMissing,m235手选口同改,wants双口径分缓存,GameTest廿六号七断言,配置craftIngredientAlternatives默认开v43,版本0.1.343）；（m342：进口起手拉线+进出字标(作者点名)=进口柱成起手点(拖仓卡=供料线mode1/拖机器=反向NodeLink对方出→我进,预览进线色锚随口位,命中插卡体拖动前),病根=进口本无拖线行为点它就整卡拖走,进/出单字标画柱旁卡外随口位,零新协议纯客户端,版本0.1.342）；（m341：节点进出口互换(作者点名)=接线柱左出右进+拖线命中同源随开关,nodePortsSwapped默认开v42纯客户端零协议(配置键已随m340先行入库)）；（m340：连线喂料改"连线优先+显式供料线补足"(作者两截图实锤猪人塔+合成机对,m339修的是经验机对没打中)=五处hasIn二选一是根因(接线即把仓当空气),topUpSource/dualCount/dualWithdraw三口五机一刀(合成/酿造含燃料/附魔/交易/通用耗料)熔炉族刻意除外防误烧,隐式网络永不自动补,机器组合.md第9条立新语义,supplyTopUp v41,版本0.1.341）；（m339：经验池公平层(作者实锤两台拉满第二台饿死)=根因非预算(四键默认天文)而是吃经验机器同核节点间裸先到先得,m302方案①下沉节点层=xpGate过账+xpStarved记名+下拍全池礼让+吃上销名+名单每拍保洁,裁决抽xpFairDecide纯函数廿五号六断言直测,顺手补预算剪零亮黄(m99静默账,hadWork位区分没攒够周期),配置xpFairShare v40,返工留痕=中文文案嵌半角引号切串冒烟当场抓,版本0.1.339）；（m338：超级工作台材料总览卡(作者截图点名+24…显示不全)="+N…"升级可点"+N▼"(悬停ACCENT)→盖右栏总览卡(不压槽位)原尺寸网格自适应列宽+滚轮翻行双端夹+迷你滚动条+悬停页脚精确数(补m244缩写口),任意点/Esc/换台收起且清滚,热区渲染缓存m215同源,零新色全走SciSkin,版本0.1.338）；（m337：批量图标动画化63张(作者点名"好做的都动")=掩码占比1%~22%判好做(HSV S>0.45 V>0.55),m336管线复用底图静止+掩码0.78~1.12呼吸6帧,frametime=4+hash%3错拍不齐步闪,<1%的41张不硬凑>22%的5张不闪眼,SPRITES 10→73计数断言+CI mcmeta闸13→76,单张回滚口入档,版本0.1.337）；（m336：复制机图标换装(作者供图黑曜青辉方核)=alpha裁边+4%边距+BOX降采样128+青辉光掩码4.2%六帧0.78~1.12呼吸,尺寸mcmeta模型SPRITES闸全原位复用纯贴图替换,版本0.1.336）；（m335：界面打磨(作者明令GitHub搜着学不搬代码)=选择器查询语法(学JEI用户语法@模组/-排除/|并联,PickerQuery纯函数自写且沙箱直跑真值表全过,pickerHit统一命中口+名字id懒缓存平m107a账+满页计总数+页脚三态含悬停完整id,配置pickerQuerySyntax v39)+交易所等级经验条(学原版村民屏形态,SciSkin配色200×3线性填充)+锁定行红×(原版锁定叉),GameTest廿四号十二断言真值表,版本0.1.335）；（m334：无限复制机(机器数94→95,作者点名配方超难+复制一切+特效界面)=目标全物品注册表网格选(骑mode0+srcOverride=allItems零新形态),母本压阵不消耗+每件烧经验池duplicatorXpPerItem默认20,组件不复制(机器组合.md第9条),配方全表最贵8星+8合金块+双信标+重核等103位Ⅲ档且廿三号挂超难回归闸(≥100件防降价),接线五件=tick独立分支/accepts恒假(经验不走线)/setNodeTarget=validTarget唯一口径/徽章并通用路/chainWants显式零需求,特效6帧脉冲贴图128×768漂移恒0+SPRITES 9→10+CI mcmeta闸12→13,配置v38两键(enabled熔断阀+XpPerItem),待拍板=经验价经济手感+作者GitHub界面参考链接断了待补全,版本0.1.334）；（m333：交易所等级系统(作者点名"村民不升级兑换不提升"=升级从未实现)=合同加lv/xp两键新手→大师,门槛原版10/70/150/250,单笔经验2+2×交易等级×倍率,Trade加minLevel逐条标级,表尾追加五条新货(农民南瓜2西瓜3金胡萝卜5/工具匠收钻石4)图书管理员十书分档(顶书大师主力专家),trade()服务端等级闸+升级播报,界面头行等级经验进度+锁定行灰显标解锁级,两红线=旧合同无lv键按大师接管不没收+交易表序号锚定只许尾追(交易机目标串职业|序号防漂移)且交易机不受等级闸(机器组合.md第8条),配置tradeLeveling/tradeXpMultiplier v37,GameTest廿二号=门槛/封顶/接管/三锚序号,版本0.1.333）；（m332：随身仓库专属仓位=原版背包屏mixin追加47格(下标46副手上方,只收仓库格上限1),账面PersistentState按UUID挂主世界(m296刀法,死亡不掉换维度跟人),客户端吃playerScreenHandler恒广播白捡同步,兼容四件=吸附抽magnetTick静态走END_SERVER_TICK钩(仓位无inventoryTick)/仓位右键onClicked服务端开屏/vault()兜底stackOf取物屏全通/quickMove兜底else白捡shift回背包,mixin两枚(PlayerScreenHandler<init>TAIL加槽+InventoryScreen drawBackground画槽框,五名yarn核到,靶点覆写与否CI现形),配置portableVaultSlot默认开v36需双端一致,边界立档=创造背包页重排落位/onClicked开屏时序待实机,GameTest廿一号=账面往返30亿+准入三断言,版本0.1.332）；（m331：对接文档对齐现状(作者粘贴版漂移且内嵌PAT已提醒作废,仓库版停在m175口径)=铁律1并入m317版本一笔一跳/铁律2补"粘贴副本也不嵌PAT"/铁律5改CI已启用m258+构建报告专扫warning段m328+闸红即停m291b+报告分支m310b/踩坑四补(共享Inventory禁原地改栈m326b·事务作用域禁手账m323m327·冒烟盲区家族m257m271m288m328·动画物品登SPRITES表m320m330)/文件地图补CoreScheduler·GameTest·ci.yml·任务看板四行并更新profile子命令/任务看板节勘误(实际停更m218前后,恢复前事实源=HANDOVER+DEVLOG,去留待作者拍板),纯文档零Java版本0.1.331）；（m330：五台规划器机器节点帧动画(作者点名"自动合成机那些")=合成机三行扫描/种植机上下反相呼吸生长/酿造塔冷色反相+炉口2倍频/附魔工厂屏符文反相+灯闪/交易机行情走灯+绿宝石闪,16px断言逼出三轮返工(病根=掩码稀少被整轮廓平均稀释,药=反相块+量化放宽谓词+抬幅,终稿0.0124~0.0455全过漂移全0),SodiumSpriteKicker SPRITES 4→9(m320立档首兑现),CI jar mcmeta断言7→12,画布drawItem精灵路径核过无直贴,样张人工过目五件清晰）；（m329：全量BUG审计(作者点名,教训目录当模式清单横扫)=3修:打折机红灯文案"附魔金苹果"勘误(实取普通金苹果,玩家会肝错料)/VaultTake接收器补m269写包预算(16接收器唯一漏点,取物回包=放大器)/画布属性case1,3,4,5补16位饱和Math.min(32767)(m106族,bigStacks后machineCount可破短通道);2立档:withdraw int形参>2^31理论边界(上游预算封顶现实到不了)/交易所附魔书直发背包vs交易机进精确账本双轨待作者拍板(trade()过时注释已勘正);12面扫过干净免重扫(修订号6/6+8/8,C2S其余15接收器,TradePlanner防御,vault take,cyclesThisTick四闸次序,落盘链等,详DEVLOG)）；（m328：mock玩家API迁移=作者构建报告8处[removal]警告清账(createMockCreativeServerPlayerInWorld→createMockPlayer(SURVIVAL),用法逐点核过玩家对象只调getInventory其余全按PlayerEntity形参传,行为等价),新教训=冒烟盲区#6弃用标注沙箱不可见(缺MC类时@Deprecated与yarn都看不见,作者构建报告是唯一弃用尺,新MC API落地后首份报告专扫warning段),下次构建报告应零警告=最终销账）；（m327：事务作用域手账审计尺=m323规矩(事务内禁withdraw/deposit系,同键前像覆盖手账=复制窗,异键存活是m278性质但静态不可判故一刀切,混部范式=先commit再手账)配CI第十一闸tools_tx_scope_audit.py(try块花括号配平+剥注释+tx手账豁免标记+gametest按档排除),三重自证=内置坏样本/真文件投毒必红/复原绿,人工审计=生产代码唯一事务块(m231 insertInto)干净零违规,尺子价值在防将来）；（m326b热修：共享网格幻影结果格=consumeCraft原地decrement/increment不触发markDirty→其他观者监听器不响,B结果格滞留幻影且可点(takeStack先到手+空网格不扣料)=窄复制窗,修=尾部craft.markDirty通知全体观者(旧updateCraftResult只刷自己的超集,零递归),教训=共享Inventory禁原地改栈——CI二十用例第一跑抓获,测试立刻回本）；（m326：端到端GameTest第二批=三用例(十八~二十号,评审清单#3/#4/#5):共享3×3网格双handler(A摆B见/结果格各算/无补料恰一轮/扣料双方跟清=m300语义判官)/面板被拆旧handler(真绑定useOnBlock原路+远程屏免距离判,canUse立假=m299三判,迟到包完整repage不抛)/终端钥匙四拍(持=真/离身=假/光标栈算身上=m303明文首判官/丢弃=假)——E2E十条8条有判官,#6重启框架内做不了留实机口径,#10 bigStacks链归第五优先兼容矩阵挂待办）；（m325：构建链收口(评审第八,非门控评审项清完)=Loom锁1.7.4(锚点=作者2026-08-01本地全绿实测解析版,SNAPSHOT漂移归零)+fabric-api依赖收窄"*"→">=0.105.0"(对齐编译锁0.105.0+1.21.1)+Actions四家升主版(checkout v7/setup-java v5/gradle-actions v6/upload-artifact v7,目标tag经api逐一核实+传参全查无被删入参),CI自身被改故run判决=三job全绿且Loom 1.7.4真解析出包）；（m324：maxRecipesPerChunkTick真接线(评审第六优先,销服主"调了没生效"陷阱)=四层封顶节点cap→核内→区块→全服,CoreScheduler尾部区块账(维度→ChunkPos.toLong→已耗,独立时钟chunkTickStamp防全服闸关时不换拍,clearAll清态)+挂钩两处防坏账(全服申请前按区块余量钳=区块封死不占全服饥饿名单,全服终裁后按实批记区块账=不虚耗),区块层无公平名单立档(同区块BE tick序竞争,管理员钝闸定位),默认262144极高=行为零变化,零新键v35不动,GameTest十七号用例=同账/异账/闸关/记满/换拍复位,机器组合.md预算段同步）；（m323：端到端GameTest第一批=四用例上真链路(评审第四优先,十三~十六号):双ServerPlayer双handler抢末组(m266复制窗handler级判官,实收和=64账本清零)/双人异词搜索互不覆盖(m292回归+m322后"共用master≠共用过滤")/4096类型+精确组件件+30亿long计数存档往返全量对账(createNbt→新BE.read同存档链路)/事务窗内异键手账abort后存活(m278增量undo核心性质首判官)——同键混部=前像覆盖属复制窗立规矩"事务作用域内禁调手账口"挂待办审计,剩余E2E清单(共享网格并发/面板拆除旧handler/终端换手/区块票重启/bigStacks链)挂待办第二批,mock玩家两API无在树先例CI当判官）；（m322：终端主快照缓存=masterEntries加(普通+精确修订号和,核心数)三元指纹缓存(m218同工艺)多观众共用一次全仓聚合+排序(m83比较器搬BE成MASTER_ORDER,handler撤本地排序=稳定排序+子序列筛选逐元素同序)+StorageCore补exactRev(8触点:depositExact两路/withdrawExact/FTA insert两路/FTA extract/回滚宁可多记/readNbt,根因=storeRev只罩普通账本精确件变动此前零修订号)+aggregate拆refreshMeta命中也刷(xpBank不进修订号防经验读数冻)+末观众释放快照,配置panelMasterSnapshotCache v35,GameTest十二号用例=命中同引用/只动精确账本必失效/预排序断言——评审到档归因补正:第一优先=m321已落地规格对上,第二优先Demand Cache评审自行门控在bench实测后,故本笔做第三优先;bench三档矩阵(logic/storage/viewer-heavy)等作者实机跑）；（m321：CoreProfiler阶段计时=tickInner六锚点四大阶段常开(维护同步/区块票/逻辑供料/生产分发,分母=record既有nanos零新采样)+六项细分PHASES门控(chainWants深度0计/端点扫描/distribute/deposit/存储解析/CraftPlanner.plans,改名*0加同名壳调用点零改动)+出口=/sdzjz profile phase[on|off]与bench报告Top Hotspots段(自动开关复原)——立足点=m305尾账profiler细账缺口,作者本轮贴档为空待重贴已诚实留痕）；（m320b：垫片四态诊断日志(挂接式样/未装/API变脸/熔断原因,日志搜SodiumSpriteKicker一行远程判定)+对照组勘误(奇点族图标动效=Avaritia自带渲染层光环非mcmeta动画,不构成对照)+格式嫌疑再排(会动方块条带vs不动物品条带逐项同=8位RGBA非隔行mcmeta一字不差)+决定性实验立档(拆光视野内三方块看其物品图标是否也冻)）；（m320：Sodium物品动画精灵保活=新client/SodiumSpriteKicker(m229反射软兼容刀法零编译依赖)每客户端tick经SpriteUtil.markSpriteActive给四件动画物品精灵标活跃(0.6=INSTANCE/0.5=静态两式自适应,未装Sodium一次熔断零开销)——根因定案=Sodium"仅动画可见纹理"只保区块渲染器标活跃的精灵,方块靠世界渲染保活会动而纯GUI物品精灵冻第0帧(作者二分实锤),取用链yarn三名核到+BLOCK_ATLAS在树先例,配置sodiumIconAnimFix v34,反射类名待实机验证）；（m319：图标动画二轮返工=并发三支路整块轮亮/速度箭头整体双拍+齿环亮弧旋转(幅度全面加大)+核心模块换回作者原图(m141归位入anim_base,模型改回generated,Blockbench版+调色板贴图退役残留检0,新动画=芯心跳双峰+端口反相+电路半流+金针齐闪)+16px可读性断言(坏样本自证未抓住=行波抵消假说被推翻,降级防呆底线,真根因待实机二分:数量升级动不动)+CI新步骤jar内mcmeta≥7排除打包缺失）；（m318：构建产物旧版清扫=build.gradle新purgeStaleJars(Delete)挂build.finalizedBy自动删build/libs里不含当前版本号的jar——根因=m317一笔一跳后Gradle不删旧版产物,作者工具按文件名从libs选产物0.0.1字典序捡走(实锤:pull最新+全UP-TO-DATE+进mods却是0.0.1),三反证收口=检出最新/工作区若旧则processResources不可能UP-TO-DATE/故新jar必已存在;CI Gradle job当DSL真判官）；（m317：版本号方案=mod_version 0.1.<里程碑号>一笔一跳(0.0.1→0.1.317,接线链gradle.properties→build.gradle→fabric.mod.json核通)+防忘跳回归尺tools_version_check挂CI新闸(断言=DEVLOG最大mNNN对表)+双模组名调查留痕(背包两行"生电终结者"=客户端JEI+REI各追加fabric.mod.json的name一遍,m236同款结论非我方bug,自家唯一追加点=DY水印恰一次,用户侧REI关追加模组名或JEI清modNameFormat即回单行)）；（m316：画布右键菜单层级穿透修复=renderMenu补抬z400(m202/m283同病同刀,五浮层z口径对齐,根因=节点drawItem在z100~200带深度而菜单z0漏网)+图标文字间距(贴图后+20/物品后+18原+16零间隙)+MENU_W 136→144容最长行,16处消费点同源常量命中渲染零漂移）；（m315：随身仓库屏底部提示重叠修复(根因=两行提示同落y+126,PINV_Y 138→150屏高224→236分行)+搜索框(m216去黑壳自绘底格+聚焦描边,名称/id/拼音首字母三通道m282白捡,纯客户端过滤零新协议,聚焦截键盘E不关屏Esc放行,名称缓存≤256条)）；（m314：四件物品贴图帧动画=并发/数量/速度升级+核心模块照m277刀法(tools_item_anim.py尺寸参数化,发光像素调制底盘逐位不动,anim_base唯一源)——并发=枝干上流+端球错相/数量=三块轮亮9帧3拍/速度=箭头上冲+齿环角向流光/核心模块=Blockbench调色格按明度分层心跳+橙灯快闪,审计闸放行竖条口径(高=宽整数倍+mcmeta),core_module.png参考图零消费未动）；（m313：画布快捷键(悬停节点P暂停/X断线/Del取出/V主选择器分派/F2改组名/Shift+G解散组,与G打组无冲突各modal截前,帮助卡加两行)+用户8张菜单按钮图标归位(32²贴图,menuTexs平行表贴图优先,七处换装物品图标机制保留)）；（m312：随身仓库取物屏=右键开屏,零S2C(账本随手上包组件天然同步客户端直读),唯一新包C2S VaultTakePayload走Bounded,5行滚动列表三式取物(左键一组64/右键拿满一格=大堆叠下2^30/Shift取尽填背包),背包Shift点物品整叠入账,只扣实收余量永留账本,包离手关屏;m311d=用户立绘归位+tooltip瘦身撤明细+小说系统入立项候选只记不做留联动）；（m311：随身仓库=物品自带long账本(组件存,换手跟走,int墙碰不到,倾倒按≤10亿/笔切块入仓)+吸附模式(每0.5s吸掉落物直接入账尊重拾取延迟,叮系统味提示)+四式交互(潜行右键开关/右键报账/背包右键点包收纳/右键核心或面板整包入仓)+护栏(类型上限256只闸新类型/饱和加法/组件件拒收),六件套全断言过,GameTest十号用例=30亿跨int边界倾倒对账,程序占位贴图挂绘图名单,配置v33;GUI选取式取物=m312候选）；（m310：原生大堆叠替代ItemStackProMax=本模组首批mixin三件(ItemStack存档Codec钳位1..99放宽到2^30/getMaxCount配置抬顶只动可堆叠物/Slot无参getMaxItemCount抬格上限)+客户端计数>9999画K M B简写(m232口径),配置bigStacks·bigStackMax=2^30两键v32(int天花板2147483647翻不过且顶格合并a+b溢出吃物品故取2^30,更大量级=压缩包×4096或仓储long账本),弃案=组件codec的mixin靶点在lambda咬不中且机制不需要已删,GameTest九号用例当mixin真裁判——run#55红裁判咬中lambda陷阱,m310b失败报告回推ci-gametest-report分支破blob盲区,m310c通配靶点修正后run#57九用例全绿）；（m309：调度器k>cap恒饿修复=作者100×64+产线核心(恰101>cap100)实测抓获tick序末核2400拍颗粒无收,两层根因=逐请求记名违背'有进展不记'注释致全员每拍重列+先食权无资历人人持有等于没有;修=FED本拍进食集+名单值升拍龄+资历闸(先保更饿者否则让贤拍龄+1,连续挨饿有界≤O(k/cap));算法尺稳态窗口口径+旧语义对照复现恒饿+GameTest八号用例(105核cap100分batch串行)）；（m308：压测报告识别m115看门狗噪声=黄灯占空比直测(SCBE.lagPausedNow每20拍全站采样,100×512实测3051×实为占空比噪声非序偏置=成组账目逐字节相同/最低核6000拍只申请65拍为指纹)+判据四档重写修'3051×嘴硬达标'bug(占空比>0倍数判据无效仅防饥饿有效,占空比0才按≤10×真判,>10×=偏斜超阈提示方案②)；100×512=5.1万节点已是单机20TPS产能边界属容量发现）；（m307：压测三修=铺场首票自举(核心自持票在自身tick里注册,模拟距离外永不tick=鸡生蛋,spawnSite代发CoreChunkLoading.force幂等+清场补release兜底)+报告防哑账(铺场清单对上账缺席点名即不达标,首轮14/20沉默核心曾骗出达标)+忙时MSPT真值(原版tickTimes环形,墙钟49.9ms实为健康脉搏非负载)+非压测核心消费量化；首轮有效结论=防饥饿保底1周期/拍实测成立零吞吐0）；（m306：一键压测/sdzjz bench start[核][节点][秒][cap]|stop=自动铺场(核心+存储+刷石机满载,64格跨区块)→自测tick耗时P95→报告落游戏目录sdzjz_bench_时间戳.txt(判据行只算压测核心)→自动清场(benchClearNodes不散落)复原配置,IDLE零开销,中途停服按日志坐标手清已立档）；（m305：GameTest七号用例=调度器防饥饿soak(100核固定序×cap100×120拍,断预算硬顶+min≥30防饥饿保底,比例公平属实机口径不断),首跑见CI回填）；（m304：调度器观测账=/sdzjz profile sched(cap/上拍消费/名单数+granted最低中位最高+判据直出零吞吐标红/倍数达标,最低3最高3明细),reset只清计数不动名单,评审复评9.4通过'下一步是测'响应第一件）；（m303：AccessMode=开屏方式进handler构造链(remote位,旧三参委托零调用点改动),远程屏验钥匙=身上仍持绑定本面板的终端(含光标栈)否则关屏,方块屏恢复原版触达语义(同维度+canInteractWithBlockAt4.0——m299曾为远程一刀切撤距离判),TerminalItem.isBoundTo唯一判定出口,m299立档余账收官零新配置键）；（m302：全服生产预算真接线maxRecipesPerNetworkTick(此前未接线,m270实为单核预算)+饥饿名单保底1周期/拍(到场即出名单幽灵一拍过期,两处挂钩无中央循环,machine/CoreScheduler),默认1M不束缚零新配置键,算法尺60种子四不变量挂CI第十闸,评审③方案①销账,CI run#39三job全绿首次真编译即通）；（m301：GameTest挂CI第九闸=独立job真跑runGametest六用例+junit报告always上传+40min超时,纯CI改动零游戏代码,首跑run#37全绿junit报告已产出,调度公平性前置已解锁,计数=6待作者看一眼日志）；（m300：共享网格=公共工作台语义立档四条+换轨WARN带坐标+两处陈注释对齐）；（m299：canUse=存活三判(removed/世界空/服务端实例不符即关屏),跨维度远程故无距离判,绑定有效性另立AccessMode里程碑）；（m298：配方书摘要=原料筛(CRAFTING ingredient全集静态缓存按RecipeManager失配重建)+truncated位+书旁小字"缺席≠0",指纹改过滤后口径）；（m297：GameTest六用例=取尽不变量/事务回滚普通+嵌套精确/超长包解码期拒收/类型硬顶/精确索引平移,fabric-gametest入口+gradlew runGametest配好,首跑结果待实机）；（m296：强加载换轨=核心区块弃原版forced改自定义无期票sdzjz_core(radius2=31级同/forceload零回退)+每维度PersistentState声明表开服LOAD重发票自举,与管理员/forceload两通道互不相干误伤根除,m268 EXTERNAL退役,旧档chunkOwned首登记撤旧旗换轨(一次性),签名全保调用方零改,PersistentState.Type三参null待编译验证）；（m295：精确账本旁挂transient ItemVariant→下标索引(列表仍唯一权威undo/存档零改动,查O(1)/删只平移整数,回滚与读档置脏懒重建),五处线性扫全换装,5万步双实现模拟对账全等）；（m294：写包预算表下线remove+停服clear）；（m293：类型绝对安全上限=新typeGate只换四个插入闸(默认8192,≤0关,展示口径不动照显无限,旧超限存档不裁账),配置v31）；（m292：终端视图迁handler=BE只供masterEntries快照(DispEnt升public,m112保险丝原样),每handler自持搜索/滚动/matchedIds/filteredCount+54格SimpleInventory各自过滤排序分页,节流改玩家级(脏≥2t即刷+10t节拍兜机器侧),BE的display/setView/refreshDisplay/tick刷新链退休,接收器路由currentScreenHandler,双人同面板互不覆盖）；（m291b：更正=尺剥注释再扫治误报,NodeGroup陈旧注释更新,CI第八闸补挂真绿,立教训"闸红即停禁预写账"）；（m291：C2S有界Codec=新net/Bounded三件解码期越界即DecoderException拒收(预分配按夹紧值),换装7包(panel_view搜索128匹配256×128/node_add·filter·sensor128/target256/group名64成员4096/storage_link维度256)上限对齐业务sanitize双层防御,回归尺tools_bounded_codec_check挂CI第八闸）；（m290：全量体检=修2真BUG(m289每秒裸BFS改蹭panel.aggregate缓存链路/spinDeg除零夹紧防%0崩渲染线程)+七闸冒烟矩阵配平残留全绿+12项人工复审清白+2边界立档(GROUND光照组细微差/书钮丢Tab可达)）；（m289：配方书可合成计入仓储=新S2C TerminalStockPayload(syncId+ids/counts并行表,前2048种/计数封顶9999),服务端sendContentUpdates每秒序无关指纹变了才发,客户端按syncId灌handler.applyStock+onStockSync催recipeBook.refresh(书update只认背包changeCount不催陈灰),populateRecipeFinder加喂addInput,精确件不入摘要与jeiFill口径一致,配置terminalBookStock v30,实机复核refresh是否重建输入）；（m288：修m286编译红=@Override被楔到私有toggleBook头上(str_replace锚没带注解行),归位refreshRecipeBook;冒烟盲区#4立档=超类不可解析时javac跳过@Override校验,新回归尺tools_override_check.py挂CI第七道闸对坏样本自证能抓）；（m287：超级合成台机器列表按名称排序(借m282字库拼音字母序,view只动显示序填料协议不变)+BOM重设计(名称排序+整块pose缩放0.62+列宽自适应治计数糊邻列+行数按BTN_Y硬夹不淹压缩两钮,5行×6列30格守卫者农场全显,溢出画+N)）；（m286：书钮主题化=SciSkin.termBtn自绘"配方"钮右缘对齐卡内容右缘x+346纵向落标题行,开书主紫态,画在书后命中在书前(窄屏开书可关),开合抽toggleBook,原版TexturedButtonWidget退场）；（m285：扁平内容物扫光=展示栈开原版ENCHANTMENT_GLINT_OVERRIDE按alpha裁形全模式同效,只给!hasDepth,配置compressedPackFlatSheen v29,组件名同律高置信待编译复核）；（m284：手持不居中修复=非GUI整组统一套边框display变换(getTransformation按模式apply左手镜像)内外NONE嵌套同锚必居中,内容物缩0.5旋转半径0.354<内孔0.375(PIL实测2px border)不进环带,GUI老路不动,yarn三API核到）；（m283：熔炉族选烧候选=仓库现有(m163b同刀:busIds∩可熔炼输入按存量序,无同步回退全表,空仓=空网格+标题说明,已选照m116置顶)+选择器整体抬z=400治画布节点物品穿透(m202同病同刀,零早退push/pop配对,全模式同治)）；（m282：终端搜索拼音首字母——新client/PinyinInitials零依赖纯客户端(一级字库经典区位边界法+二级3008字pypinyin离线硬表治"燧石/鹦鹉"漏字,ASCII取词首字母,无GBK运行时静默降级);matchByLocalName并建initIndex随语言探针重建,纯字母查询开通道与子串取并集,走既有matchedIds管道零服务端改动;回归尺tools_pinyin_check.py三断言+沙箱javac真跑6例全过;配置terminalSearchInitials v28）；（m281：存储终端接原版配方书——机制=RecipeBookWidget点配方发CraftRequestC2SPacket由服务端调fillInputSlots,m201已继承AbstractRecipeScreenHandler故只补屏端三件(RecipeBookProvider接口/绿书钮BUTTON_TEXTURES/render・click・tick・出界四路转发全按原版CraftingScreen刀法,开书findLeftEdge挪窗搜索框跟走,narrow阈值563同比例换算);服务端覆写fillInputSlots换m212 jeiFill=仓储优先取料背包兜底,缺料回发CraftFailedResponseS2CPacket画ghost;边界=书内可合成筛选只见背包+网格属预期挂待办;配置terminalRecipeBook v27;yarn全核名,fillInputSlots形参RecipeEntry<?>待编译验证）；（m280：压缩包内容物自转——m243动态图标的内容物renderItem前叠POSITIVE_Y匀速旋转(Util.getMeasuringTimeMs时间源按整圈周期取模,display变换之外=GUI绕屏幕竖轴展台式/手持绕自身竖轴各模式姿态天然正确);只转hasDepth的3D方块模型,扁平物品与边框恒静止;GUI深度账复核0.354<0.4不穿框;配置compressedPackSpinDegPerSec=45可调0=关,v26）；（m279：存储核心空间索引(审计第二批性能项②收官)——CORES平面表旁挂64格桶索引(x>>6,z>>6分桶,packBuckets单一出口,register/unregister/clearAll单一漏斗双写同源,空桶回收,幽灵剔除自动双清);新查询口coresNear=AABB覆盖桶粗筛+调用方d2精筛口径与旧全表逐点一致,巨range/稀桶兜底回全表;nearestWirelessPanel与端点扫描无线分支两处改接,卫星四处coresIn(全维全核语义)不动;O(全核心)/查询→O(覆盖桶内核心);算法尺tools_core_spatial_sim.py入库30种子×4000步逐点相等,冒烟真错0+四符号定向检调用点零命中,零新配置键路由语义零变化;第二批性能项两项全收官）；（m278：FabricLedger增量事务日志(审计第二批性能项①)——FTA事务快照由整本浅拷(每带写事务拷store全表+精确双列表,管道每口每tick开事务=大仓库拷贝风暴)换增量undo日志:快照=位点,回滚=逆序重放undo(普通账本记键前值/精确账本set记下标前值/add撤尾/remove原位插回,逆序保证下标前像对得上),嵌套=每层记各自位点,日志不变量事务外恒空,O(全账本)→O(触碰条目);附带更稳=非事务手账改动不再被整本回滚冲掉;算法尺tools_ledger_journal_sim.py入库=vs整本深拷参照同流40种子×600事务逐事务相等,冒烟真错0+undoJournal定向检0,零新配置键语义零变化;第二批性能项②存储核心扫描空间索引接续）；（m277：三块方块动画二轮返工——m249/m250全息BER三件套退役(摘3注册/删3渲染器/删holo三贴图残留检0,SuperBench零数据BE暂留防旧档孤儿告警退役挂待办),改原版.png.mcmeta贴图帧动画=docs/tools_block_anim.py程序化生成(发光像素HSV分类逐帧只调亮度过冲泛白,底盘逐位不动断言,基础美术入库docs/anim_base唯一源可复跑);三套语义各2s循环=结构核心能量波径向外扩/数据面板连通域切屏错相闪刷+下扫刷新带/工作台紫晶心跳双峰+金线曼哈顿流光;白捡修复=旧档工作台不重放也有动画(贴图动画不吃BE),物品图标同步动属预期;原定m277同步对表落账顺延为m278待作者实机数据）；（m276：区块初始同步瘦身+m89双轨核对(审计第3条方案A第二刀)——toInitialChunkDataNbt改调writeRenderNbt,路过玩家(含vanilla BlockEntityUpdateS2CPacket.create默认取数)从存档级全量降到渲染子集,客户端readNbt缺键全容忍语义零变化;200t周期兜底注释随刀(行为m275已接管=标脏→快照自愈);m89双轨核对结论=两轨落点不同不打架(m89写Screen静态缓存读取口优先,快照写客户端BE后备,数据同源),m265回声机制依赖40t拍退役需重做回声故双轨保留,退役挂待办池待实机验证后再议;方案A全部落地,m277=作者实机profile前后对表落账+按数据决定方案B是否立项）；m275：观众定向渲染快照+标脏聚合(审计第3条方案A核心刀)——writeNbt/readNbt拆出writeRenderNbt/readRenderNbt渲染子集(18键拆分前后逐键相等+子集写读对偶两道断言过,存档格式零变化);新S2C包CanvasSnapshotPayload(BlockPos+UNLIMITED_NBT_COMPOUND,yarn核到field_49677,泛型值型待编译验证若CI红改PacketCodec.of兜底),收端写回客户端BE画布屏be()读法零改动;syncToClient换标脏29调用点零改动,flushCanvasSnapshot每tick于tickInner顶部按观众版本差补发(开屏首包与标脏聚合同一机制,createMenu强刷照旧,无观众清表重开必补发);量级=存档级全量×所有追踪玩家×每写一包→渲染快照×仅观众×每tick至多1份;prof对表尺随迁,m276接toInitialChunkDataNbt瘦身+200t周期核对+m89双轨收拢）；m274：全量NBT同步拆分方案稿落盘docs/同步拆分方案_m274.md(审计第一批第3条,先出方案再动手待作者拍板)——实测链路syncToClient=updateListeners→vanilla包→完整writeNbt存档级全量广播所有追踪玩家,触发30处,m269只治入口频率不治出口成本;客户端消费面grep收全(items/forceChunks等确定可剔,画布handler无槽位/BER只读时间/m89即定向payload在树先例);方案A=观众定向渲染快照+标脏聚合(writeRenderNbt子集收口,syncToClient内部换标脏29调用点零改动,tick末合并1包每观众,开屏首包,toInitialChunkDataNbt瘦身,200t周期改快照,prof对表),方案B=分段rev增量按A对表数据决定;拍板后按m275/276/277切分实施）；m273：NBT读入账本校验+全账本饱和加法(外部审计余账)——新公共辅助StorageCoreBlockEntity.satAdd(非负计数饱和加法,把FTA insert路径既有Long.MAX_VALUE-cur口径收成唯一出口)替换全部10处裸加法(存储核心xpAdd/deposit/depositExact,结构核心泵料merge×2/面板聚合merge×2/双缓存合计/bufAdd中间溢出翻负会绕BUF_CAP封顶,数据面板xpCache);NBT读入校验三处(store/internalBuffer/nodeBufs循环)=空键/非正计数丢弃(写路径left<=0即remove零值从不合法落盘)丢弃数>0时LOGGER.warn出声,精确账本原有校验与tier/xpBank原有Math.max守卫不动;路由语义零变化机器组合.md核过无需动）；m272：配置加载健壮性(外部审计"配置损坏可能阻止启动"条)——load()异常分流:IOException=文件保留+日志出声+回落默认且本次不回写防覆盖,RuntimeException(Gson的JsonSyntaxException等运行时解析异常,旧版完全没兜一个多余逗号就中断MOD初始化)=坏档改名sdzjz.json.broken-时间戳留证后回落默认继续启动(防load尾部save()回写默认值覆盖用户手改内容,改名失败退保守路径原文件不动不回写);save()的catch(IOException ignored)改LOGGER.error出声(磁盘满/只读时配置默默丢);空文件Gson返回null照旧默认重生成;调查留痕=数值字段使用点大多自带Math.max守卫统一钳制暂不铺开;零新配置键v25不动）；m271：CI红热修——m268的isForcedNow在CoreChunkLoading重复定义两遍致fc603c6/1488dac连红;冒烟盲区#3实锤=方法参数类型是MC类解析不了时javac判不出同签名,already defined沙箱根本不报只有CI真编译现形;删重复+新回归尺docs/tools_dup_method_check.py(同文件同名同参数类型序列≥2即报,合法重载不报,注入式自审过)挂进CI成第五道闸）；m270：服务器硬上限(外部审计第一批第四条)——四道闸全0=无限只闸新增长旧档不截断:节点maxNodesPerCore=512(insertMachine把门签名升带玩家actionbar提示)/连线maxEdgesPerCore=2048+度数maxEdgesPerNode=64(toggleConnection断开永远放行,storageEdges单独同额封顶)/缓存类型maxBufferTypesPerNode=256(bufTypeOk只拒新类型,5写入点取料前判:泵=不抽留仓,分发=残量走默认路由,零物品损失)/maxRecipesPerCoreTick真接线cyclesThisTick全核共享预算(审计核实三键此前零使用,耗尽=工作量保留下tick续,Chunk/Network两键标遗留),v25,机器组合.md同步）；m269：C2S包全面护栏收官(外部审计第一批第二条余账)——17接收器逐包过堂4改13核实:setNodePos坐标钳±1e6(审计点名"任意32位整数写NBT")/moveGroup改long安全加法+终值钳幅(反复发包int加爆点名项)/NodeAddPayload补itemId≤128长度闸/统一入包闸=viewingCore与viewingPanel前置writeBudget每玩家每tick写包预算(每个写包都触发全量同步,洪泛=同步风暴;放必经谓词未来新接收器自动被闸)/配置packetWriteBudgetPerTick默认40零手感损失0=关,v24纯加键;复核无恙项清单落DEVLOG防重查）；m268：强加载所有权修复（外部审计）——CoreChunkLoading加所有权判定:运行时force前查getForcedChunks本MOD未登记且已forced=外部所有记EXTERNAL永不解除;本MOD亲手force的区块所有权由核心持久化进自身NBT(chunkOwned零新API);release/reclaimOrphan凭核心传回的所有权只解除本MOD名下区块,重启后运行时表空但核心chunkOwned仍在故不误伤管理员forceload(首版PersistentState.Type沙箱查不了类型CI真编译红,改核心自身NBT)）；m267：P0/P1终端视图包DoS护栏(外部审计第二条)——setView入包四层:搜索词钳128/matchedIds上限256单项128且Identifier.tryParse过滤非法丢弃/scrollRow钳0..1e6/三值变化检测同值零刷新/每玩家≥2t一次真刷新不丢更新;精确聚合O(n²)→O(n)(第四条)=ItemVariant哈希键LinkedHashMap合并顺序排序零漂移）；m266：P0终端Shift取物复制窗修复（外部审计第一条）——quickMove展示格改账本权威:先withdraw按实取量发货,取0则不给并刷缓存,背包塞不下余量deposit原路退回绝不落地;旧序先塞后扣且忽略withdraw返回值=两面板同tick抢最后一组时凭空复制,单击路径本来就查实取量属shift路径独漏）；m265：总线端点卡可拖下画布（复用m80前遗留storageNodePos/StorageNodeMovePayload整套旧管线；值升三元带放置标记老档二元死数据仍停靠；新姊妹包StorageNodeHomePayload走m89通道40t同拍；snx/sny三态收口=拖动覆盖>放置投影>停靠排位9处调用全链路自动生效；拖出带钉画布/拖回带收回/微动不动/右键收回总线；坐标双端±1e6钳幅；canvasEndsPlaceable开关v23）；m264：节点菜单组合两入口（组合所选=选中集∪右键这台/组合相连=connectedComponent沿机器连线BFS收整串，纯客户端拼成员走m191建组包零新协议，≤512熔断上限内才显示不给静默哑口；空白菜单打组/G键/框选照旧）；m263：物品图穿模顶部总线带修复（机器层剪刀顶缘24→动态带底bandBot与带绘制同源同帧，带区零机器像素可穿；带本体挪出机器剪刀按原24剪刀单独裹，视觉零漂移；m159底栏同病同修思路=裁剪治穿模不玩z序）；m262：设置面板二轮紧凑化334→300（行距18/行区起点20收口SETT_ROW0/滑内距13/间距各收/底注释挪标题行右侧顺治与按钮重叠旧账；几何账脚本复算断言不重叠，协议零变化）；m261：画布背景默认纯黑000000（v22迁移只替换旧默认空串用户自定义不动；设色即m220自动隐装饰图，清空可回随主题）；m260：拆包卡顿主治（m246逐包循环=每包全扫180槽做NBT容量账,一击几百包→十几万次比较内置服卡顿——改槽级批量capacityAll一次结算+insertBulk单趟灌装,每击O(槽×轮),语义零变化;报账剩量只数可拆包/全清不误报已满,死码收编;main/markDirty yarn双核）；m259：CI首跑复盘——Gradle编译job success=m177~m258共82笔真编译欠账一次验绿！红在配方校验闸=tools_m172_check.py写死/home/claude沙箱路径(m109坏尺子同款)，改自锚定+四道闸陌生cwd预演全过，第二跑应全绿出jar；m258：CI正式启用（作者开通PAT workflow权限，工作流搬进.github/workflows四道闸=配方校验/资源审计/文档同步/取色回归尺+Gradle真编译出包，首跑即m112以来第一次自动真编译，红了按报错逐修）；m257：全库语法冒烟清账（m177~m256共80笔没过编译器——javac21全库108文件一次喂+Xmaxerrs放开上限（默认100条封顶=假绿新教训），真语法错0、自家126类符号定向检零命中、自家包错误0；冒烟查不了MC API类型错，真编译仍等作者构建或CI启用）；m256：CI启用二次尝试失败留痕（对接文档PAT仍缺workflow scope整推被拒已回滚，工作流继续暂存docs/ci/ci.yml并挂上第四道闸=m255取色回归尺；启用两条路：重发带workflow权限PAT或网页端手动放入.github/workflows）；m255：全画布事件路径裸取色巡检销账（m239挂账待办）——8条事件路径×3层调用图零裸读色、唯一读色口settColorVal已自带作用域、事件路径配置写无"读色→写回"模式、render作用域包裹完整；巡检落盘docs/tools_color_scope_audit.py成回归尺（命中退出码1可挂CI）；m254：mC收尾"m末"销账（机器组合.md新增工程款造价段：全量总数口径+玩家四步用法+账目权威源；核查报告追加第七节结账记录：18/18收官轨迹与精账14/粗账4分野；docs_sync✓94）——超级压缩改造需求线 m236核查→mA(m241)→mB(m242)→mC(m245~m253)→m末(m254) 全线收官，唯四张粗账投影重传后可精算重跑；m253：缺档4台按摘录过粗账=mC 18/18台全入新账（40核8033→8262溢价2.9%覆盖91%/920万船吸107425→111937溢价4.2%覆盖93%/史莱姆4654→4832覆盖80%/猪灵交易1934→2178覆盖65%——粗账=下限账，摘录截断的7%~35%无从入账，Java注释明记；重传四张投影走m172管线落json后tools_pack_bom一跑即精账）；m252：工程款全量过账农作物塔+渔场（json直出组真收官14/18台——m248把渔场误记缺档，实测json里有：农作物塔37种14575→15258溢价4.7%槽位45耕地并土/裸火把并账/作物归种子，渔场沙74112全量=19超级包78930→82779溢价4.9%槽位48水64桶税/他模组背包件剔除/漏斗矿车入料；真缺档只剩4台：40核刷铁/920万船吸/史莱姆/猪灵交易场）；m251：工程款账本死料修复（守卫者wall_torch×1/160核farmland×128两处无物品形态死料——根因归一化表漏收耕地→土与裸wall_torch后缀规则漏网，工具补三条RENAME+落盘多键合账水税封顶+新增--extra外挂账参数全script算账，两台重跑销死料：守卫者3752→4195溢价11.8%槽位83、160核总账不变dirt=128槽位71；教训：口径文档写了≠代码收了，落地必须双向核）；m250：全息动画观感返工（渲染层换EntityTranslucentEmissive半透明自发光、三张贴图重画羽化渐变细亮芯低alpha按方块配色紫/青/青绿、网格128px细线径向羽化0.76宽9°/s缓转、扫描带0.06高alpha脉动、数据流0.08宽彗星端点淡入淡出，贴图预览人工过目后提交；教训：BER发光件先出观感样张，半透明+羽化+脉动三件套缺一即塑料贴纸）；m249：三块静态方块动画化（结构核心=扫描环3s巡扫/数据面板=四面字符雨错相巡游/超大工作台=悬浮全息网格缓转浮沉，StorageCore同款BER手发四边形+满亮度自发光+三张程序化全息贴图；工作台补零数据BE升BlockWithEntity覆写MODEL渲染型，旧存档已放置工作台需重放一次才有动画）；m248：工程款批量过账再6台json直出组收官12/18台入新账（160核+收集合账2.7%/猪人塔合账4.6%传送门剔除/守卫者三件套11.9%+海绵16外挂账人工补/蜜匹15.0%/紫水晶3.8%/试炼大厅2.2%），剩5台json缺档（920万船吸/40核/史莱姆/猪灵交易/渔场）按注释摘录粗账或等作者重传投影（首选重传，摘录有截断）；m247：工程款批量过账6台（刷石机重取整+1728熔炉阵+沼泽v2+百万劫掠塔+溺尸塔+凋骷农场，溢价3.5%~9.2%）+取整策略修正（去"超32格强制全二级"——前提1格1件已被包堆叠淘汰，二级仅溢价≤15%用，作者可否决）+实体入料口径（载具展示类入料/活体不入/铁傀儡折铁块4+南瓜1）+tools_pack_bom.py落盘；剩12台：json直出6台（160核/猪人塔/守卫者三件套/蜜脾/紫水晶/试炼大厅）+json缺档5台（920万船吸/40核/史莱姆/猪灵交易/渔场按注释摘录或等重传）；m246：拆包重做（产物先网格后背包两级承接永不落地、空间账按层各算、每击必报账、包在背包时明示挪网格；旧"只落网格装不下罢工"在大数量下=功能性死锁已除）；m245：工程款全量过账首台=百万刷石机（json全解52种→归一化口径定档：红石线→红石/壁挂→手持/水=64桶税/岩浆全量桶/技术方块·不可获得·实体剔除；策略取整50种料BOM总数113182溢价18.1%槽位59，脚本算账+断言双算；剩17台照此口径逐台一笔）；m244：打包版BOM基建（bomPacked建器/layout=null打包填料标记/pullPacked二级→一级→散件三轮贪心搬包/屏大数K缩写/三条离线断言：64整倍·超32格须4096整倍·保守槽位账≤144；暂零台入表，m245起逐台照 docs/litematic_实测_2026-08.json 过账）；m243：压缩包动态图标加框（内容物模型缩0.8+档位边框叠层，builtin/entity+BuiltinItemRendererRegistry，坐标账+0.5回中心嵌套renderItem零双重变换，GUI边框前移0.4防穿插，旧静态底图删）；m242：匹配内核认包（gridMultiset/扣料/客户端对照三点把压缩包按 内容物×倍率 折算原版计数，精确多重集语义不变，防御性找零回网格，填料铺格待 mC 定语义）；m241：压缩包两件套+工作台压缩区（方案A/mA 作者拍板：一级64:1/二级4096:1 通用两件、CUSTOM_DATA 记内容物同内容堆叠异内容不混堆、右栏底两钮压缩级联/拆开只落网格查容量不落地、组件物品不压防抹组件、六件套断言全过、下一步 mC~ 逐台照 litematic_实测 json 过账重写工程款 BOM）；m240：超大工作台底部越界修复（热栏304..321压过贴图308..315底边伸出316面板——面板加高332+底图三段带绘0..304原样/288..304平铺16px/304..316底边落新底，逐行扫描证实平铺带仅竖线无缝，四常量收口协议零变化）；m239：背景色"清了又白回来"根因修复（scopeCanvas 只在 render 帧内开、滑杆走事件路径漏在终端域取到紫晶浅墨写回背景——settColorVal 收口自带作用域保存/恢复四目标一次修齐）+设置面板紧凑化（行距20/滑杆内距14收口 SETT_SL_SP/394→334 作者视口装得下；待办：全画布事件路径裸取色巡检）；m238：投影实机复核（46 张 litematic 全解析落盘 docs/litematic_实测_2026-08.json，三锚点 99587/6013/2005 与注释档案精确一致=档案验真，无尽贪婪 20 张复原销 m173 旧账；方案 A 拍板，下一步 mA 压缩包两件套）；m237：超大工作台换肤统一（super_bench_gui.png 整图转薰衣草靛紫 594080 像素与全 MOD 同盘）+配方搜索（m216 工艺自绘底格/过滤视图存 ALL 下标填料协议零变化/名称+id 双匹配）；m236：工程款"总数改造"核查报告（docs/超级压缩改造核查.md：18 台 litematic 全量数据在仓已整表摘录、纯64:1超144格必须上4096:1二级档且策略下全表✓、方案A两件通用压缩包+组件敏感匹配待作者拍板、无尽贪婪投影台数据丢需重传；笼子tooltip双模组名=客户端JEI+REI各加一遍非我方bug）；m235：合成机手选配方（节点菜单"配方:自动→换"循环换挡走 NodeFilterPayload#cr 哨兵零新协议，服务端权威循环候选序双端同源，节点标签 cr+NodeTags 纯函数，生产/链需求/收料三点手选优先、失效回退自动、换目标自动清选，Plan 带 recipeId+LinkedHashMap 保格序）；m234：合成机多候选配方按库存挑选（CraftPlanner 全候选缓存原版排前+生产端 pick 按实际库存定案都不齐回退原版报缺料+链需求收料改候选并集，治"装 ProjectEF 后金锭报缺贤者之石无视 79B 金粒"，机器组合.md 已同步）；m233：数据线按面断开（链接器潜行右键手臂=断/缆芯断开侧=恢复/缆芯=启停原样；视觉 endFor 双端掩码+客户端短路防鬼影、拓扑 linkBlocked 插全库 7 走线 seen 前、抽取口四口径同断+缓存作废，offFaces NBT）；m232：升级读数负数修复（16 位属性通道 m106 同坑——批量低15+高位双属性拼回+1K/1M/1B 缩写显示）；m231：抽取口回收模式（方向钮送出/回收单口单向结构性无环；doPull=StorageUtil.move 机器可取视图→core.fabricStorage 双账本入仓绝不落地，尊重侧向规则只吸输出不偷原料，过滤同 m225 三态，升级三件全套生效，界面下移22高224）；m230：抽取口升级槽（速度/数量/并发真槽级数=件数，周期=基础÷(1+速度级)触底1t富余折批量永不静默无效，批量=基础×(1+数量)×(1+并发)×折算，[3][4]属性实时读数，quickMove三分流）；m229：ProjectEF 转化桌软兼容——贴桌即卖（全反射零编译依赖五签名对源核实；owner 认领制 EMC 记账+仅在线卖，FTA 目标优先箱满溢出才卖、无价物回账本，界面出售状态行+桌伸插头，卸载即静默降级）；m228：抽取口插入六面视图（AvaritiaNeo 压缩机等"只认顶面收料"侧向机器修通——每邻块贴线面→其余五面逐视角收集身份去重，语义=六面各贴漏斗且尊重侧向规则，旁接计数改邻块数口径）；m227：扳手并入数据链接器（链接器右键数据线=配置屏/潜行=开关，原绑定功能不变；网络扳手六件套整体退役，wrench.on/off 键改名 extract_port.on/off——一件工具管完全部网络配置）；m226：抽取口配置界面（现为链接器右键开屏；9 幽灵过滤槽=点登记/空点清/背包shift登记/槽shift·Q清，光标不消耗、服务端权威落盘，启停钮+邻接存储计数双属性同步，五屏成列零新协议零新配置——"数据线连所有存储+可选抽取什么"需求线 m224~m226 三连收官）；m225：数据线抽取口引擎（潜行右键开关；相连核心账本→邻接 FTA 存储，空过滤=全部/模板白名单双模式，塞不下回账本不落地，pos移相+40t核心缓存，v21）；m224：网络扳手六件套+数据线 FTA 直连层（邻接存储探测/插头视觉，走 Fabric Transfer API 标准口零逐模组适配；抽取引擎 m225、配置界面 m226 接续）；m223：设置面板四色行加 RGB 滑杆调节区（点行选中+拖杆即时预览，照 m202 终端调色器工艺，预设/恢复默认下移常量位）；m222：底部五钮自适应居中（装行折行+行内测宽居中，固定坐标退役）；m221：整理布局间距收紧（步距=卡实占+间隙，rows/GapX/GapY 三键 v20）；m220：设背景色自动隐装饰底图（canvasBgDecor v19，改色无感/多余边同根修复）；m219：底带收纳——状态/帮助收顶栏钮，底带默认只剩按钮排（canvasStatusOpen v18）；m218：多核心性能第一刀（面板视图rev缓存/精确支路走缓存/四拍错峰/scratch复用，双开关v17）；m217：画布背景四项进设置（底色/网格色/网格浓度/暗角强度，空=跟随主题，v16）；m216：存储终端搜索框自绘默认提示（原版placeholder聚焦即隐弃用）；m215：画布上下chrome紧凑化——底带78→56/总线行距收紧/卡尺寸滑块下限0.55并落盘，canvasCompactChrome 开关可回旧版，v15；m214 画布/终端主题分家（画布默认暗夜、终端默认紫晶，v14迁移）；m213 JEI整组填料均分；m212~m215 待编译验证，JEI依赖=全量fabric jar（m209））

**作者本地 gradle build 全绿至 c5b5982=m176（2026-08-01 实测：Loom 1.7.4，BUILD SUCCESSFUL 1m1s，
仅两条"已过时 API"注提示非报错；作者自备"拉取并构建"工具直接同步 jar 进 1.21.1 测试实例）——
m129~m176 编译层欠账一次性清账，m173h 热修就此验明（修前的远端 m173 编不过）。**
实机游玩验证按待办池清单跑（编译≠游玩验证，DEVLOG 各里程碑验证脚本照旧）。近期里程碑：

- **m163 三连**：a 抽取量五挡 64→512→4096→32768→262144（撤 BUF_CAP 钳位+精确支路封顶统一
  bufCapL）；b 抽取白名单候选=仓库现有（总线聚合并入精确条目、前10→前400、复用 busIds 通道
  零新协议，选择器走 pickerSrcOverride）；c ItemStackProMax 大堆叠兼容（全库已动态 getMaxCount
  白捡；终端两处"合到一整组"循环加 4096 轮冻死护栏，原版下永不触发）。
- **m162 图标换皮**：垃圾桶/抽取节点用户新版立绘归位。
- **m161 三连**：a 交易机/打折机图标换皮（用户新版 1254² 立绘归位）；b 终端搜索框去黑壳
  （撤原版黑底灰边，CELL底+细边聚焦亮青）；c **跨模组直连**——存储核心挂 Fabric Transfer API
  （ItemStorage.SIDED），Create/MI/TechReborn 等 FTA 管道怼核心即存取，双账本全暴露、
  事务快照回滚、markDirty 推迟提交。截图里粉↓箭头/紫K/右上四钮经像素级排查属 IPN（外模组），留痕在 DEVLOG。

- **m99 升级数学重写**：工作量累积模型。速率=(1+speedGain)^速度级×productionRateMultiplier，
  每 tick 累积、溢出折同 tick 多周期永不触底；并发=直接乘台数(台数×(1+级)×tier)；数量封顶只剩
  "产出只能进内部缓存"时。五条生产分支（自动合成/农场/万能熔炉/通用机/抓物笼）统一。
  config 新增 upgradeSpeedGainPerLevel(0.5)/upgradeMaxCyclesPerTick(20)，configVersion=4。
- **m100 批量取出**：数据面板右键浮层第二行 2组/4组/8组/填满背包；服务端分块取+余量回仓绝不落地。
- **m101 交易所**：图书管理员 10 本好附魔书（绿宝石+书，治愈折扣生效）；列表 4 行滚动窗口；
  附魔书直发背包（仓储按 id 记账会抹组件，绝不入仓）；修双输入交易不扣第二种料的旧 bug。
- **m102 深层采掘平台**：钻石(0.15)/远古残骸(0.05)/深层地质/原矿三件套加权掉落；引子配方
  钻石×2+残骸×2；残骸→万能熔炉→下界合金碎片，量产链打通。
- **m103** 滚轮只在悬停交易列表时翻页；**m104** 深层采掘平台真美术归位，**全库 79 张物品图零占位**。
- **m106 存储终端双修**：a) 修"无存储核心"假红字（0xFFFF 无限哨兵过 short 通道符号扩展成 -1，
  收包端补掩码）；b) 合成终端 AE 式补完——shift 点结果连续合成一整组（服务端权威零预测）+
  网格模板化网络自动补料（学 AE2 CraftingTermSlot 思路，代码自写，LGPL 不抄）。
- **m107 存储终端体检 8 项全落地**：性能（经验属性缓存撤每秒 40 次 BFS/面板 viewer 门控闲置零空转/
  搜索本地化名静态索引）+ 滚动（真实比例滚动条可拖拽、行数属性 id4 同步、滚轮只在存储区生效）+
  合成区（清空回仓按钮 id=3、带组件拒收 actionbar 提示）。
- **m108 三连**：a 绘图名单更正（五张"待重绘"实为用户素材，零待画）；b 交易所/村民合同等 15 基础件
  进超大工作台浏览器（与原版配方逐字一致，修配方书无解锁不显示的发现性死角）；c 面板 cores() 40t 缓存
  （撤每调一趟 BFS，高产线卡顿主治）。
- **m109 量产覆盖四提案收官**（引子模式照 m102）：a 考古工作站（20 考古陶片随机/回响碎片/唱片残片5/
  三稀有唱片/海洋之心/附魔金苹果极低；引子=回响碎片×2+海洋之心×2）；b 末地远征平台（末地石/龙息/
  鞘翅 0.004 极低；引子=末地石×2+龙息×2）；c 试炼农场（试炼/不祥钥匙/不祥之瓶/1.21 三陶片/
  重锤核心 0.008 极低；引子=试炼钥匙×2+不祥之瓶×2）。三台均 40t 周期、六件套断言过、
  签名多重集全表 62 条唯一、贴图程序占位挂绘图名单。
- **m110 画布概念图收尾**：a 小地图（顶条「地图」开关；节点分类配色概览+视口白框+点击/拖拽跳转，
  几何快照防抖，纯客户端零协议）；b 节点齿轮设置+单节点启停（状态存节点栈 NBT "np"；
  NodePausePayload 照开关包样板；tick 最先判不攒进度、闸门/链需求/收料四处消费；
  右键菜单抽成 openNodeMenu 与标题栏齿轮图标共用，暂停=压暗+黄字角标+黄灯）。

## 架构速查（改哪类问题去哪个文件）

- **组合玩法/物流语义**：`机器组合.md`（m134 照代码实证：三层物流/四逻辑节点/闸门连锁/链式拉料/蓝图集/防坑）——改路由代码须同步更新

- **生产/升级/tick**：`block/StructureCoreBlockEntity.java`（~1900 行核心；五分支 tick、
  cyclesThisTick/runningCount/rollDrops、供料 supplyFor/入库 depositFor/分发 distribute、链式需求 chainWants）
- **机器定义/注册**：`machine/Machines.java`(掉落表) + `registry/ModItems.java` + `machine/SuperBenchRecipes.java`(引子签名配方)
- **配方规划器**：合成=`machine/CraftPlanner.java`；熔炼=`machine/SmeltPlanner.java`；
  酿造=`machine/BrewPlanner.java`(m131b,BFS原版酿造注册表,目标串「药水id|p/s/l」,缓存挂SERVER_STOPPED)；
  附魔=`machine/EnchantPlanner.java`(m132,附魔动态注册表,目标串「附魔id|等级」,成本=书+青金石3×级+经验B×级×25点从核心经验池xpPool扣,缓存同位)
- **存储网络**：`block/StorageCoreBlockEntity.java`（connectedCores=贴邻/数据线 BFS4096；类型默认无限 m98）
- **存储终端**：`screen/DataPanelScreenHandler.java`(按钮 id=1000+格×10+档位0..8；id6=右键整组合成) + `client/DataPanelScreen.java`；合成网格 m126a 起常驻 `block/DataPanelBlockEntity.java`(craftGrid, NBT 持久化)
- **交易所**：`machine/VillagerTrades.java`(纯Java,Trade record 含 enchant 字段) +
  `block/TradeCenterBlockEntity.java`(employ/trade/heal) + `client/TradeCenterScreen.java`
- **画布 UI**：`client/StructureCoreScreen.java`（节点/总线/机器库侧栏/视图控制）
- **网络包**：`net/*Payload.java`，注册与接收器在 `Sdzjz.java`
- **美术管线**：物品图 128×128 RGBA 透明底进 `textures/item/`；归位=裁边→留4%边距补方→LANCZOS 128→
  断言尺寸/模式/覆盖率→勾 `绘图名单.md`

## 立项候选（作者已拍板方向，动工前再对细节）

- **"小说系统"金手指（作者 2026-08-08 定调：先记录不往里做，可做联动）**：系统流包装——
  绑定宿主/"叮！"提示（m311 随身仓库已用此风格）/宿主面板（总产能/仓储总量/产线数纯展示）/
  签到/系统任务（生产目标发奖）/系统商城（绿宝石或经验池兑换）。**联动方向**：与随身仓库、
  交易所、画布经验池共用经济口径；奖励表/定价/任务池等数值设计留作者拍板后再排里程碑。
- **随身仓库后续**：GUI 取物屏（m312 在做）；压缩包第三级 ×262144（m241 级联架构现成，
  差一张档位边框贴图，作者点头即做）。

## 待办池（按优先级）

0. **外部审计②余账对表（m345 登记；P0 CraftPlanner=m343 已销、P1 玩家扫描=m344 已销，均逐行对源核实后才动的手）**：
   - **属实待做**：①SmeltPlanner 稳定选序=m346 已销，余"按库存挑输出"升级项待作者拍板；②tick 头维护段=m348 已销（扫描分档+看门狗冻结坑，区块票 20t 管停机转变沿属必要留驻）；③孤儿 claim 渐进核销=m347 已销；④DataPanel"重复解析"=已有等效实现（cores() 自 m108c 40t 缓存+幽灵重建，m290 升 public 共链路），不另做快照层；⑤BrewPlanner 全图一次 BFS
     （现=逐目标 BFS 有缓存，首开选择器偏重）；⑥bigStacks 兼容分档（OFF/VANILLA_ONLY/FULL，
     审计标 P1 兼容风险，涉及三方模组 int 假设——机制改动需作者拍板）；⑦portableVaultSlot 双端
     一致改握手或恒留槽（m332 立档项，审计再点名）；⑧生产预算默认值出 safe/normal/high 预设
     （现四键天文数字=零变化哲学，审计建议预设化——产品口径待作者拍板）；⑨Machines.java 数据
     驱动化 json（长期）；⑩SCBE 拆 Runtime 家族（绞杀者 m180 已开线，长期最大工程项）；
     ⑪Canvas 增量快照/复用集合 GC 优化（P3）。
   - **已有等效实现/部分失效**：审计⑬ Mixin method="*" 建议加 codec smoke——GameTest 九号用例
     m310 已是该判官；⑰⑱ GameTest/CI 已覆盖其点名的多数（withdraw 竞争/事务/精确件），其补测
     建议中"Ingredient 替代材料"=廿六号已加，"多熔炼配方选择稳定"随①做，"512 节点压测矩阵"=
     /sdzjz bench 已有待作者实机跑，"多人同 Panel"=十四号已有，"orphan claim 重启"=框架内做不了
     （m326 #6 同口径）留实机。
1. 验证 m112-m163。**m163**：五条见 DEVLOG（换挡五挡/白名单窗只列仓库现有含精确条目/
   ISPM 上限 10 万实测拿一组与 4096 轮护栏）。**m161**：b 搜索框观感（无黑壳/聚焦亮青/resize 留字/中文输入照常）；
   c 跨模组直连六条见 DEVLOG（需装任一 FTA 管道模组如 Create 实测存取/精确抽取/类型上限背压）。
   **m161 后续三件（用户拍板即上）**：①漏斗对接（核心实现幻影槽 SidedInventory，插入即入账、
   禁抽取，内部 BFS 无自冲突已核实）；②反向直连（pushOutput 补 ItemStorage.SIDED.find 分支，
   往 MI/TechReborn 这类只暴露 FTA 的机器里塞料）；③EMI 插件（超级工作台配方进浏览器，需加
   EMI 编译依赖）。**m133 强制加载**：开机核心跑远 500 格回来产量不断档；/forceload query 见核心
   区块、停机 15 秒后消失、拆核心消失；重启服务器不靠近产线仍跑（旧存档升级后需先路过一次核心
   建立标记）；跨维度端点持续入库；config coreChunkLoading=false 复旧行为。**m130 精确存储**：附魔书/损耗钻镐/GM机器 各存取一轮组件应无损；
   同一种书不同附魔应分行显示各自计数；批量取出（右键浮层）对附魔书应可用；"类型 X/Y"计数含精确条目；
   带组件物品 shift 存入不再弹"不入仓"提示。**m128（m125三修代码补推——m125 那笔提交只有文档没有代码，沙箱宕机丢失，已按 DEVLOG 重建）**：验证项即原 m125 三条（融合聚敛/取出保阶位/批量升级不卡）+ 融合入口现按"全画布同类同阶总数≥4"显示。**m127**：a 终端左侧立牌已撤、物品 tooltip 显示"DY：乔大仙"；
   b 结果格防线——终端/超级工作台的结果格：右键（结果数>1 时）、Q 键、双击收集都不应再取走部分产物
   （终端右键=整组合成到光标是 m126b 正路；超级工作台右键应无动作），左键空手整取/Shift 照常，
   重点盯"取一半白丢一半"绝迹。**m126 存储终端合成双修（AE2 源码实拉对照后落地）**：
   ①网格常驻——终端摆好配方关界面再开，9 格原样还在；退出重进世界仍在；拆面板方块网格内容散落不吞；
   两人同开一个面板看网格同步；②右键结果格=连续合成一整组到光标（光标拿着同类=续装到满），
   不再出现"右键取一半白丢一半"；左键单取/Shift 整组进背包/清空按钮不回归；
   ③shift 整组连打应比之前顺（配方缓存撤全表扫）。其余：m125 审计三修：①画布散放 4 台同类 ×1 机器→任一节点菜单"融合"应聚敛合成，
   被抽空节点消失、其升级回背包、连线不错乱；②取出超级/GM 机器再放回，阶位前缀仍在；
   ③Shift 批量塞升级不再瞬卡。其余：m117 换肤第一步：四屏观感应与之前几乎一致（刻意变化仅两处：终端/交易所的
   按钮悬停色并入画布蓝族、超级工作台多了全屏暗底），重点看有没有哪块颜色明显错位；
   后续换肤只改 client/SciSkin.java；m118 已接 slot.png/button.png 贴图管线
   （简易稿复刻旧观感），实机看终端槽位/画布按钮有无错位拉花，用户 GPT 精修图同名覆盖即换肤。
   m116：白名单远古残骸的 面板→过滤器→熔炉组 链，残骸碎片应从 ~100/秒跳到
   万级/秒（受熔炉自身产能上限）；打开过滤配置窗，已选项应固定显示在最前排。
   m115a：升级格 Shift+左键应一口气吃光背包同类升级（至多64）、Shift+右键全取回；
   m115b：断网首次喷射有聊天警告、接回存储后再断会再提醒；/tick 或大量实体压到 >45ms 看机器全线
   黄灯暂停+提示、流畅后自动续跑；>60ms 喷出的掉落物被清而玩家扔的不动。
   m114 断网喷射（现 2t 一组≈10组/秒）：裸核心+机器不接任何存储，顶面应急速喷掉落物，
   贴上箱子/面板 2 秒内停喷改落库，拆掉又恢复喷射；挂机十分钟看掉落物堆不失控（原版合堆+消失兜底）。
   m112（修 m111 整页清空回归）：复现视频操作——拿着东西 shift 点背包物品入库，展示区不再
   全黑；取整组/半组后格子即时显示余量；shift 入库/取出各连打十几下看有无错乱；其余按键表照验（m113 终版：
   空手左键=抓整组、空手右键=数量浮层(一组/拿满)、拿着左键=全存/右键=存1、Shift+左键=一组入背包）。
   m110 地图/暂停顺带。
2. **已拍板路线（m129 用户确认）全部落地**：精确存储(m130✅) → 酿造塔(m131b✅) → 附魔工厂(m132✅，均待编译验证)。
   m132 验证：超级工作台 附魔台+书架+书+青金石 合出附魔工厂；画布节点菜单"选择目标附魔"开**行式**
   选择器（搜"锋利"出 锋利V..I 五行，图标+罗马数字名，当前目标绿框）；选 锋利V 挂网络+同画布
   烈焰人塔攒经验，应吃 书1+青金石15+经验125/本（扣的是画布经验池——工厂与"领取经验"按钮抢同池，
   属设计特性）出附魔书入库为精确条目（终端分行、与交易所同款书合并计数）；池空/缺料红灯；
   断存储时输出缓存书不混堆不变裸书；上游连线直喂书/青金石可行；经验修补/诅咒/模组附魔均可选；
   m132-6 顺修验证：面板→过滤器(白名单书+青金石)→附魔工厂 链应能拉料生产，同构验酿造塔
   （过滤器喂酿造材料——m131b 这条此前恒不通，chainWants 漏接实锤）。
   m131b 验证：超级工作台 brewing_stand×2+blaze_rod+nether_wart 合出酿造塔；放画布点徽章开药水选择器
   （三形态按钮/搜索/绿框回显）；选 强化迅捷·喷溅 挂网络后应吃 玻璃瓶3+地狱疣+糖+萤石粉+火药+烈焰粉
   （4步→每5批1粉）出3瓶入库为精确条目（终端分行显示）；断存储时输出缓存药水不变裸瓶不混堆；
   力量药水材料粉+燃料粉双账扣数对得上；上游机器连线直喂材料可行（accepts）。
3. **队列已拍板（用户"按顺序来"）**：G组杂项(m135✅) → 凋灵机(m137✅) → 幽匿线(m138✅) → 砂轮祛魔(m139✅)——**队列四项全部收官**。
   m139 验证：砂轮2+书2合机；仓里塞附魔书挂机→书变裸书+经验池按表涨（锋利V一本+45）；
   纯诅咒书纹丝不动；断网络红灯；附魔工厂+砂轮同挂池不凭空涨（回收<成本，防泵封顶0.8×）。
   m140（实测反馈轮）：崩溃排查结论非本模组（栈零 sdzjz 帧+实体触点全审+毒实体在存档后出现
   重进即回滚，详 DEVLOG m140）；砂轮加青金石退款 1/级（33%有损）；图标裁切路线废弃、
   12 张全改规整机身占位（用户判词"异形"）。
   **m141 图标收官**：用户重发八张概念图（三张新入库），连通域抠图路线归位 12 张
   （矩形粗裁+alpha连通域剔邻居残片+直切沿框柱框线，256/128 双档亲眼核对），m140 异形清零；
   附魔工厂顺带从 m132 旧裁切换整图。**全库唯一待画：grindstone_recycler（砂轮）**，
   需作者单独出一张单机身透明底立绘。
   **m142 崩服修复（最高优先验证）**：两轮崩服真凶实锤=本模组 m133 毒区块票——OUTPUT_IFACE
   哨兵被 refreshForceChunks 解成天边区块发加载票，radius 邻块回卷打崩实体管理器；m140
   "非本模组"结论误判已撤回（错在把哨兵当真实端点+以为需要毒实体）。三层修：源头跳哨兵+
   合法性校验 / ticket 末端拒边界外票 / NBT 读入清洗存量毒条目（老存档进图即自愈，无需回档）。
   **m143 机器合并**：用户拍板"整合到一起"——凋灵线5台→wither_farm一台、G组3台→g_misc_machine、
   幽匿3台→sculk_line（经验20/轮总账不变），图标=概念图整图；核心NBT读入 MERGED_IDS 重映射
   旧id防节点丢失与 inputBuf 错位；背包/箱子里旧机器物品会消失（画布上已插的自动换新）。
   下一步候选（待用户拍板）：①**优先**：编译装新包进上次崩的存档验 m142（应不再崩+/forceload query
   只见核心自身区块）；②验 m143（三引子/旧档节点自动换新/凋灵机多产物/sculk_line吃池20/轮/
   创造栏只见3台）；③游戏内验证 m136-m141；④验 m144（砂轮新图标/合同悬停四步用法+职业折扣动态段）；④b 验 m166 配方=原版建造清单（刷铁机=实拍进货单+村民僵尸双笼、唱片机双笼、小黑塔=末地石+命名牌+矿车，七条见 DEVLOG；m165 三档材质盘已废、角标保留；m167+m168 刷石机双轨=入门款俩桶角标Ⅰ照旧 + 新机'百万刷石机'吃用户520万/h蓝图实测蒸馏123件18种全表最重/岩浆桶10对齐四千桶/产能722每10t≈520万每h、角标Ⅲ，验证五条见 DEVLOG m168；m169 双轨第二台'40核刷铁机'=用户40核蓝图实测蒸馏119件18种/村民僵尸双笼/产能40每40t=单核×40、角标Ⅲ，验证六条见 DEVLOG m169；m170 双轨三连'200万史莱姆农场/140猪灵交易场/920万船吸刷怪塔'=三张litematic实测蒸馏82/68/116件、产能200万球每h/46万件每h吃16.8万金/920万件每h、全Ⅲ档，验证六条见 DEVLOG m170；m171 双轨两连'沼泽刷怪塔(女巫×64≈101万件每h)/强力守卫者农场(×16≈9.2万件每h,zip电梯+击杀舱+仓储三件合账)'+图标政策定案=工程款永久复用基础款只改名、五张待画撤单，验证五条见 DEVLOG m171；m172 双轨九连+m173 特殊分支双工程款+m173h 热修(远端m173曾混入重复定义编译必炸,已修;javac.args 倾倒文件清库)+m174 新线六连"动物农场(五笼兔子线)/凋灵玫瑰农场(26k对表)/龙池杀凋机(付费快档星720/h)/屠龙炮(龙息+500xp每轮全库最强经验引擎)/疣猪兽农场/弱加载盾构机(吃TNT出地形18.8万块每h)"——38张litematic分拣收官,验证七条见 DEVLOG m174；m175 CI备好=GitHub Actions 三道闸(PAT缺workflow权限,工作流暂存docs/ci/ci.yml,重发带权限PAT或网页端手动放入.github/workflows即启用)(配方校验+全库资源审计+真编译出包上传artifact)——【待编译验证】欠账自动化闭环,首跑若红即是m112以来第一次真编译报错,贴回即修；m176 文档漂移清理=README机器数94标记块+fabric.mod.json撤电力系统+优化与缺口挂历史快照声明+新tools_docs_sync.py唯一数据源生成机器清单.md并入CI防再漂；m177 性能尺子=/sdzjz profile core|network|reset + dumpgraph(OP2),核心tick环形窗计时+路由供料链查同步逐点计数,增量同步与执行计划改造的前后对表基线,验证七条见DEVLOG m177；m178 错误解释=节点阻塞原因通道(nodeReason平行nodeStatus走NBT同步,statR+转绿清因,全库22红灯3黄灯配人话,命名缺料带账目"缺料：铁锭(仓3/需8)",卡面y+38常显红字金字徽章副行让位,零新协议,验证七条见DEVLOG m178；m179 编译执行计划=hasOut/hasIn/outT按topoRev修订号缓存零分配复用(五处突变点bump+长度兜底+派生列表只读已核),profile core行尾报编译次数稳态应≈0增长自带漏bump回归检测,验证五条见DEVLOG m179；m180 绞杀者第一刀=25个节点标签纯函数外迁node/NodeTags(方法体一字未改),SCBE原位同签名垫片零调用点改动回归风险按构造为零,SCBE 2921→2871行,后续每刀一里程碑,验证三条见DEVLOG m180）；⑤验 m160 三件内置（抽取白名单/阈值自动启停/安全桶,六条见 DEVLOG）与 m159（换挡/升级生效/状态栏裁剪）与 m158（三种摆法等价销毁）与 m157（卫星mesh归位/退料找回失踪货/搬仓）与 m154 抽取节点（重点背压闭环）与 m153（过滤→垃圾桶定向清仓/牧师青金石交易/深掘平台青金石）+复验 m152 卫星节点（材质/位置/欧拉序三修后：锅材质双面可视/馈源臂朝向/UV镜像三点）、m150 垃圾桶（两轮分发垫底/不抽仓/已吞计数）与总线卡观感、m149 机器二级界面（熔炉选烧什么/多产物机选产物/竖排整理，五条见 DEVLOG）与 m148 菜单3A化（弹入动画/行图标/危险项垫底红显/点选音，五条见 DEVLOG）与 m145 打折机、m146 无限交易机（交易机六条验证见 DEVLOG m146——注意 v1 交易表无'线收购'条目，刷线机直连演示前可一行补 t(string,20,emerald,1)）；⑥待办池其余。
   m138 验证：深暗之域三配方合三机（催化体+幽匿3/传感器2+幽匿2/尖啸+传感+幽匿2）；三机全吃
   画布经验池（催化2/轮、传感与尖啸9/轮），池空红灯、与附魔工厂/领取经验抢池属设计；催化机出
   幽匿块流水+催化体0.08；幽匿产物无组件、出线走正常物流（与山羊角出线无视相反，别测串）。
   m137 验证：超级工作台 星+凋骷头2+灵魂沙 合出凋灵机（另四台：三色灯+岩浆膏/角2+雪块2/鳞2+蛛眼2/
   双花籽+苔藓2）；凋灵机挂网络出下界之星（≈50秒/星基准）且画布经验池在涨（50/星）；山羊角机产出
   8 变体随机、终端按变体分行显示（精确条目）、给它拉出线时产物仍进仓（组件产物出线无视属设计）、
   断存储时缓存里角不混堆不变裸角；青蛙灯三色齐出；模组若加自定义山羊角（GOAT_HORNS 标签）自动进掉落池。
   m136 连线验证：画布连线应为丝滑发光缎带（旧为点阵方块）、两端发光圆点、脉冲顺流向、缩放 0.4~2.5
   线宽恒定、总线连线从卡底垂直进出且从节点卡片下层穿过；盯点 DrawContext.getVertexConsumers/
   RenderLayer.getGui/vertex(Matrix4f,f,f,f).color(int)。
   m135 验证：超级工作台 蛛网2+线2 / 孢子花2+苔藓2 / 紫水晶块2+方解石2 合出三机；放画布产出入库；
   蛛网机/孢子花圃两张为程序占位（G组概念图一体式构图，分区待视觉核对，见绘图名单）。
   幽匿线设计输入：概念图含 催化体/传感器/尖啸体 三塔（见绘图名单 m131a 条）——原版链条是
   催化体吃经验长蔓延，可考虑接核心经验池（与附魔工厂同池竞争先例已开）。
4. 组合玩法/物流语义手册：`机器组合.md`（m134）——改路由代码须同步更新。
4. 真美术：m147 再度收官（打折机/交易机立绘归位），零占位零待画。后续新机器出图规格照旧：单机身透明底立绘，同名覆盖即换。

## 用户使用速查（作者常问）

离人生产（m133）：核心**开机即强制加载**自身与存储端点区块，人走了照跑、服务器重启自动恢复
（旧存档升级后需先路过一次核心）；不想加载就停机或 config 关 coreChunkLoading。
村民合同：工作台 纸6+面包2+绿宝石1；交易所=绿宝石4+铁锭4+核心模块，**必须贴存储核心或数据线连通**，
就业耗网络里 1 个职业工作方块（图书管理员=讲台），治愈耗金苹果每级-10%最高5级，附魔书进背包不进仓。
