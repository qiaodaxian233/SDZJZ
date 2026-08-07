package com.sdzjz.client;

import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.net.NodeLinkPayload;
import com.sdzjz.item.AutoCrafterItem;
import com.sdzjz.net.NodeMovePayload;
import com.sdzjz.net.NodeFilterPayload;
import com.sdzjz.net.NodeSensorPayload;
import com.sdzjz.net.NodeSwitchPayload;
import com.sdzjz.net.NodeRemovePayload;
import com.sdzjz.net.NodeTargetPayload;
import com.sdzjz.net.NodeUpgradePayload;
import com.sdzjz.net.StorageLinkPayload;
import com.sdzjz.net.StorageNodeMovePayload;
import com.sdzjz.registry.ModItems;
import com.sdzjz.screen.StructureCoreScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 结构核心画布界面（ComfyUI 式）：
 * 机器节点 + 存储/终端接口节点（连了几个显示几个），平移/缩放/拖动/双向连线/右键菜单。
 */
public class StructureCoreScreen extends HandledScreen<StructureCoreScreenHandler> {

    private static final Identifier FRAME = Identifier.of("sdzjz", "textures/gui/structure_core_canvas.png");
    private static final int TXT      = SciSkin.TXT;
    private static final int SUB      = SciSkin.SUB;
    private static final int ON       = SciSkin.ON;
    private static final int CYAN     = SciSkin.ACCENT;
    private static final int NODEBG   = SciSkin.withAlpha8(SciSkin.CELL, 0xE0); // m207 孤儿字面量归队（尺子按RGB对表，调色板一变即成孤儿）
    private static final int NODEFRM  = SciSkin.FRAME;
    private static final int STORFRM  = 0xFF1E8A5A;   // 存储节点边框（绿）
    private static final int TERMFRM  = 0xFF7A5AC8;   // 数据终端边框（紫）
    private static final int OFFFRM   = SciSkin.OFF_GRAY;   // 离线边框（灰）
    private static final int NW = 100, NH = 52;
    private static final int SW = 88, SH = 30;        // 存储节点尺寸（m92 紧凑化，用户点名"还是太大"）
    private static final String[] KIND = {"绑定", "有线", "无线", "卫星", "离线", "终端", "接口"};
    private static final Item[] UPG = { ModItems.SPEED_UPGRADE, ModItems.COUNT_UPGRADE, ModItems.PARALLEL_UPGRADE };

    private static final java.util.Map<BlockPos, double[]> VIEW = new java.util.HashMap<>();

    private double panX = 0, panY = 0, zoom = 1.0;
    // ===== m186 缩放平滑动效（anime.js easeOutExpo 思路移植：速度∝剩余距离，帧率无关）=====
    private double zoomTarget = 1.0;           // 缓动目标缩放
    private boolean zoomAnim = false;          // 动效进行中
    private double zoomAnchorSx, zoomAnchorSy; // 锚点屏幕坐标（指哪缩哪）
    private double zoomAnchorWx, zoomAnchorWy; // 锚点世界坐标
    private long zoomAnimNs = 0;               // 上帧时间戳
    private boolean libOpen = false; // m88 机器库侧栏
    private int libScroll = 0;
    private boolean busCollapsed = false; // m91：总线收起（拉线时自动展开）
    private boolean busVisible() { return !busCollapsed || linking; }
    private static float busScale = -1f;   // m93：总线大小滑块；m215 起持久化到配置（-1=未从配置装载哨兵，init 装载）
    private boolean busScaleDrag = false;
    private static final float BUS_MIN = 0.55f, BUS_MAX = 1.25f; // m215 下限 0.8→0.55（作者点名"太大"，允许拖更小）
    private int bw() { return Math.round(SW * busScale); }
    private int bh() { return Math.round(SH * busScale); }

    // ===== m215 上下 chrome 紧凑化（配置开关，改后重开画布生效；所有底栏/总线纵向几何只许走这三口，不许再散写 78/44/12）=====
    private static boolean compactChrome() { return com.sdzjz.config.SdzjzConfig.get().canvasCompactChrome; }
    /** 底部横带上缘 y（旧版=height-78，紧凑=height-56）：画布剪刀/暗角/小地图/机器库底缘全部以此为准。 */
    private int botTop() { // m219 状态区可收：收起=按钮排+上下留白；展开=两行统计（提示行已迁"帮助"卡，56/78 时代的第三行位随之瘦掉）
        if (!com.sdzjz.config.SdzjzConfig.get().canvasStatusOpen) return this.height - (compactChrome() ? 26 : 30);
        return this.height - (compactChrome() ? 46 : 66);
    }
    /** m219 底部五钮摆位（init 与"状态"开合共用）。m222 改自适应居中（作者点名"对齐/自适应放置/自动居中"）：
     *  顺序装行、装不下换行（m182 口径：放得下的必是前缀，折行只在尾部），每行按可用宽水平居中；
     *  首行在带内(botTop()+4)，溢出行往带上方叠——旧固定 bbX{8,104,200,300,396} 左堆版退役。 */
    private void layoutBottomButtons() {
        if (bottomBtns == null) return;
        int gap = 6, avail = Math.max(60, workRight() - 16); // 可用宽=带内左右各留 8（带只画到 workRight()）
        int bbH = bottomBtns[0].getHeight();
        java.util.List<java.util.List<TermButton>> rows = new java.util.ArrayList<>();
        java.util.List<TermButton> cur = new java.util.ArrayList<>();
        int curW = 0;
        for (TermButton b : bottomBtns) {
            int need = (cur.isEmpty() ? 0 : gap) + b.getWidth();
            if (!cur.isEmpty() && curW + need > avail) { rows.add(cur); cur = new java.util.ArrayList<>(); curW = 0; need = b.getWidth(); }
            cur.add(b);
            curW += need;
        }
        if (!cur.isEmpty()) rows.add(cur);
        for (int r = 0; r < rows.size(); r++) {
            java.util.List<TermButton> row = rows.get(r);
            int tw = -gap;
            for (TermButton b : row) tw += b.getWidth() + gap;
            int x = 8 + Math.max(0, (avail - tw) / 2);
            int y = r == 0 ? botTop() + 4 : botTop() - (bbH + 4) * r; // 溢出行=带上方 4px 起往上叠（与画布剪刀 botTop 不撞，m182 口径）
            for (TermButton b : row) { b.setX(x); b.setY(y); x += b.getWidth() + gap; }
        }
    }

    /** 总线卡片首行 y（旧 44，紧凑 42=收起态带底同值）。 */
    private int busCardTop() { return compactChrome() ? 42 : 44; }
    /** 总线卡片行距（旧 bh+12，紧凑 bh+8）。 */
    private int busRowStep() { return bh() + (compactChrome() ? 8 : 12); }
    private int busTrackX() { return workRight() - 152; }
    private static final int BUS_TRACK_W = 104;
    private void busScaleFromMouse(double mx) {
        busScale = (float) Math.max(BUS_MIN, Math.min(BUS_MAX, BUS_MIN + (mx - busTrackX()) / BUS_TRACK_W * (BUS_MAX - BUS_MIN)));
    }

    // ===== m89：端点直发包缓存（BE 同步链实机不生效的最终修复）=====
    private static long endsCachePos = Long.MIN_VALUE;
    private static java.util.List<long[]> endsCache = java.util.List.of();
    private static java.util.List<String> endsDimsCache = java.util.List.of();
    private static java.util.List<String> busIdsCache = java.util.List.of();
    private static java.util.List<Long> busCountsCache = java.util.List.of();

    // m265 端点画布落位缓存（与 endsCache 同通道同拍；posLong→{x,y}，不在表=停靠）
    private static final java.util.Map<Long, int[]> homesCache = new java.util.HashMap<>();
    private static long homesCachePos = Long.MIN_VALUE;
    // m265 松手本地回声：40t 同步节奏下发包后卡会被旧包打回停靠闪 ≤2s（m196 同类同步打架），
    // 短时压住服务器旧值，到期自动让位——服务器 2s 内必然带着新值追上。
    private static final java.util.Map<Long, int[]> homesHold = new java.util.HashMap<>();
    private static final java.util.Map<Long, Long> homesHoldUntil = new java.util.HashMap<>();

    public static void applyHomesPayload(com.sdzjz.net.StorageNodeHomePayload p) {
        homesCache.clear();
        for (int i = 0; i < p.endPos().size() && i < p.nx().size() && i < p.ny().size(); i++)
            homesCache.put(p.endPos().get(i), new int[]{p.nx().get(i), p.ny().get(i)});
        homesCachePos = p.pos().asLong();
    }

    public static void applyEndsPayload(com.sdzjz.net.CanvasEndsPayload p) {
        java.util.List<long[]> e = new java.util.ArrayList<>();
        for (int i = 0; i < p.endPos().size() && i < p.endKind().size(); i++)
            e.add(new long[]{p.endPos().get(i), p.endKind().get(i)});
        endsCache = e;
        endsDimsCache = new java.util.ArrayList<>(p.endDim());
        busIdsCache = new java.util.ArrayList<>(p.busIds());
        busCountsCache = new java.util.ArrayList<>(p.busCounts());
        endsCachePos = p.pos().asLong();
    }

    private boolean cacheHit() {
        BlockPos p = this.handler.blockPos();
        return p != null && p.asLong() == endsCachePos;
    }

    private List<long[]> endsOf(StructureCoreBlockEntity be) {
        return cacheHit() ? endsCache : be.storageEndpointsView();
    }

    private java.util.List<String> endDimsOf(StructureCoreBlockEntity be) {
        return cacheHit() ? endsDimsCache : be.storageEndpointDimsView();
    }

    private java.util.List<String> busIdsOf(StructureCoreBlockEntity be) {
        return cacheHit() ? busIdsCache : be.busTopIdsView();
    }

    private java.util.List<Long> busCountsOf(StructureCoreBlockEntity be) {
        return cacheHit() ? busCountsCache : be.busTopCountsView();
    }

    // ===== m265 端点画布落位：读取口（回声 hold > 服务器缓存 > BE 后备；总开关关=全停靠）=====
    private boolean endsPlaceableOn() { return com.sdzjz.config.SdzjzConfig.get().canvasEndsPlaceable; }

    /** 已放置端点的画布(世界)坐标；null=停靠在总线带。 */
    private int[] endHome(StructureCoreBlockEntity be, long pl) {
        if (!endsPlaceableOn()) return null;
        Long until = homesHoldUntil.get(pl);
        if (until != null) {
            if (System.currentTimeMillis() <= until) return homesHold.get(pl); // hold 值为 null=按住"停靠"
            homesHoldUntil.remove(pl); homesHold.remove(pl);
        }
        BlockPos p = this.handler.blockPos();
        if (p != null && p.asLong() == homesCachePos) return homesCache.get(pl);
        return be != null && be.storageNodePlaced(pl)
                ? new int[]{be.storageNodeX(pl, 0), be.storageNodeY(pl, 0)} : null; // 缓存未热身走 BE 后备（m89 口径）
    }

    private boolean endPlaced(StructureCoreBlockEntity be, long pl) { return endHome(be, pl) != null; }

    /** 松手本地回声（2.5s > 40t 同步周期）：home=null 表示按住"已收回停靠"。 */
    private static void holdHome(long pl, int[] home) {
        homesHold.put(pl, home);
        homesHoldUntil.put(pl, System.currentTimeMillis() + 2500L);
    }
    private int dragIndex = -1;
    private long dragStor = Long.MIN_VALUE;
    // m265 端点卡拖拽（拖出总线带=放置画布 / 拖回带内=收回停靠）
    private long dragEnd = Long.MIN_VALUE;   // 拖动中的端点 posLong
    private double dragEndOffX, dragEndOffY; // 按下点相对卡左上角的屏幕偏移
    private int dragEndX, dragEndY;          // 拖动中的屏幕坐标（渲染/命中唯一真源，m196 口径）
    private double dragEndPressX, dragEndPressY; // 按下点（<4px 视为点击不动卡）
    private boolean dragEndWasPlaced;        // 起手时是否已放置（决定微动=原样归位语义）
    private double dragOffX, dragOffY;
    private int dragCurX, dragCurY; // m196 拖动中的屏幕本地坐标（渲染/发包唯一真源）——BE 坐标拖动中会被服务器全量同步打回旧值，读它=闪跳；从它回读发包=同步恰好盖掉就把旧坐标发回去=永久漂移
    private boolean linking = false;
    private int linkFrom = -1;                        // 机器输出口起点
    private long linkStor = Long.MIN_VALUE;           // 存储供料口起点

    // ===== m192 画布分组：Shift框选 + 组框（数据/协议见 m191；配置 canvasGroupsEnabled 总开关）=====
    private final java.util.LinkedHashSet<Integer> selected = new java.util.LinkedHashSet<>(); // 选中节点下标（客户端瞬态）
    private boolean boxSelecting = false;
    private double boxX0, boxY0, boxX1, boxY1;        // 框选矩形（世界坐标）
    private int dragGid = -1;                         // 拖动中的组
    private double dragGidWx, dragGidWy;              // 组拖动起点（世界坐标）
    private int dragGidDx = 0, dragGidDy = 0;         // 已应用整数位移（松手随包发出）
    private final java.util.HashMap<Integer, int[]> dragGidSnap = new java.util.HashMap<>(); // 成员坐标快照：快照+增量绝对写，中途被服务端全量同步覆盖也自愈
    private int renameGid = -1;                       // 重命名中的组（>=0 = 小窗开着）
    private TextFieldWidget renameField;
    private static final int GPAD = 10, GBAND = 16;   // 组框内边距 / 标题带高（世界单位）

    // 右键菜单
    private boolean menuOpen = false;
    private int menuX, menuY;
    private final List<String> menuLabels = new ArrayList<>();
    private final List<Runnable> menuActions = new ArrayList<>();
    private final List<ItemStack> menuIcons = new ArrayList<>();   // m148 行图标（可空）
    private final List<net.minecraft.util.Identifier> menuTexs = new ArrayList<>(); // m313 用户设计贴图图标（可空，优先于物品图标）
    private double lastMouseX, lastMouseY; // m313 快捷键悬停命中用（render 每帧缓存）
    private final List<Integer> menuStyles = new ArrayList<>();    // m148 0普通 1危险(红) 2组首(上加分隔线)
    private String menuTitle = null;                               // m148 标题带（节点菜单=机器名）
    private long menuOpenMs = 0;                                   // m148 开合动画时钟
    private float[] menuHoverP = new float[0];                     // m148 逐行悬停缓动进度
    private long pickerOpenMs = 0;                                 // m148 选择器淡入时钟
    private List<Item> pickerSrcOverride = null;                   // m149 机器加工过滤的候选源（null=常规）
    private String pickerTitleOverride = null;                     // m149 机器加工过滤的窗题
    private static final int MENU_W = 144, MENU_H = 18, MENU_TITLE_H = 14; // m148 加宽容图标+标题带；m316 136→144 容图标间距加宽后的最长行（抽取量挡位）

    // ===== m199 画布设置面板（游戏内可调画布客户端项；modal 照 renameField 在树写法）=====
    private boolean settingsOpen = false;
    private TextFieldWidget wireOutField, wireInField;   // 出/进线颜色 RRGGBB：live 写配置实例即时预览，关窗落盘
    private TextFieldWidget bgField, gridColField;       // m217 背景色/网格色 RRGGBB：空=跟随主题；live 写实例即时预览
    private TermButton[] bottomBtns;                      // m219 底部五钮存引用："状态"钮切换后就地重摆（botTop 变高矮）
    private boolean helpOpen = false;                     // m219 帮助卡（操作提示行迁入，modal 同吞穿透口径）
    private static final int SETT_W = 236, SETT_H = 300, SETT_ROW = 18; // m262 再紧凑：行距 20→18/行区起点 24→20/滑杆内距 14→13/预设与按钮间距各收 2~3/底注释挪标题行，334→300（作者点名还是太大；m239 首轮 394→334）
    private static final int SETT_ROW0 = 20;                       // 行区起点 y 偏移（m262 收口：渲染/点击原各硬编码 24）
    // m223 颜色行 RGB 滑杆调节区（作者点名照终端主题编辑器"图六那样，不能让我自己输入"；渲染/点击几何共用以下常量，改必同改）
    private static final int SETT_SL_Y = SETT_ROW0 + 10 * SETT_ROW + 2; // 调节区头行 y 偏移（=202）
    private static final int SETT_SL_SP = 13;                     // 滑杆内距（m239 收口常量 17→14，m262 再收 13：轨高 12+1px 间隙）
    private static final int SETT_PRESET_Y = SETT_SL_Y + 14 + 3 * SETT_SL_SP + 7; // 主题预设行 y 偏移（=262）
    private static final int SETT_RESET_Y = SETT_PRESET_Y + 19;   // 恢复默认钮 y 偏移（=281；钮 16 高+3 底距=300 收口）
    private static final int SETT_SL_X = 32, SETT_SL_W = 146;     // 滑轨 x 偏移/宽（照 m202 终端滑杆工艺）
    private static final String[] SETT_COLOR_NAMES = {"出线颜色", "进线颜色", "背景色", "网格色"}; // 调节目标名（序=setFs 序）
    private int settColSel = 2;   // m223 调节目标：0出线 1进线 2背景 3网格（默认背景色，作者截图点名的行）
    private int settSlDrag = -1;  // m223 拖动中的通道 0R/1G/2B；-1=没拖

    // m110a 小地图（纯客户端零协议）
    private boolean mapOpen = false;
    private boolean mapDragging = false;
    private double[] mapGeomDrag;                     // 拖拽期间用抓取时的几何快照，防视口移动导致反馈抖动
    private static final int MAP_W = 148, MAP_H = 100;

    // 自动合成机目标选择器
    private int pickerNode = -1;
    private int pickerMode = 0; // 0=合成目标 1=过滤名单(多选) 2=传感器监测物品 3=作物多选 4=药水目标(m131b)
    private int brewForm = 0; // m131b 药水形态：0=普通 1=喷溅 2=滞留
    private java.util.List<net.minecraft.util.Identifier> potionIds; // 全药水注册id（一次构建）
    private final List<ItemStack> potionFiltered = new ArrayList<>();
    private final List<String> potionFilteredIds = new ArrayList<>(); // 与上表对齐的完整目标串 id|形态
    // m132 附魔目标选择器：附魔书图标全长一个样，网格式没法认——改行式（图标+原版名字，罗马数字/诅咒红字自带）。
    // 全表每次开窗按当前世界动态注册表重建（附魔随存档/数据包变，不做跨世界静态缓存）。
    private java.util.List<String> enchAllIds;                        // 全部目标串 附魔id|等级（同附魔等级降序）
    private java.util.List<net.minecraft.text.Text> enchAllNames;
    private final List<ItemStack> enchFiltered = new ArrayList<>();   // 行样板栈（绿框/悬停用）
    private final List<String> enchFilteredIds = new ArrayList<>();
    private java.util.List<String> tradeAllIds;                       // m146 全部交易目标串 职业|序号
    private final List<ItemStack> tradeFiltered = new ArrayList<>();
    private final List<String> tradeFilteredIds = new ArrayList<>();
    private final List<net.minecraft.text.Text> tradeFilteredNames = new ArrayList<>();
    private final List<net.minecraft.text.Text> enchFilteredNames = new ArrayList<>();
    private static final int ENCH_ROW_H = 18, ENCH_ROWS = 8;
    private TextFieldWidget pickerField;
    private List<Item> craftables;
    private List<Item> allItems;
    private final List<Item> pickerFiltered = new ArrayList<>();
    private static final int PICK_W = 226, PICK_H = 210, PICK_COLS = 10, PICK_ROWS = 7;

    public StructureCoreScreen(StructureCoreScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 360;
        this.backgroundHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        // m121 画布全屏：把 GUI 占位声明成整屏（x/y=0）——JEI/REI 只往界面右侧空余区放列表，
        // 判定没空间就自动隐藏；本 handler 零槽位，挪 x/y 不影响任何槽渲染。背包等物品界面 JEI 照常。
        this.x = 0; this.y = 0;
        this.backgroundWidth = this.width;
        this.backgroundHeight = this.height;
        BlockPos p = this.handler.blockPos();
        if (p != null && VIEW.containsKey(p)) {
            double[] v = VIEW.get(p);
            setViewInstant(v[0], v[1], v[2]); // m186 恢复视图走直设，顺带对齐动效目标
        }
        // m182 底栏五钮防溢出→m222 自适应居中：顺序装行装不下折行（放得下的必是前缀），每行水平居中，
        // 首行带内、溢出行往带上方叠——摆位唯一家=layoutBottomButtons()，旧固定坐标 8/104/200/300/396 退役。
        if (busScale < 0) busScale = (float) Math.max(BUS_MIN, Math.min(BUS_MAX, com.sdzjz.config.SdzjzConfig.get().canvasBusScale)); // m215 首次从配置装载
        int[] bbW = {90, 90, 96, 92, 92}; // 坐标 bbX 唯一家=layoutBottomButtons()（m219 搬家，防散写）
        int bbH = compactChrome() ? 16 : 20; // m215 紧凑钮高
        bottomBtns = new TermButton[]{ // m203 画布横幅按钮换终端主题控件；m219 存引用可重摆
                new TermButton(0, 0, bbW[0], bbH, Text.literal("▶ 开机"), b -> click(0), true), // m203 主紫
                new TermButton(0, 0, bbW[1], bbH, Text.literal("■ 停止"), b -> click(1)),
                new TermButton(0, 0, bbW[2], bbH, Text.literal("★ 领取经验"), b -> click(2)),
                new TermButton(0, 0, bbW[3], bbH, Text.literal("整理布局"), b -> autoLayout()), // m85 概念图底栏
                new TermButton(0, 0, bbW[4], bbH, Text.literal("重置视角"), b -> setViewInstant(0, 0, 1.0))
        };
        layoutBottomButtons();
        for (TermButton btn : bottomBtns) this.addDrawableChild(btn);
        this.addDrawableChild(new TermButton(132, 2, 60, 16, Text.literal("机器库"), b -> libOpen = !libOpen)); // m88
        this.addDrawableChild(new TermButton(196, 2, 44, 16, Text.literal("地图"), b -> mapOpen = !mapOpen)); // m110a
        this.addDrawableChild(new TermButton(244, 2, 44, 16, Text.translatable("sdzjz.canvas.settings"), // m199 画布设置面板；244+44=288≤312，320 最小视口安全边距内（m182 口径核过）
                b -> { if (settingsOpen) closeSettings(); else openSettings(); }));
        this.addDrawableChild(new TermButton(292, 2, 40, 16, Text.translatable("sdzjz.canvas.status"), // m219 状态区开合（即点即存；320 极窄视口下与右簇重叠属既有降级，见 DEVLOG）
                b -> { com.sdzjz.config.SdzjzConfig.get().canvasStatusOpen = !com.sdzjz.config.SdzjzConfig.get().canvasStatusOpen;
                       com.sdzjz.config.SdzjzConfig.save(); layoutBottomButtons(); }));
        this.addDrawableChild(new TermButton(336, 2, 40, 16, Text.translatable("sdzjz.canvas.help"), // m219 帮助卡（操作提示行迁入）
                b -> helpOpen = !helpOpen));
        int wr2 = this.width - 8; // m121 视图控制随全屏右移
        this.addDrawableChild(new TermButton(wr2 - 170, 2, 16, 16, Text.literal("−"), b -> zoomBy(1 / 1.2)));
        this.addDrawableChild(new TermButton(wr2 - 106, 2, 16, 16, Text.literal("+"), b -> zoomBy(1.2)));
        this.addDrawableChild(new TermButton(wr2 - 86, 2, 78, 16, Text.literal("适应视图"), b -> fitView()));
        String keep = pickerField != null ? pickerField.getText() : "";
        this.pickerField = new TextFieldWidget(this.textRenderer, 0, 0, PICK_W - 16, 14, Text.literal("搜索"));
        this.pickerField.setChangedListener(t -> refilterPicker());
        this.pickerField.setText(keep);
        String keepG = renameField != null ? renameField.getText() : ""; // m192 组重命名输入框（照 pickerField 在树写法）
        this.renameField = new TextFieldWidget(this.textRenderer, 0, 0, 184, 14, Text.empty()); // 占位仅narration不上屏，empty保literal棘轮
        this.renameField.setMaxLength(24);
        this.renameField.setText(keepG);
        String keepO = wireOutField != null ? wireOutField.getText() : com.sdzjz.config.SdzjzConfig.get().canvasWireOutColor; // m199 颜色框：重排保留输入，首建取配置现值
        this.wireOutField = new TextFieldWidget(this.textRenderer, 0, 0, 58, 14, Text.empty()); // 占位仅narration，empty保literal棘轮（m192 教训）
        this.wireOutField.setMaxLength(7);
        this.wireOutField.setText(keepO == null ? "" : keepO);
        this.wireOutField.setChangedListener(t -> com.sdzjz.config.SdzjzConfig.get().canvasWireOutColor = t.trim()); // live 写实例=连线即时预览（SciSkin 串比缓存自动重解析）；落盘在关窗
        String keepI = wireInField != null ? wireInField.getText() : com.sdzjz.config.SdzjzConfig.get().canvasWireInColor;
        this.wireInField = new TextFieldWidget(this.textRenderer, 0, 0, 58, 14, Text.empty());
        this.wireInField.setMaxLength(7);
        this.wireInField.setText(keepI == null ? "" : keepI);
        this.wireInField.setChangedListener(t -> com.sdzjz.config.SdzjzConfig.get().canvasWireInColor = t.trim());
        String keepB = bgField != null ? bgField.getText() : com.sdzjz.config.SdzjzConfig.get().canvasBgColor; // m217 背景色/网格色框（wire 同款：live 写实例即时预览，落盘在关窗）
        this.bgField = new TextFieldWidget(this.textRenderer, 0, 0, 58, 14, Text.empty());
        this.bgField.setMaxLength(7);
        this.bgField.setText(keepB == null ? "" : keepB);
        this.bgField.setChangedListener(t -> com.sdzjz.config.SdzjzConfig.get().canvasBgColor = t.trim());
        String keepGc = gridColField != null ? gridColField.getText() : com.sdzjz.config.SdzjzConfig.get().canvasGridColor;
        this.gridColField = new TextFieldWidget(this.textRenderer, 0, 0, 58, 14, Text.empty());
        this.gridColField.setMaxLength(7);
        this.gridColField.setText(keepGc == null ? "" : keepGc);
        this.gridColField.setChangedListener(t -> com.sdzjz.config.SdzjzConfig.get().canvasGridColor = t.trim());
    }

    @Override
    public void removed() {
        if (zoomAnim) { zoom = zoomTarget; panX = zoomAnchorSx - zoomAnchorWx * zoom; panY = zoomAnchorSy - zoomAnchorWy * zoom; zoomAnim = false; } // m186 结算未完动效再存视图
        BlockPos p = this.handler.blockPos();
        if (p != null) VIEW.put(p, new double[]{panX, panY, zoom});
        if (settingsOpen) com.sdzjz.config.SdzjzConfig.save(); // m199 设置窗开着直接关屏也把颜色改动落盘
        super.removed();
    }

    private void click(int id) {
        if (this.client != null && this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(this.handler.syncId, id);
        }
    }

    private StructureCoreBlockEntity be() {
        BlockPos p = this.handler.blockPos();
        if (p != null && this.client != null && this.client.world != null
                && this.client.world.getBlockEntity(p) instanceof StructureCoreBlockEntity c) return c;
        return null;
    }

    private double wmx(double mx) { return (mx - panX) / zoom; }
    private double wmy(double my) { return (my - panY) / zoom; }
    // m196 拖动中覆盖：单卡读 dragCur、组成员读快照+增量——渲染永不读会被同步打回的 BE 坐标，根治"闪两个位置来回跳"
    private int wnx(StructureCoreBlockEntity be, List<ItemStack> nodes, int i) {
        if (i == dragIndex) return dragCurX;
        if (dragGid >= 0) { int[] s = dragGidSnap.get(i); if (s != null) return s[0] + dragGidDx; }
        return be.nodeX(nodes.get(i), 20 + (i % 6) * 112);
    }
    private int wny(StructureCoreBlockEntity be, List<ItemStack> nodes, int i) {
        if (i == dragIndex) return dragCurY;
        if (dragGid >= 0) { int[] s = dragGidSnap.get(i); if (s != null) return s[1] + dragGidDy; }
        return be.nodeY(nodes.get(i), 20 + (i / 6) * 88);
    }
    /** m85：画布 UI 的右边界——右侧留空给 JEI/REI 物品栏（用户点名），所有屏幕锚定元素不越界。 */
    private int workRight() { return this.width - 8; } // m121 撤 m85 的 JEI 预留：画布全屏，JEI 已随全屏声明自动隐藏

    /** m86 节点分类配色（概念图）：紫=逻辑 橙=加工(消耗输入) 绿=农场 青=生产(免费)。 */
    private int nodeAccent(ItemStack st) {
        if (StructureCoreBlockEntity.isFilter(st) || StructureCoreBlockEntity.isSensor(st)
                || StructureCoreBlockEntity.isSwitch(st) || StructureCoreBlockEntity.isDistributor(st)) return 0xFFB06AE8;
        if (st.getItem() instanceof com.sdzjz.item.CropFarmItem) return 0xFF63D06A;
        if (st.getItem() instanceof com.sdzjz.item.MachineItem mi && mi.def().consumesInputs()) return 0xFFE8963C;
        return CYAN;
    }

    /** m88 机器库：背包里可入画布的物品去重合并计数。 */
    private List<ItemStack> libItems() {
        List<ItemStack> out = new java.util.ArrayList<>();
        if (this.client == null || this.client.player == null) return out;
        java.util.LinkedHashMap<net.minecraft.item.Item, Integer> m2 = new java.util.LinkedHashMap<>();
        var inv = this.client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty() || !nodeInsertable(st)) continue;
            m2.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        for (var e : m2.entrySet()) out.add(new ItemStack(e.getKey(), e.getValue()));
        return out;
    }

    private boolean nodeInsertable(ItemStack st) {
        return st.getItem() instanceof com.sdzjz.item.MachineItem
                || st.getItem() instanceof com.sdzjz.item.CropFarmItem
                || (st.getItem() instanceof com.sdzjz.item.CaptureCageItem && com.sdzjz.item.CaptureCageItem.isCaged(st))
                || StructureCoreBlockEntity.isFilter(st) || StructureCoreBlockEntity.isSensor(st)
                || StructureCoreBlockEntity.isSwitch(st) || StructureCoreBlockEntity.isDistributor(st);
    }

    /** m87：文本按宽度截断，尾加省略号——底栏任何文字不越 JEI 界。 */
    private String fitText(String t, int maxW) {
        if (this.textRenderer.getWidth(t) <= maxW) return t;
        while (!t.isEmpty() && this.textRenderer.getWidth(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    /** m185 缩放钳位统一出口：范围走配置（默认 5%~800%），下限兜底 0.01 防除零；配置写反自动纠序。 */
    private double clampZoom(double z) {
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get();
        double lo = Math.max(0.01, Math.min(c.canvasZoomMin, c.canvasZoomMax));
        double hi = Math.max(lo, Math.max(c.canvasZoomMin, c.canvasZoomMax));
        return Math.max(lo, Math.min(hi, z));
    }

    /** m186 视图直设：重置/适应/恢复等瞬时路径统一走这里，顺手终止缩放动效防隔帧抢写。 */
    private void setViewInstant(double px, double py, double z) {
        panX = px; panY = py; zoom = z; zoomTarget = z; zoomAnim = false;
    }

    /** m186 朝目标缩放：锚点 (sx,sy) 屏幕点始终指着同一世界点；连滚累积在目标上；配置关平滑=瞬时跳变（旧行为）。 */
    private void zoomToward(double factor, double sx, double sy) {
        double nz = clampZoom((zoomAnim ? zoomTarget : zoom) * factor);
        double wx = (sx - panX) / zoom, wy = (sy - panY) / zoom;
        if (!com.sdzjz.config.SdzjzConfig.get().canvasSmoothZoom) {
            zoom = nz; zoomTarget = nz; zoomAnim = false;
            panX = sx - wx * nz; panY = sy - wy * nz;
            return;
        }
        zoomAnchorSx = sx; zoomAnchorSy = sy; zoomAnchorWx = wx; zoomAnchorWy = wy;
        zoomTarget = nz;
        if (!zoomAnim) { zoomAnim = true; zoomAnimNs = System.nanoTime(); }
    }

    /** m186 每帧推进：指数趋近（1-e^{-14·dt}，半衰≈50ms），收敛吸附；锚点公式保证屏幕锚点纹丝不动。 */
    private void tickZoomAnim() {
        if (!zoomAnim) return;
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - zoomAnimNs) / 1.0e9);
        zoomAnimNs = now;
        zoom += (zoomTarget - zoom) * (1 - Math.exp(-14.0 * dt));
        if (Math.abs(zoomTarget - zoom) < zoomTarget * 0.002) { zoom = zoomTarget; zoomAnim = false; }
        panX = zoomAnchorSx - zoomAnchorWx * zoom;
        panY = zoomAnchorSy - zoomAnchorWy * zoom;
    }

    /** m86 视图控制：围绕工作区中心缩放。 */
    private void zoomBy(double f) {
        zoomToward(f, workRight() / 2.0, this.height / 2.0); // m186 走平滑缓动
    }

    /** m86 适应视图：所有节点装进 总线下缘~底栏 之间的可视区。 */
    private void fitView() {
        StructureCoreBlockEntity be = be();
        if (be == null || be.nodes().isEmpty()) { setViewInstant(0, 0, 1.0); return; }
        List<ItemStack> nodes = be.nodes();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            minX = Math.min(minX, nx); minY = Math.min(minY, ny);
            maxX = Math.max(maxX, nx + NW); maxY = Math.max(maxY, ny + NH + 28); // 含升级格
        }
        int top = 118, bottom = this.height - 86, left = 12, right = workRight() - 12;
        double zw = (right - left) / (double) Math.max(1, maxX - minX);
        double zh = (bottom - top) / (double) Math.max(1, maxY - minY);
        zoom = clampZoom(Math.min(zw, zh)); // m185 范围走配置
        panX = left + ((right - left) - (maxX - minX) * zoom) / 2 - minX * zoom;
        panY = top + ((bottom - top) - (maxY - minY) * zoom) / 2 - minY * zoom;
        zoomTarget = zoom; zoomAnim = false; // m186 直设视图终止动效
    }

    // m80：端点按用户点名改为顶部「存储总线」横排（屏幕坐标，永远可见），行满向下换行。
    private int busCols() { return Math.max(1, (workRight() - 24) / (bw() + 14)); }
    // m265 端点卡屏幕坐标三态：拖动中=本地覆盖坐标（m196 口径，防同步打架）＞已放置=世界坐标投影
    // （卡体尺寸恒定不随缩放，锚点跟着画布平移/缩放走）＞停靠=总线带排位（m80 原样）。
    // 全部连线/命中/绘制统一走这两口，放置态自动全链路生效。
    private int snx(StructureCoreBlockEntity be, long pl, int j) {
        if (dragEnd != Long.MIN_VALUE && pl == dragEnd) return dragEndX;
        int[] h = endHome(be, pl);
        if (h != null) return (int) Math.round(panX + h[0] * zoom);
        return 14 + (j % busCols()) * (bw() + 14);
    }
    private int sny(StructureCoreBlockEntity be, long pl, int j) {
        if (dragEnd != Long.MIN_VALUE && pl == dragEnd) return dragEndY;
        int[] h = endHome(be, pl);
        if (h != null) return (int) Math.round(panY + h[1] * zoom);
        return busCardTop() + (j / busCols()) * busRowStep();
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        tickZoomAnim(); // m186 缩放动效每帧推进（先于一切使用 pan/zoom 的绘制）
        ctx.fill(0, 0, this.width, this.height, SciSkin.canvasBg()); // m203 全屏底随主题；m217 配置可覆盖（空=跟随主题墨色）
        if (com.sdzjz.config.SdzjzConfig.get().canvasBgDecor && !SciSkin.canvasBgOverridden()) // m220 设了背景色=纯色画布，装饰底图让位（作者截图：改色无感+装饰边碍眼同根）；canvasBgDecor=false 可无条件关
            ctx.drawTexture(FRAME, 0, 0, 0.0F, 0.0F, this.width, this.height, this.width, this.height);
        SciSkin.termBand(ctx, 0, 0, workRight(), 22); // m203 顶条换终端主题浅带（照作者画布设计稿）
        SciSkin.termBandLine(ctx, 0, workRight(), 22);
        // m210 底栏浅带迁到背景层：HandledScreen 帧序=背景→按钮→前景，m203 的不透明浅带留在前景层
        // 把底部五钮整排糊死（作者实机截图实锤）；机器区剪刀(24~height-78)挡着，前置安全。
        SciSkin.termBand(ctx, 0, botTop(), workRight(), this.height);
        SciSkin.termBandLine(ctx, 0, workRight(), botTop());
        // m188 网格双色制：细线打底 + 每4格一根主线；相位按世界格序号（floorMod）定，平移不跳档
        int step = 32;
        int gi0 = (int) Math.floor(-panX / step), gi1 = (int) Math.floor((this.width - panX) / step);
        for (int gi = gi0; gi <= gi1; gi++) {
            int gsx = (int) Math.floor(panX + gi * (double) step);
            ctx.fill(gsx, 34, gsx + 1, this.height,
                    Math.floorMod(gi, 4) == 0 ? SciSkin.termGridMajor() : SciSkin.termGridMinor()); // m203 网格随主题强调色
        }
        int gj0 = (int) Math.floor((34 - panY) / step), gj1 = (int) Math.floor((this.height - panY) / step);
        for (int gj = gj0; gj <= gj1; gj++) {
            int gsy = (int) Math.floor(panY + gj * (double) step);
            if (gsy >= 34) ctx.fill(0, gsy, this.width, gsy + 1,
                    Math.floorMod(gj, 4) == 0 ? SciSkin.termGridMajor() : SciSkin.termGridMinor());
        }
        SciSkin.vignette(ctx, 0, 23, workRight(), botTop()); // m188 四缘暗角压景深（卡片/连线画在其上不受影响）

        StructureCoreBlockEntity be = be();
        if (be == null) return;
        List<ItemStack> nodes = be.nodes();
        List<long[]> ends = endsOf(be);

        // m164b 悬停聚焦（用户点名"线看着还是乱"）：指着哪张卡，只有它的线保持全亮，其余压暗成
        // 三成底色——一眼看清单张卡的进出走向；不指任何卡=全亮照旧，零学习成本。压暗走
        // SciSkin.mix 向底色靠拢（wirePath 会丢传入 alpha，改 alpha 无效——见其 rgb=color&0xFFFFFF）。
        int hovN = -1; long hovEnd = Long.MIN_VALUE;
        {
            double hx = wmx(mouseX), hy = wmy(mouseY);
            for (int i = nodes.size() - 1; i >= 0; i--) {
                int nxH = wnx(be, nodes, i), nyH = wny(be, nodes, i);
                if (hx >= nxH && hx <= nxH + NW && hy >= nyH && hy <= nyH + NH + 26) { hovN = i; break; }
            }
            if (hovN < 0) for (int j = 0; j < ends.size(); j++) { // m265 放置卡收起态也参与悬停
                if (!busVisible() && !endPlaced(be, ends.get(j)[0])) continue;
                int sxH = snx(be, ends.get(j)[0], j), syH = sny(be, ends.get(j)[0], j);
                if (mouseX >= sxH && mouseX <= sxH + bw() && mouseY >= syH && mouseY <= syH + bh()) { hovEnd = ends.get(j)[0]; break; }
            }
        }
        boolean hovAny = hovN >= 0 || hovEnd != Long.MIN_VALUE;

        // m263 总线带底缘（收起=44，展开随端点行数走）——机器层剪刀顶缘用它：drawItem 自带 z 偏移(~150)，
        // 卡片滑进带下时图标会穿透后画的 z0 带填充（m159 底栏同病，作者截图：物品图穿模顶部栏）。
        // 机器层直接裁到带底 = 带区内不再有任何机器像素可穿；带本体改在机器层之后按原 24 剪刀单独画。
        int bandBot = Math.min(busVisible()
                ? busCardTop() + Math.max(1, (ends.size() + busCols() - 1) / busCols()) * busRowStep() + 2
                : 44, botTop());
        ctx.enableScissor(0, bandBot, workRight(), botTop()); // m159 画布剪刀（m263 顶缘 24→带底）
        // m193 分组共享表一次算好：组成员 / 组框矩形 / 节点→组查表（组框渲染与连线归并共用）
        java.util.LinkedHashMap<Integer, java.util.List<Integer>> gm =
                groupsOn() ? groupMembers(be) : new java.util.LinkedHashMap<>();
        java.util.HashMap<Integer, int[]> gRect = new java.util.HashMap<>();
        int[] nGid = new int[nodes.size()];
        java.util.Arrays.fill(nGid, -1);
        for (var ge : gm.entrySet()) {
            gRect.put(ge.getKey(), groupRect(be, nodes, ge.getValue()));
            for (int gi2 : ge.getValue()) if (gi2 < nGid.length) nGid[gi2] = ge.getKey();
        }
        boolean bundleOn = !gm.isEmpty() && com.sdzjz.config.SdzjzConfig.get().canvasGroupBundleWires; // m193 归并开关
        // m192 组框（最底层：存储线/机器线/卡片全画其上）——世界坐标独立一次变换
        if (!gm.isEmpty()) {
            MatrixStack mg = ctx.getMatrices();
            mg.push();
            mg.translate(panX, panY, 0);
            mg.scale((float) zoom, (float) zoom, 1);
            for (var ge : gm.entrySet()) {
                int[] r = gRect.get(ge.getKey());
                int fc = dragGid == ge.getKey() ? CYAN : SciSkin.GROUP_FRM; // 拖动中框提亮
                ctx.fill(r[0], r[1] + GBAND, r[2], r[3], SciSkin.GROUP_FILL);
                SciSkin.hGrad(ctx, r[0], r[1], r[2], r[1] + GBAND,
                        SciSkin.withAlpha(SciSkin.GROUP_FRM, 0.50f), SciSkin.withAlpha(SciSkin.GROUP_FRM, 0.08f));
                ctx.fill(r[0], r[1], r[2], r[1] + 1, fc);
                ctx.fill(r[0], r[3] - 1, r[2], r[3], fc);
                ctx.fill(r[0], r[1], r[0] + 1, r[3], fc);
                ctx.fill(r[2] - 1, r[1], r[2], r[3], fc);
                ctx.fill(r[0], r[1] + GBAND - 1, r[2], r[1] + GBAND, SciSkin.withAlpha(fc, 0.45f));
                ctx.drawText(this.textRenderer, be.groupsView().getOrDefault(ge.getKey(), "组" + ge.getKey())
                        + " ×" + ge.getValue().size(), r[0] + 5, r[1] + 4, SciSkin.TXT_HI, false);
            }
            mg.pop();
        }
        // m136 存储定向连线（屏幕坐标）提前到节点卡片之前——线走卡片下层不再盖脸；
        // 总线端切线改垂直（线从下方垂直接入卡底端口，不再横着怼），机器端保持水平出入。
        // m184 机器端左右缘按几何就近选（卡口在机器哪一侧就从哪缘出入），端口语义（卡底左收料/右供料）不变。
        java.util.LinkedHashMap<String, int[]> seBundles = new java.util.LinkedHashMap<>(); // m193 (组|端点|向)→{数,亮}
        if (busVisible()) for (long[] e : be.storageEdgesView()) {
            int mi = (int) e[0];
            if (mi >= nodes.size()) continue;
            int j = endpointIndex(ends, e[1]);
            if (j < 0) continue; // 端点不在列表=不画，杜绝悬空线
            boolean lit = !hovAny || mi == hovN || e[1] == hovEnd; // m164b 悬停聚焦
            if (bundleOn && nGid[mi] >= 0) { // m193 组成员的存储线→归并，后面按组框画一条
                int[] acc = seBundles.computeIfAbsent(nGid[mi] + "|" + e[1] + "|" + e[2], k -> new int[]{0, 0});
                acc[0]++;
                if (lit) acc[1] = 1;
                continue;
            }
            int sx = snx(be, e[1], j), sy = sny(be, e[1], j);
            float mys = (float) (panY + (wny(be, nodes, mi) + NH / 2.0) * zoom);
            float mcx = (float) (panX + (wnx(be, nodes, mi) + NW / 2.0) * zoom); // m184 机器中心屏幕x：选缘看几何
            if (e[2] == 0) { // 机器→存储（产出）：机器近侧缘水平出线 → 垂直向上接入卡底左收料口（m184 选缘看几何，卡口在哪侧就走哪缘，反向不再绕背后大圈）
                boolean er = sx + 14 >= mcx; // 收料口在机器右侧→右缘出线，否则左缘出线
                float mxs = (float) (panX + (wnx(be, nodes, mi) + (er ? NW : 0)) * zoom);
                drawWire(ctx, mxs, mys, er ? 1 : -1, 0, sx + 14, sy + bh() + 2, 0, -1,
                        lit ? SciSkin.wireOut() : SciSkin.mix(SciSkin.termInk(), SciSkin.wireOut(), 0.30f), 1f); // m198 出线配置色
            } else {         // 存储→机器（供料）：卡底右供料口垂直下发 → 水平接入机器近侧缘（m184 同上）
                boolean fr = sx + bw() - 14 >= mcx; // 供料口在机器右侧→从右缘进（行进方向向左），否则左缘进
                float mxi = (float) (panX + (wnx(be, nodes, mi) + (fr ? NW : 0)) * zoom);
                drawWire(ctx, sx + bw() - 14, sy + bh() + 2, 0, 1, mxi, mys, fr ? -1 : 1, 0,
                        lit ? SciSkin.wireIn() : SciSkin.mix(SciSkin.termInk(), SciSkin.wireIn(), 0.30f), 1f); // m198 进线配置色
            }
        }
        for (var en : seBundles.entrySet()) { // m193 组↔存储归并线：组框缘（世界→屏幕）到端点口，一对一条
            String[] kp = en.getKey().split("\\|");
            int[] r = gRect.get(Integer.parseInt(kp[0]));
            long pl = Long.parseLong(kp[1]);
            int j = endpointIndex(ends, pl);
            if (r == null || j < 0) continue;
            int sx = snx(be, pl, j), sy = sny(be, pl, j);
            float gys = (float) (panY + ((r[1] + GBAND + r[3]) / 2.0) * zoom);
            float gcx = (float) (panX + ((r[0] + r[2]) / 2.0) * zoom);
            boolean lit = en.getValue()[1] != 0;
            float bx2, by2;
            if (kp[2].equals("0")) { // 组→存储（产出）
                boolean er = sx + 14 >= gcx;
                float gxs = (float) (panX + (er ? r[2] : r[0]) * zoom);
                bx2 = sx + 14; by2 = sy + bh() + 2;
                drawWire(ctx, gxs, gys, er ? 1 : -1, 0, bx2, by2, 0, -1,
                        lit ? SciSkin.wireOut() : SciSkin.mix(SciSkin.termInk(), SciSkin.wireOut(), 0.30f), 1f); // m198 出线配置色
                drawBundleBadge(ctx, (gxs + bx2) / 2, (gys + by2) / 2, en.getValue()[0], lit);
            } else {                 // 存储→组（供料）
                boolean fr = sx + bw() - 14 >= gcx;
                float gxi = (float) (panX + (fr ? r[2] : r[0]) * zoom);
                bx2 = sx + bw() - 14; by2 = sy + bh() + 2;
                drawWire(ctx, bx2, by2, 0, 1, gxi, gys, fr ? -1 : 1, 0,
                        lit ? SciSkin.wireIn() : SciSkin.mix(SciSkin.termInk(), SciSkin.wireIn(), 0.30f), 1f); // m198 进线配置色
                drawBundleBadge(ctx, (gxi + bx2) / 2, (gys + by2) / 2, en.getValue()[0], lit);
            }
        }

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(panX, panY, 0);
        m.scale((float) zoom, (float) zoom, 1);

        // 机器↔机器 连线（世界坐标，pxScale=zoom 让线宽在屏幕上恒定不糊不细）
        // m193 归并：端点属组→锚到组框缘，同锚对折并成一条+×N徽章；同组内部线照旧各画各的
        java.util.LinkedHashMap<Long, int[]> mmBundles = new java.util.LinkedHashMap<>(); // 锚对→{数,亮}
        for (int[] c : be.connections()) {
            if (c[0] < nodes.size() && c[1] < nodes.size()) {
                int ga = bundleOn ? nGid[c[0]] : -1, gb = bundleOn ? nGid[c[1]] : -1;
                boolean lit2 = !hovAny || c[0] == hovN || c[1] == hovN; // m164b 悬停聚焦
                if ((ga >= 0 || gb >= 0) && ga != gb) { // 跨组界→归并（组内部线不归并）
                    int se = ga >= 0 ? (GROUP_ENC | ga) : c[0], de = gb >= 0 ? (GROUP_ENC | gb) : c[1];
                    int[] acc = mmBundles.computeIfAbsent(((long) se << 32) | (de & 0xFFFFFFFFL), k -> new int[]{0, 0});
                    acc[0]++;
                    if (lit2) acc[1] = 1;
                    continue;
                }
                int ax0 = wnx(be, nodes, c[0]), ay = wny(be, nodes, c[0]) + NH / 2;
                int bx0 = wnx(be, nodes, c[1]), by = wny(be, nodes, c[1]) + NH / 2;
                boolean fwd = bx0 >= ax0; // m184 下游在右=右缘出左缘进（旧行为）；在左=左缘出右缘进，不再绕背后大圈
                int ax = ax0 + (fwd ? NW : 0), bx = bx0 + (fwd ? 0 : NW), dir = fwd ? 1 : -1;
                drawWire(ctx, ax, ay, dir, 0, bx, by, dir, 0,
                        lit2 ? SciSkin.wireOut() : SciSkin.mix(SciSkin.termInk(), SciSkin.wireOut(), 0.30f), (float) zoom); // m198
            }
        }
        for (var en : mmBundles.entrySet()) { // m193 归并线：组框缘/卡缘 → 组框缘/卡缘，一锚对一条
            long k3 = en.getKey();
            double[] A = mmAnchorGeom(be, nodes, gRect, (int) (k3 >>> 32));
            double[] B = mmAnchorGeom(be, nodes, gRect, (int) k3);
            if (A == null || B == null) continue;
            boolean fwd = B[0] >= A[0];
            float ax = (float) (fwd ? A[3] : A[2]), bx = (float) (fwd ? B[2] : B[3]);
            int dir = fwd ? 1 : -1;
            boolean lit3 = en.getValue()[1] != 0;
            drawWire(ctx, ax, (float) A[1], dir, 0, bx, (float) B[1], dir, 0,
                    lit3 ? SciSkin.wireOut() : SciSkin.mix(SciSkin.termInk(), SciSkin.wireOut(), 0.30f), (float) zoom); // m198
            drawBundleBadge(ctx, (ax + bx) / 2, (float) ((A[1] + B[1]) / 2), en.getValue()[0], lit3);
        }
        if (linking && linkFrom >= 0 && linkFrom < nodes.size()) {
            int nx0 = wnx(be, nodes, linkFrom);
            boolean lr = wmx(mouseX) >= nx0 + NW / 2.0; // m184 预览线同看几何：鼠标在节点左侧就从左缘出
            int ax = nx0 + (lr ? NW : 0), ay = wny(be, nodes, linkFrom) + NH / 2;
            drawWireFree(ctx, ax, ay, lr ? 1 : -1, 0, (float) wmx(mouseX), (float) wmy(mouseY), SciSkin.wireOut(), (float) zoom); // m198 预览随出线色
        }
        for (int i = 0; i < nodes.size(); i++) {
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            drawNode(ctx, be, i, nx, ny, nodes.get(i));
            if (!StructureCoreBlockEntity.isFilter(nodes.get(i)) && !StructureCoreBlockEntity.isSensor(nodes.get(i))
                    && !StructureCoreBlockEntity.isSwitch(nodes.get(i)) && !StructureCoreBlockEntity.isDistributor(nodes.get(i)))
                drawUpgradeSlots(ctx, be, nx, ny, nodes.get(i)); // 逻辑节点无升级格
            drawGear(ctx, nx + NW - 24, ny + 4); // m110b 齿轮=节点设置入口
            if (StructureCoreBlockEntity.nodePaused(nodes.get(i))) { // m110b 暂停视觉：压暗+角标
                ctx.fill(nx, ny, nx + NW, ny + NH, 0x66000000);
                ctx.fill(nx + NW - 43, ny + NH - 15, nx + NW - 3, ny + NH - 3, 0xE0121A28); // m164a 角标垫底，不再与目标行叠字
                ctx.drawText(this.textRenderer, "已暂停", nx + NW - 40, ny + NH - 12, 0xFFFFC84A, false);
            }
        }
        if (groupsOn() && !selected.isEmpty()) { // m192 选中高亮（青描边；服务端删点后失效下标顺手清）
            selected.removeIf(i -> i >= nodes.size());
            for (int i : selected) {
                int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                int selC = SciSkin.termAccent(); // m203 选中描边随主题
                ctx.fill(nx - 3, ny - 3, nx + NW + 3, ny - 2, selC);
                ctx.fill(nx - 3, ny + NH + 2, nx + NW + 3, ny + NH + 3, selC);
                ctx.fill(nx - 3, ny - 3, nx - 2, ny + NH + 3, selC);
                ctx.fill(nx + NW + 2, ny - 3, nx + NW + 3, ny + NH + 3, selC);
            }
        }
        if (boxSelecting) { // m192 框选矩形（淡面+亮边）
            int bx1 = (int) Math.min(boxX0, boxX1), by1 = (int) Math.min(boxY0, boxY1);
            int bx2 = (int) Math.max(boxX0, boxX1), by2 = (int) Math.max(boxY0, boxY1);
            int boxC = SciSkin.termAccent(); // m203 框选随主题
            ctx.fill(bx1, by1, bx2, by2, SciSkin.withAlpha(boxC, 0.10f));
            ctx.fill(bx1, by1, bx2, by1 + 1, boxC);
            ctx.fill(bx1, by2 - 1, bx2, by2, boxC);
            ctx.fill(bx1, by1, bx1 + 1, by2, boxC);
            ctx.fill(bx2 - 1, by1, bx2, by2, boxC);
        }
        m.pop();
        ctx.disableScissor(); // m263 机器层剪刀到此为止（顶缘=带底）

        // ===== 存储总线：顶部横排，屏幕坐标绘制（m91：可收起——收起只留一行库存条，拉线时自动展开）=====
        ctx.enableScissor(0, 24, workRight(), botTop()); // m263 带本体沿用原 24 剪刀（库存条图标顶 2px 照旧裁掉，视觉零漂移）
        {
            int bot = bandBot; // m263 与机器层剪刀同源（收起=只留头部行，内容底缘41，两模式同高）
            SciSkin.termBand(ctx, 8, 24, workRight() - 8, bot); // m203 总线带换浅色主题（旧半透深底字面量退役）
            SciSkin.termBandLine(ctx, 8, workRight() - 8, bot - 2); // m203 底轨随主题（旧轨道色字面量退役）
            ctx.drawText(this.textRenderer, "存储总线（网络库存）", 14, 29, SciSkin.termInk(), false); // m203 浅带写墨字
            // 收起/展开开关（右上角小块）
            int tx = workRight() - 34;
            boolean th = mouseX >= tx && mouseX <= tx + 22 && mouseY >= 26 && mouseY <= 40;
            ctx.fill(tx - 1, 25, tx + 23, 41, th ? SciSkin.termAccentDeep() : SciSkin.termFrame());
            ctx.fill(tx, 26, tx + 22, 40, SciSkin.mix(SciSkin.termBase(), SciSkin.termHi(), 0.35f));
            ctx.drawText(this.textRenderer, busCollapsed ? "▼" : "▲", tx + 7, 29, th ? SciSkin.termAccentDeep() : SciSkin.termInk(), false);
            // m93：总线大小滑块（0.8x~1.25x）
            int trx = busTrackX();
            ctx.drawText(this.textRenderer, "尺寸", trx - 26, 29, SciSkin.termSub(), false);
            ctx.fill(trx, 31, trx + BUS_TRACK_W, 35, SciSkin.termBaseDeep()); // m203 滑轨浅井+紫钮（照设计稿）
            int knx = trx + Math.round((busScale - BUS_MIN) / (BUS_MAX - BUS_MIN) * (BUS_TRACK_W - 6));
            ctx.fill(knx, 27, knx + 6, 39, busScaleDrag ? SciSkin.termAccentDeep() : SciSkin.termAccent());
            if (busVisible() && ends.isEmpty())
                ctx.drawText(this.textRenderer, "端点同步中…（2秒内应出现输出接口）", 14, busCardTop() + 4, SciSkin.termSub(), false);
            // m85：网络库存条（前10物品，服务端聚合同步）——概念图顶栏样式
            int cx = 132;
            java.util.List<String> bi = busIdsOf(be);
            java.util.List<Long> bc = busCountsOf(be);
            for (int k2 = 0; k2 < bi.size(); k2++) {
                ItemStack ist = new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(bi.get(k2))));
                if (ist.isEmpty()) continue;
                String cnt = fmtNum(bc.get(k2));
                int cw = 20 + this.textRenderer.getWidth(cnt) + 10;
                if (cx + cw > workRight() - 186) { ctx.drawText(this.textRenderer, "…", cx, 29, SciSkin.termSub(), false); break; }
                ctx.drawItem(ist, cx, 22);
                ctx.drawText(this.textRenderer, cnt, cx + 18, 29, SciSkin.termInk(), false);
                cx += cw;
            }
        }
        ctx.disableScissor(); // m263 带剪刀到此为止——拖线预览/总线卡/小地图要全屏可见（m159 语义不变）
        // 定向连线本体已前移到节点卡片之前绘制（m136 走下层）；此处只留拖线预览（反馈要压最上层）
        if (linking && linkStor != Long.MIN_VALUE) {
            int j = endpointIndex(ends, linkStor);
            int sx = snx(be, linkStor, Math.max(j, 0)), sy = sny(be, linkStor, Math.max(j, 0));
            drawWireFree(ctx, sx + bw() - 14, sy + bh() + 2, 0, 1, mouseX, mouseY, SciSkin.wireIn(), 1f); // m198 预览随进线色
        }
        { // m265 端点卡三态绘制：放置卡剪到工作视口（不盖上下栏）→停靠卡随总线显隐→拖动中的卡最后无剪刀压顶
            java.util.List<String> dimsE = endDimsOf(be);
            int bandBotE = Math.min(busVisible()
                    ? busCardTop() + Math.max(1, (ends.size() + busCols() - 1) / busCols()) * busRowStep() + 2
                    : 44, botTop()); // m263 同式
            ctx.enableScissor(0, bandBotE, workRight(), botTop());
            for (int j = 0; j < ends.size(); j++) {
                long pl = ends.get(j)[0];
                if (pl != dragEnd && endPlaced(be, pl))
                    drawStorageNode(ctx, be, ends.get(j), j, j < dimsE.size() ? dimsE.get(j) : "");
            }
            ctx.disableScissor();
            if (busVisible()) for (int j = 0; j < ends.size(); j++) {
                long pl = ends.get(j)[0];
                if (pl != dragEnd && !endPlaced(be, pl))
                    drawStorageNode(ctx, be, ends.get(j), j, j < dimsE.size() ? dimsE.get(j) : "");
            }
            if (dragEnd != Long.MIN_VALUE) for (int j = 0; j < ends.size(); j++)
                if (ends.get(j)[0] == dragEnd)
                    drawStorageNode(ctx, be, ends.get(j), j, j < dimsE.size() ? dimsE.get(j) : "");
        }
        if (mapOpen) renderMinimap(ctx); // m110a
    }

    // ================= m110a 小地图 =================
    private int mapX() { return workRight() - MAP_W - 8; }
    private int mapY() { return botTop() - 6 - MAP_H; }
    private boolean inMap(double mx, double my) {
        return mapOpen && mx >= mapX() && mx <= mapX() + MAP_W && my >= mapY() && my <= mapY() + MAP_H;
    }

    /** 几何：{世界minX, 世界minY, 缩放}。范围 = 全部机器节点 ∪ 当前视口（视口白框永不丢出图外）。 */
    private double[] mapGeom(StructureCoreBlockEntity be) {
        List<ItemStack> nodes = be.nodes();
        double minX = (0 - panX) / zoom, minY = (34 - panY) / zoom;
        double maxX = (workRight() - panX) / zoom, maxY = (botTop() - panY) / zoom;
        for (int i = 0; i < nodes.size(); i++) {
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            minX = Math.min(minX, nx); minY = Math.min(minY, ny);
            maxX = Math.max(maxX, nx + NW); maxY = Math.max(maxY, ny + NH + 28);
        }
        double sc = Math.min((MAP_W - 10) / Math.max(1, maxX - minX), (MAP_H - 10) / Math.max(1, maxY - minY));
        return new double[]{minX, minY, sc};
    }

    /** 概览：节点按分类配色画小矩形 + 当前视口白框；点击/拖拽跳转（见 mapJump）。 */
    private void renderMinimap(DrawContext ctx) {
        StructureCoreBlockEntity be = be();
        if (be == null) return;
        int mx = mapX(), my = mapY();
        ctx.fill(mx + 2, my + 3, mx + MAP_W + 3, my + MAP_H + 3, 0x59000000); // m120 投影
        ctx.fill(mx - 1, my - 1, mx + MAP_W + 1, my + MAP_H + 1, NODEFRM);
        ctx.fill(mx, my, mx + MAP_W, my + MAP_H, 0xE0101820);
        ctx.fill(mx, my, mx + MAP_W, my + 2, CYAN);
        List<ItemStack> nodes = be.nodes();
        if (nodes.isEmpty()) {
            ctx.drawText(this.textRenderer, "画布为空", mx + (MAP_W - this.textRenderer.getWidth("画布为空")) / 2,
                    my + MAP_H / 2 - 4, SUB, false);
            return;
        }
        double[] g = mapGeom(be);
        for (int i = 0; i < nodes.size(); i++) {
            int x1 = mx + 5 + (int) ((wnx(be, nodes, i) - g[0]) * g[2]);
            int y1 = my + 5 + (int) ((wny(be, nodes, i) - g[1]) * g[2]);
            int w = Math.max(3, (int) (NW * g[2])), h = Math.max(3, (int) (NH * g[2]));
            ctx.fill(x1, y1, x1 + w, y1 + h, nodeAccent(nodes.get(i)));
        }
        int vx1 = mx + 5 + (int) (((0 - panX) / zoom - g[0]) * g[2]);
        int vy1 = my + 5 + (int) (((34 - panY) / zoom - g[1]) * g[2]);
        int vx2 = mx + 5 + (int) (((workRight() - panX) / zoom - g[0]) * g[2]);
        int vy2 = my + 5 + (int) (((botTop() - panY) / zoom - g[1]) * g[2]);
        vx1 = Math.max(mx + 1, vx1); vy1 = Math.max(my + 1, vy1);
        vx2 = Math.min(mx + MAP_W - 1, vx2); vy2 = Math.min(my + MAP_H - 1, vy2);
        int vc = 0xCCFFFFFF;
        ctx.fill(vx1, vy1, vx2, vy1 + 1, vc); ctx.fill(vx1, vy2 - 1, vx2, vy2, vc);
        ctx.fill(vx1, vy1, vx1 + 1, vy2, vc); ctx.fill(vx2 - 1, vy1, vx2, vy2, vc);
    }

    /** 点中的世界点移到工作区中心；拖拽期间用快照几何。 */
    private void mapJump(double mouseX, double mouseY) {
        if (mapGeomDrag == null) return;
        zoomAnim = false; zoomTarget = zoom; // m186 手动跳转终止缩放动效防隔帧抢写
        double wx = mapGeomDrag[0] + (mouseX - mapX() - 5) / mapGeomDrag[2];
        double wy = mapGeomDrag[1] + (mouseY - mapY() - 5) / mapGeomDrag[2];
        panX = workRight() / 2.0 - wx * zoom;
        panY = (34 + botTop()) / 2.0 - wy * zoom;
    }

    private static int endpointIndex(List<long[]> ends, long pl) {
        for (int j = 0; j < ends.size(); j++) if (ends.get(j)[0] == pl) return j;
        return -1;
    }

    /** 存储/终端接口节点：连了几个显示几个。 */
    private void drawStorageNode(DrawContext ctx, StructureCoreBlockEntity be, long[] ep, int j, String dim) {
        long pl = ep[0];
        int kind = (int) ep[1];
        int x = snx(be, pl, j), y = sny(be, pl, j);
        boolean iface = kind == 6;
        int frm = iface ? CYAN : kind == 5 ? TERMFRM : kind == 4 ? OFFFRM : STORFRM;
        // m150b 重画：类型色不再糊满边框（截图里荧光绿/紫大框太吵）——边框压到与机器卡同族的
        // 暗色（类型色只掺三成），类型身份交给 3px 顶带 + 右上类型药丸；图标垫暗托盘；
        // 端口从实心色块改成 暗座+亮芯 的接线柱。端口矩形几何原位不动（连线命中区）。
        int mframe = SciSkin.mix(SciSkin.FRAME, frm, 0.3f);
        SciSkin.drawCard(ctx, x, y, bw(), bh(), mframe);
        ctx.fill(x, y, x + bw(), y + 3, frm);                                   // 顶带=类型signature
        ctx.fill(x, y + 3, x + bw(), y + 15, SciSkin.withAlpha(SciSkin.BACKDROP, 0.25f)); // 标题底带（与机器卡同；m207 字面量退役随族）
        ctx.fill(x, y + bh() - 1, x + bw(), y + bh(), SciSkin.withAlpha(frm, 0.35f)); // 底部发丝线
        ctx.fill(x + 10, y + bh() - 2, x + 18, y + bh() + 4, SciSkin.mix(SciSkin.wireOut(), SciSkin.BACKDROP, 0.55f)); // 收料口·暗座（m207 端口跟出线色，m198留痕销账）
        ctx.fill(x + 11, y + bh() - 1, x + 17, y + bh() + 3, SciSkin.wireOut());                     // 收料口·亮芯（m207 跟出线色）
        if (!iface) {
            ctx.fill(x + bw() - 18, y + bh() - 2, x + bw() - 10, y + bh() + 4, SciSkin.mix(SciSkin.wireIn(), SciSkin.BACKDROP, 0.55f)); // 供料口·暗座（m207 跟进线色）
            ctx.fill(x + bw() - 17, y + bh() - 1, x + bw() - 11, y + bh() + 3, SciSkin.wireIn());                    // 供料口·亮芯（m207 跟进线色）
        }
        ItemStack icon = new ItemStack(iface ? com.sdzjz.registry.ModBlocks.SATELLITE_NODE.asItem()
                : kind == 5 ? com.sdzjz.registry.ModBlocks.DATA_PANEL.asItem()
                : com.sdzjz.registry.ModBlocks.STORAGE_CORE.asItem());
        float isc = 1.5f * busScale; // m122 图标 1.5× 且随尺寸滑块缩放（用户点名"看不清"）
        int ipx = Math.round(16 * isc);
        ctx.fill(x + 3, y + (bh() - ipx) / 2 - 1, x + 5 + ipx + 1, y + (bh() + ipx) / 2 + 1,
                SciSkin.withAlpha(SciSkin.BACKDROP, 0.75f)); // m150b 图标暗托盘（图标不再悬空；m207 字面量退役随族）
        ctx.fill(x + 3, y + (bh() - ipx) / 2 - 1, x + 5 + ipx + 1, y + (bh() - ipx) / 2,
                SciSkin.withAlpha(frm, 0.4f));
        var msI = ctx.getMatrices(); msI.push();
        msI.translate(x + 4, y + (bh() - 16 * isc) / 2f, 0);
        msI.scale(isc, isc, 1f);
        ctx.drawItem(icon, 0, 0);
        msI.pop();
        int txI = x + 8 + Math.round(16 * isc); // 文字随图标宽度让位
        String title;
        if (iface) title = "输出接口";
        else { // 分组编号：存储1/2…、数据面板1/2…（服务端已按 接口→存储→面板 排序）
            int no = 0;
            java.util.List<long[]> allEp = endsOf(be);
            for (int k = 0; k <= j && k < allEp.size(); k++)
                if (allEp.get(k)[1] != 6 && (allEp.get(k)[1] == 5) == (kind == 5)) no++;
            title = (kind == 5 ? "数据面板" : "存储") + no;
        }
        String tag = KIND[Math.min(kind, 6)];
        int tagC = iface ? CYAN : kind == 4 ? SUB : kind == 5 ? 0xFFB9A0F0 : ON;
        int tw = this.textRenderer.getWidth(tag);
        // m189 标题行自动适配（用户截图：药丸/长标题出框）：药丸右贴卡缘位置恒定，
        // 标题按剩余宽截断加省略号——副行 m164a 已治过，这行是漏网的（tgx 原先跟着标题漂无钳位）。
        int tgx = Math.max(txI + 12, x + bw() - tw - 7);
        ctx.drawText(this.textRenderer, fitText(title, tgx - 6 - txI), txI, y + 5, SciSkin.TXT_MAX, false); // m150b 标题提亮
        ctx.fill(tgx - 3, y + 4, tgx + tw + 3, y + 14, SciSkin.mix(tagC, SciSkin.CELL, 0.8f)); // m150b 类型药丸（m207 归队）
        ctx.fill(tgx - 3, y + 4, tgx + tw + 3, y + 5, SciSkin.withAlpha(tagC, 0.55f));
        ctx.fill(tgx - 3, y + 13, tgx + tw + 3, y + 14, SciSkin.withAlpha(tagC, 0.55f));
        ctx.drawText(this.textRenderer, tag, tgx, y + 5, tagC, false);
        String sub;
        if (iface) {
            sub = "自动寻路: 绑定>有线>无线>卫星";
        } else {
            BlockPos bp = BlockPos.fromLong(pl);
            sub = bp.getX() + "," + bp.getY() + "," + bp.getZ();
            boolean sameDim = this.client != null && this.client.world != null
                    && (dim == null || dim.isEmpty()
                        || dim.equals(this.client.world.getRegistryKey().getValue().toString()));
            if (sameDim && this.client.world.getBlockEntity(bp) instanceof StorageCoreBlockEntity sc) {
                sub += sc.maxTypes() == Integer.MAX_VALUE ? ("  类型 " + sc.usedTypes()) : ("  类型 " + sc.usedTypes() + "/" + sc.maxTypes()); // 仅同维度读数; m98 无限不显上限
            }
        }
        // m164a 文字自适应（用户点名"上面的文字都出框了"）：副行按卡片剩余宽度截断加省略号
        ctx.drawText(this.textRenderer, fitText(sub, x + bw() - txI - 4), txI, y + 17, SUB, false);
    }

    private void drawNode(DrawContext ctx, StructureCoreBlockEntity be, int i, int x, int y, ItemStack st) {
        SciSkin.drawCard(ctx, x, y, NW, NH, NODEFRM); // m120 投影+渐变面+角刻
        ctx.fill(x, y, x + NW, y + 3, nodeAccent(st)); // m86 分类配色
        ctx.fill(x, y + 3, x + NW, y + 15, SciSkin.withAlpha(SciSkin.BACKDROP, 0.25f)); // m120 标题读数底带（m207 字面量退役随族）
        ctx.fill(x - 4, y + NH / 2 - 3, x + 2, y + NH / 2 + 3, CYAN);
        ctx.fill(x + NW - 2, y + NH / 2 - 3, x + NW + 4, y + NH / 2 + 3, ON);
        int mt = StructureCoreBlockEntity.machineTier(st); // m123 阶位视觉：图标放大+前缀变色
        float isc = 2f + 0.45f * mt;
        var msi = ctx.getMatrices();
        msi.push();
        msi.translate(x + 6, y + 16 - (isc - 2f) * 10, 0);
        msi.scale(isc, isc, 1f);
        ctx.drawItem(st, 0, 0);
        msi.pop();
        String pre = mt <= 0 ? "" : mt == 1 ? "超级·" : mt == 2 ? "神级·" : "GM·";
        int preW = pre.isEmpty() ? 0 : this.textRenderer.getWidth(pre);
        if (preW > 0) ctx.drawText(this.textRenderer, pre, x + 6, y + 6,
                mt == 1 ? SciSkin.GOLD : mt == 2 ? 0xFFB06AE8 : SciSkin.RED, false);
        String name = st.getName().getString();
        if (this.textRenderer.getWidth(name) > NW - 22 - preW) {
            while (name.length() > 1 && this.textRenderer.getWidth(name + "…") > NW - 22 - preW) name = name.substring(0, name.length() - 1);
            name = name + "…";
        }
        ctx.drawText(this.textRenderer, name, x + 6 + preW, y + 6, TXT, false);
        drawStatusDot(ctx, x + NW - 11, y + 5, be.nodeStatus(i)); // 状态灯：绿=运行 黄=阻塞/关闸 红=缺料
        if (StructureCoreBlockEntity.isDistributor(st)) {
            int outs = 0;
            for (int[] c : be.connections()) if (c[0] == i) outs++;
            ctx.drawText(this.textRenderer, "均分 → " + outs + " 路", x + 44, y + 26, CYAN, false);
            ctx.drawText(this.textRenderer, outs == 0 ? "拉出线到下游" : "余数轮转", x + 44, y + 38, SUB, false);
            return;
        }
        if (StructureCoreBlockEntity.isExtractor(st)) { // m154 启停显示 + m159 速率与已抽读数
            boolean onX = StructureCoreBlockEntity.extractorOn(st);
            int bfx = onX ? ON : SciSkin.OFF_GRAY;
            ctx.fill(x + 43, y + 23, x + 91, y + 45, bfx);
            ctx.fill(x + 44, y + 24, x + 90, y + 44, onX ? SciSkin.ON_DARK : 0xFF141A24);
            ctx.drawText(this.textRenderer, onX ? "● 抽取中" : "○ 待命", x + 48, y + 30, onX ? ON : SUB, false);
            String siX = StructureCoreBlockEntity.sensorItem(st);
            if (!siX.isEmpty()) { // m160 自动启停行：图标 <阈值 [−][+]
                try { ctx.drawItem(new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(siX))), x + 4, y + 44); } catch (Exception ignored) {}
                long thX = StructureCoreBlockEntity.sensorThreshold(st);
                ctx.drawText(this.textRenderer, (StructureCoreBlockEntity.sensorLess(st) ? "<" : ">") + fmtNum(thX),
                        x + 22, y + 48, CYAN, false);
                ctx.fill(x + 62, y + 46, x + 76, y + 59, SciSkin.BTN_FACE);
                ctx.fill(x + 79, y + 46, x + 93, y + 59, SciSkin.BTN_FACE);
                ctx.drawText(this.textRenderer, "-", x + 67, y + 49, TXT, false);
                ctx.drawText(this.textRenderer, "+", x + 84, y + 49, TXT, false);
            } else {
                long xr = StructureCoreBlockEntity.extractorRate(st);
                long xc = StructureCoreBlockEntity.extractorCount(st);
                // m164a：262144 挡原样拼"262144/轮×升级"必出框——速率走 fmtNum(262.1K)，整行 fitText 兜底
                ctx.drawText(this.textRenderer, fitText(fmtNum(xr) + "/轮" + (xc > 0 ? " 抽" + fmtNum(xc) : "×升级"), NW - 48),
                        x + 44, y + 48, SUB, false);
            }
            return;
        }
        if (StructureCoreBlockEntity.isSwitch(st)) {
            boolean on = StructureCoreBlockEntity.switchOn(st);
            int bfr = on ? ON : SciSkin.OFF_GRAY;
            ctx.fill(x + 43, y + 23, x + 91, y + 45, bfr);
            ctx.fill(x + 44, y + 24, x + 90, y + 44, on ? SciSkin.ON_DARK : 0xFF141A24);
            ctx.drawText(this.textRenderer, on ? "● 开" : "○ 关", x + 55, y + 30, on ? ON : SUB, false);
            return;
        }
        if (StructureCoreBlockEntity.isTrash(st)) { // m150 卡面 / 菜单入口见 openNodeMenu
            long ate = StructureCoreBlockEntity.trashCount(st);
            int tfN = StructureCoreBlockEntity.filterList(st).size();
            ctx.drawText(this.textRenderer, tfN > 0 ? "[白名单·" + tfN + "]" : "[虚空]", x + 44, y + 26, SciSkin.RED_SOFT, false);
            ctx.drawText(this.textRenderer, ate > 0 ? "已吞 " + fmtNum(ate) : tfN > 0 ? "只吞名单内" : "连啥吞啥", x + 44, y + 38, SUB, false);
            return;
        }
        if (StructureCoreBlockEntity.isFilter(st)) {
            boolean black = StructureCoreBlockEntity.filterBlacklist(st);
            ctx.drawText(this.textRenderer, black ? "[黑名单]" : "[白名单]", x + 44, y + 26, black ? SciSkin.GOLD : ON, false);
            List<String> fl = StructureCoreBlockEntity.filterList(st);
            if (fl.isEmpty()) {
                ctx.drawText(this.textRenderer, "右键配置", x + 44, y + 38, SUB, false);
            } else {
                int shown = Math.min(3, fl.size());
                for (int k = 0; k < shown; k++) {
                    try { ctx.drawItem(new ItemStack(Registries.ITEM.get(Identifier.of(fl.get(k)))), x + 42 + k * 18, y + 34); } catch (Exception ignored) {}
                }
                if (fl.size() > 3) ctx.drawText(this.textRenderer, "+" + (fl.size() - 3), x + 42 + 54, y + 38, SUB, false);
            }
            return;
        }
        if (StructureCoreBlockEntity.isSensor(st)) {
            String si = StructureCoreBlockEntity.sensorItem(st);
            if (si.isEmpty()) {
                ctx.drawText(this.textRenderer, "直通(未配置)", x + 44, y + 26, SUB, false);
                ctx.drawText(this.textRenderer, "右键配置", x + 44, y + 38, SUB, false);
            } else {
                try { ctx.drawItem(new ItemStack(Registries.ITEM.get(Identifier.of(si))), x + 40, y + 20); } catch (Exception ignored) {}
                long th = StructureCoreBlockEntity.sensorThreshold(st);
                boolean less = StructureCoreBlockEntity.sensorLess(st);
                ctx.drawText(this.textRenderer, (less ? "<" : ">") + fmtNum(th) + " 放行", x + 58, y + 24, CYAN, false);
                ctx.fill(x + 57, y + 36, x + 71, y + 49, SciSkin.BTN_FACE); // [−]
                ctx.fill(x + 74, y + 36, x + 88, y + 49, SciSkin.BTN_FACE); // [+]
                ctx.drawText(this.textRenderer, "-", x + 62, y + 39, TXT, false);
                ctx.drawText(this.textRenderer, "+", x + 79, y + 39, TXT, false);
            }
            return;
        }
        ctx.drawText(this.textRenderer, "×" + st.getCount(), x + Math.max(44, 10 + Math.round(16 * isc)), y + 26, CYAN, false); // m123 让位大图标
        String why178 = be.nodeReason(i); // m178 阻塞原因：黄/红灯时常显在 y+38 行（徽章副行让位）
        int stv178 = be.nodeStatus(i);
        boolean showWhy = (stv178 == 2 || stv178 == 3) && !why178.isEmpty();
        boolean isCrop = st.getItem() instanceof com.sdzjz.item.CropFarmItem;
        boolean isBrew = st.getItem() instanceof com.sdzjz.item.BrewingTowerItem; // m131b
        boolean isEnch = st.getItem() instanceof com.sdzjz.item.EnchantFactoryItem; // m132
        boolean isTrade = st.getItem() instanceof com.sdzjz.item.VillagerTraderItem; // m146
        if (st.getItem() instanceof AutoCrafterItem || isCrop || isBrew || isEnch || isTrade) {
            int bx = x + NW - 30, by = y + 14;
            ctx.fill(bx - 1, by - 1, bx + 21, by + 21, NODEFRM);
            ctx.fill(bx, by, bx + 20, by + 20, SciSkin.BTN_FACE);
            java.util.List<String> cropsSel = isCrop ? StructureCoreBlockEntity.cropList(st) : java.util.List.of();
            String t = StructureCoreBlockEntity.craftTarget(st);
            if (isCrop && !cropsSel.isEmpty()) { // m93 多选作物：徽章=第一种，下行前3种mini图标+计数
                ctx.drawItem(new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(cropsSel.get(0)))), bx + 2, by + 2);
                if (!showWhy) { // m178 阻塞时让位原因行
                    int nm = Math.min(3, cropsSel.size());
                    for (int k = 0; k < nm; k++)
                        ctx.drawItem(new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(cropsSel.get(k)))), x + 42 + k * 13, y + 34);
                    ctx.drawText(this.textRenderer, "×" + cropsSel.size() + "种", x + 42 + nm * 13 + 4, y + 38, ON, false);
                }
            } else if (!isCrop && !t.isEmpty()) {
                ItemStack ts = isBrew ? com.sdzjz.machine.BrewPlanner.targetStack(t)
                        : isEnch ? com.sdzjz.machine.EnchantPlanner.targetStack(MinecraftClient.getInstance().world, t)
                        : isTrade ? com.sdzjz.machine.TradePlanner.iconStack(t)
                        : new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(t)));
                if (ts == null) ts = new ItemStack(isEnch ? net.minecraft.item.Items.ENCHANTED_BOOK
                        : isTrade ? net.minecraft.item.Items.EMERALD
                        : net.minecraft.item.Items.BREWING_STAND); // 目标串解析失败兜底（模组卸载/数据包变更等）
                ctx.drawItem(ts, bx + 2, by + 2);
                String tn = ts.getName().getString();
                if (isEnch) { // m132 附魔书名恒为"附魔书"——徽章文字用附魔名（罗马数字自带）
                    var en = com.sdzjz.machine.EnchantPlanner.targetName(MinecraftClient.getInstance().world, t);
                    if (en != null) tn = en.getString();
                }
                if (isTrade) tn = com.sdzjz.machine.TradePlanner.displayName(t).getString(); // m146 徽章=整条交易
                tn = fitText(tn, NW - 50 - this.textRenderer.getWidth("→")); // m164a：硬切改省略号截断
                if (!showWhy) ctx.drawText(this.textRenderer, "→" + tn, x + 44, y + 38, ON, false); // 放大图标后挪右，避免压字;m178 阻塞时让位
            } else {
                ctx.drawText(this.textRenderer, "?", bx + 7, by + 6, SUB, false);
                if (!showWhy) ctx.drawText(this.textRenderer, isCrop ? "选作物" : isEnch ? "选附魔" : isTrade ? "选交易" : "设目标", x + 44, y + 38, SUB, false);
            }
        }
        if (showWhy) // m178 错误解释：从"猜"到"可诊断"——缺料红字/阻塞金字，转绿自动消失
            ctx.drawText(this.textRenderer, fitText(why178, NW - 50), x + 44, y + 38, stv178 == 3 ? SciSkin.RED_SOFT : SciSkin.GOLD, false);
    }

    /** 状态灯：核心停机=灰；1 绿呼吸=运行 2 黄=阻塞/关闸 3 红=缺料 其余=待机灰。 */
    private void drawStatusDot(DrawContext ctx, int x, int y, int stat) {
        int c;
        if (!this.handler.isRunning()) c = 0xFF3A424E;
        else c = switch (stat) {
            case 1 -> ((165 + (int) (88 * Math.sin(System.currentTimeMillis() / 300.0))) << 24) | 0x33D07A;
            case 2 -> SciSkin.GOLD;
            case 3 -> SciSkin.RED;
            default -> 0xFF3A424E;
        };
        ctx.fill(x - 1, y - 1, x + 7, y + 7, 0xFF06101C);
        ctx.fill(x, y, x + 6, y + 6, c);
    }

    private static String fmtNum(long n) {
        if (n < 10000L) return String.valueOf(n);
        if (n < 1_000_000L) return String.format("%.1fK", n / 1000.0);
        if (n < 1_000_000_000L) return String.format("%.1fM", n / 1_000_000.0);
        return String.format("%.1fB", n / 1_000_000_000.0);
    }

    private void drawUpgradeSlots(DrawContext ctx, StructureCoreBlockEntity be, int x, int y, ItemStack node) {
        int[] lv = { be.nodeSpeed(node), be.nodeCount(node), be.nodePar(node) };
        for (int k = 0; k < 3; k++) {
            int sx = x + 4 + k * 32, sy = y + NH + 4;
            ctx.fill(sx - 1, sy - 1, sx + 25, sy + 19, NODEFRM);
            ctx.fill(sx, sy, sx + 24, sy + 18, SciSkin.BTN_FACE);
            SciSkin.drawSlot(ctx, sx + 1, sy + 1); // m120 物品坐进角括号插槽，与终端同语言
            ctx.drawItem(new ItemStack(UPG[k]), sx + 1, sy + 1);
            ctx.drawText(this.textRenderer, "" + lv[k], sx + 18, sy + 6, lv[k] > 0 ? ON : SUB, false);
        }
    }

    // ===== m136 连线精修（用户点名"不够好看不够精美"）：方块盖章 → 逐顶点着色缎带 =====
    // 旧法（m122）：贝塞尔采样后逐点 fill 2×2/4×4 方块——长线颗粒感、无抗锯齿、光晕是方块套方块。
    // 新法：沿曲线发四边形条带（与 fill 同 RenderLayer.getGui() 同批次），顶点色向两缘渐隐=免费羽化抗锯齿；
    // 三层：投影(浮出网格) + 软光晕 + 亮核；亮度沿线 82%→105% 坡升暗示方向；彗星脉冲顺流而行
    // （替代旧方块能量段，头亮尾散、过处线身微胀），相位按端点座标播种——并排线不同步呼吸；
    // 两端点各一枚羽化发光端口圆点，线不再"凭空消失在卡片边上"；线宽除以 pxScale——世界坐标层
    // 缩放 0.4~2.5 下屏幕线宽恒定，缩小不细成发丝、放大不糊成粗杠。

    /** 标准连线：两端切线已知（水平出入卡缘 / 垂直出入总线端口）。pxScale=当前矩阵缩放（屏幕坐标传 1）。 */
    private void drawWire(DrawContext ctx, float x1, float y1, float tx1, float ty1,
                          float x2, float y2, float tx2, float ty2, int color, float pxScale) {
        wirePath(ctx, x1, y1, tx1, ty1, x2, y2, tx2, ty2, color, pxScale);
    }

    /** 拖线预览：终点跟随鼠标，末端切线取行进方向，曲线自然收尾。 */
    private void drawWireFree(DrawContext ctx, float x1, float y1, float tx1, float ty1,
                              float x2, float y2, int color, float pxScale) {
        float dx = x2 - x1, dy = y2 - y1;
        float l = (float) Math.hypot(dx, dy);
        if (l < 1f) { dx = 1; dy = 0; l = 1; }
        wirePath(ctx, x1, y1, tx1, ty1, x2, y2, dx / l, dy / l, color, pxScale);
    }

    private void wirePath(DrawContext ctx, float x1, float y1, float tx1, float ty1,
                          float x2, float y2, float tx2, float ty2, int color, float pxScale) {
        float dist = (float) Math.hypot(x2 - x1, y2 - y1);
        if (dist < 2f) return;
        // m197 有效除数：屏幕线宽=基准×min(pxScale,封顶)。开关关=旧行为(除pxScale=屏幕恒宽)；
        // 屏幕坐标层调用传 pxScale=1 且封顶≥1 时 pd=1 完全不受影响。
        com.sdzjz.config.SdzjzConfig wcfg = com.sdzjz.config.SdzjzConfig.get();
        float wcap = (float) Math.max(0.2, wcfg.canvasWireMaxScale);
        float pd = wcfg.canvasWireScaleWithZoom ? pxScale / Math.min(pxScale, wcap) : pxScale;
        float d = Math.max(36f, Math.min(150f, dist * 0.42f)); // 控制柄长度：近不打结、远不拉直
        float c1x = x1 + tx1 * d, c1y = y1 + ty1 * d;
        float c2x = x2 - tx2 * d, c2y = y2 - ty2 * d;
        int n = Math.max(20, Math.min(56, (int) (dist / 8f))) + 1; // 采样数按线长自适应
        float[] px = new float[n], py = new float[n], nx = new float[n], ny = new float[n], cum = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1), u = 1 - t;
            float a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, e = t * t * t;
            px[i] = a * x1 + b * c1x + c * c2x + e * x2;
            py[i] = a * y1 + b * c1y + c * c2y + e * y2;
        }
        for (int i = 0; i < n; i++) { // 单位法线（中央差分）+ 弧长累计（脉冲定位用）
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            float dx = px[i1] - px[i0], dy = py[i1] - py[i0];
            float l = (float) Math.hypot(dx, dy); if (l < 1e-4f) l = 1;
            nx[i] = -dy / l; ny[i] = dx / l;
            if (i > 0) cum[i] = cum[i - 1] + (float) Math.hypot(px[i] - px[i - 1], py[i] - py[i - 1]);
        }
        net.minecraft.client.render.VertexConsumer vc =
                ctx.getVertexConsumers().getBuffer(net.minecraft.client.render.RenderLayer.getGui());
        org.joml.Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();

        int rgb = color & 0xFFFFFF;
        float time = (System.currentTimeMillis() % 600000L) / 1000f;
        float seed = ((x1 * 13f + y1 * 7f + x2 * 3f + y2) % 88f + 88f) % 88f; // 并排线错相
        float[] w = new float[n];
        int[] col = new int[n];

        // 第一层：投影（右下偏移的暗缎带，把线从网格上抬起来）
        float shOff = 1.4f / pd;
        java.util.Arrays.fill(w, 1.2f / pd);
        java.util.Arrays.fill(col, 0x3A000000);
        ribbon(vc, mat, px, py, nx, ny, cum, w, 1.8f / pd, col, shOff, shOff * 1.2f);

        // 第二层：软光晕（宽羽化低透明，脉冲过处微微透亮）
        for (int i = 0; i < n; i++) {
            float p = pulseAt(cum[i], time, seed);
            w[i] = 2.1f / pd;
            col[i] = ((int) (0x24 + 0x2C * p) << 24) | rgb;
        }
        ribbon(vc, mat, px, py, nx, ny, cum, w, 3.4f / pd, col, 0, 0);

        // 第三层：亮核（沿线亮度坡升暗示方向；脉冲提亮向白并微胀线身）
        for (int i = 0; i < n; i++) {
            float t = cum[i] / cum[n - 1];
            float p = pulseAt(cum[i], time, seed);
            int cc = towardWhite(mulRgb(rgb, 0.82f + 0.23f * t), p * 0.75f);
            col[i] = (Math.min(0xFF, 0xD8 + (int) (0x27 * p)) << 24) | cc;
            w[i] = (0.9f + 0.5f * p) / pd;
        }
        ribbon(vc, mat, px, py, nx, ny, cum, w, 1.0f / pd, col, 0, 0);

        // 端口圆点：线的起讫落在发光接点上，不再凭空断在卡片边缘
        portDot(vc, mat, x1, y1, rgb, pd);
        portDot(vc, mat, x2, y2, rgb, pd);
    }

    /** 彗星脉冲强度 0..1：等距脉冲沿弧长顺流（速度110px/s 间距88px），头缘陡尾缘缓。 */
    private static float pulseAt(float s, float time, float seed) {
        float m = (s - time * 110f - seed) % 88f;
        if (m < 0) m += 88f;
        float head = m < 10f ? 1f - m / 10f : 0f;          // 头前 10px 陡降
        float tail = m > 88f - 22f ? (m - (88f - 22f)) / 22f : 0f; // 尾后 22px 缓升到头
        float v = Math.max(head, tail * tail * 0.85f);
        return v * v * (3 - 2 * v); // smoothstep 圆润
    }

    /** 单条缎带：中心两侧 halfW 实体 + feather 羽化到全透明（顶点插色=抗锯齿）。绕线方向发四边形，与 fill 同绕序。 */
    private void ribbon(net.minecraft.client.render.VertexConsumer vc, org.joml.Matrix4f mat,
                        float[] px, float[] py, float[] nx, float[] ny, float[] cum,
                        float[] halfW, float feather, int[] col, float ox, float oy) {
        int n = px.length;
        for (int i = 0; i + 1 < n; i++) {
            int c0 = col[i], c1 = col[i + 1];
            int f0 = c0 & 0x00FFFFFF, f1 = c1 & 0x00FFFFFF;
            float ax = px[i] + ox, ay = py[i] + oy, bx = px[i + 1] + ox, by = py[i + 1] + oy;
            float anx = nx[i], any = ny[i], bnx = nx[i + 1], bny = ny[i + 1];
            float ah = halfW[i], bh = halfW[i + 1], af = ah + feather, bf = bh + feather;
            quad(vc, mat, ax - anx * af, ay - any * af, f0, ax - anx * ah, ay - any * ah, c0,
                          bx - bnx * bh, by - bny * bh, c1, bx - bnx * bf, by - bny * bf, f1); // 左羽化
            quad(vc, mat, ax - anx * ah, ay - any * ah, c0, ax + anx * ah, ay + any * ah, c0,
                          bx + bnx * bh, by + bny * bh, c1, bx - bnx * bh, by - bny * bh, c1); // 核心
            quad(vc, mat, ax + anx * ah, ay + any * ah, c0, ax + anx * af, ay + any * af, f0,
                          bx + bnx * bf, by + bny * bf, f1, bx + bnx * bh, by + bny * bh, c1); // 右羽化
        }
    }

    /** 端口接点：羽化发光圆点（12 段三角扇 + 渐隐外环），中心提白、边缘线色、外环透明。 */
    private void portDot(net.minecraft.client.render.VertexConsumer vc, org.joml.Matrix4f mat,
                         float cx, float cy, int rgb, float pxScale) {
        float r = 2.5f / pxScale, fe = 1.9f / pxScale;
        int cCenter = 0xFF000000 | towardWhite(rgb, 0.55f);
        int cEdge   = 0xE6000000 | rgb;
        int cOut    = rgb; // alpha 0
        int seg = 12;
        for (int k = 0; k < seg; k++) {
            double a0 = -2 * Math.PI * k / seg, a1 = -2 * Math.PI * (k + 1) / seg; // 负向=与 fill 同绕序
            float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r;
            float x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
            float X0 = cx + (float) Math.cos(a0) * (r + fe), Y0 = cy + (float) Math.sin(a0) * (r + fe);
            float X1 = cx + (float) Math.cos(a1) * (r + fe), Y1 = cy + (float) Math.sin(a1) * (r + fe);
            quad(vc, mat, cx, cy, cCenter, x0, y0, cEdge, x1, y1, cEdge, cx, cy, cCenter); // 内盘扇片
            quad(vc, mat, x0, y0, cEdge, X0, Y0, cOut, X1, Y1, cOut, x1, y1, cEdge);       // 外环羽化
        }
    }

    private void quad(net.minecraft.client.render.VertexConsumer vc, org.joml.Matrix4f mat,
                      float x1, float y1, int c1, float x2, float y2, int c2,
                      float x3, float y3, int c3, float x4, float y4, int c4) {
        vc.vertex(mat, x1, y1, 0).color(c1);
        vc.vertex(mat, x2, y2, 0).color(c2);
        vc.vertex(mat, x3, y3, 0).color(c3);
        vc.vertex(mat, x4, y4, 0).color(c4);
    }

    private static int mulRgb(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static int towardWhite(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += (int) ((255 - r) * f); g += (int) ((255 - g) * f); b += (int) ((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // m85：HandledScreen 会把前景层平移 (x,y)——之前标题/状态因此漂到屏幕中间。translate 回去，用真屏幕坐标。
        ctx.getMatrices().push();
        ctx.getMatrices().translate(-this.x, -this.y, 0);
        // m83：状态栏下沉到底部（用户点名，参考 ME 终端把信息压在操作区）——顶部只留窄标题条，给存储总线腾地方
        // m210：此处原有 0..19 重复浅带已撤——顶条带(0..22)在背景层画过，前景层再铺一条不透明浅带
        // 正好把 机器库/地图/设置/−/＋/适应视图 六钮糊死（浅带时代的画序坑，深色半透时代只是压暗看不出）。
        String tierName = this.handler.tier() >= 2 ? "超大工作台 · 画布" : "结构核心 · 画布";
        ctx.drawText(this.textRenderer, tierName, 10, 6, SciSkin.termInk(), false); // m203 浅带写墨字
        String zp = Math.round(zoom * 100) + "%"; // m86 顶条缩放读数（−/＋按钮之间）
        ctx.drawText(this.textRenderer, zp, workRight() - 128 - this.textRenderer.getWidth(zp) / 2, 6, SciSkin.termInk(), false);

        // 底部背板：按钮 + 状态 + 提示 一体
        // m87：底栏加高到 78，状态改画在按钮下方整行——之前固定 x=498 起画，GUI 缩放大时直接怼进 JEI（用户截图实锤）
        // m210：底栏浅带已迁背景层（同上画序坑，盖死底部五钮）；本层只写状态/提示文字。
        boolean run = this.handler.isRunning();
        int stor = 0, term = 0;
        StructureCoreBlockEntity be = be();
        if (be != null) for (long[] e : endsOf(be)) { if (e[1] == 5) term++; else if (e[1] != 6) stor++; }
        int nRun = 0, nBlk = 0, nLack = 0;
        if (be != null) for (int i = 0; i < be.nodes().size(); i++) {
            int st2 = be.nodeStatus(i);
            if (st2 == 1) nRun++; else if (st2 == 2) nBlk++; else if (st2 == 3) nLack++;
        }
        int maxW = workRight() - 16;
        if (com.sdzjz.config.SdzjzConfig.get().canvasStatusOpen) { // m219 状态区可收（顶栏"状态"钮开合）；提示行已迁"帮助"卡
            int sy1 = this.height - (compactChrome() ? 22 : 36), sy2 = this.height - (compactChrome() ? 12 : 24); // m215 行距节奏原样，整体上提填掉旧提示行位
            ctx.drawText(this.textRenderer, run ? "● 运行中" : "○ 已停止", 8, sy1, run ? ON : SciSkin.termSub(), false);
            ctx.drawText(this.textRenderer, fitText("经验 " + fmtNum(this.handler.xp())
                    + "  机器 " + this.handler.machineCount()
                    + "  存储 " + stor + " · 面板 " + term
                    + "  缓存 " + fmtNum(this.handler.buffered())
                    + "  产出 " + (be == null ? "0" : fmtNum(be.prodPerMinView())) + "/分(实测)", maxW - 62), 70, sy1, SciSkin.termInk(), false); // m203
            ctx.drawText(this.textRenderer, fitText("运行 " + nRun + " · 阻塞 " + nBlk + " · 缺料 " + nLack
                    + "  升级∑ 加速" + this.handler.speedLv()
                    + " 数量" + this.handler.countLv()
                    + " 并列" + this.handler.parallelLv()
                    + "  缩放" + Math.round(zoom * 100) + "%", maxW), 8, sy2, SciSkin.termSub(), false);
        }


        // ===== m88：机器库侧栏（概念图左栏——列背包里的机器，点击放入画布）=====
        if (libOpen) {
            int lx = 8, ly = 24, lw = 160, lb = botTop() - 6;
            ctx.fill(lx, ly, lx + lw, lb, 0xE0081120);
            ctx.fill(lx, ly, lx + lw, ly + 14, 0xFF10253A);
            ctx.drawText(this.textRenderer, "机器库（背包）", lx + 6, ly + 3, TXT, false);
            List<ItemStack> lib = libItems();
            int rowH = 20, visible = Math.max(1, (lb - ly - 30) / rowH);
            libScroll = Math.max(0, Math.min(libScroll, Math.max(0, lib.size() - visible)));
            for (int r = 0; r < visible && r + libScroll < lib.size(); r++) {
                ItemStack it = lib.get(r + libScroll);
                int ry = ly + 16 + r * rowH;
                boolean hov = mouseX >= lx && mouseX <= lx + lw && mouseY >= ry && mouseY < ry + rowH;
                if (hov) ctx.fill(lx, ry, lx + lw, ry + rowH, 0x552E6E8E);
                ctx.drawItem(it, lx + 4, ry + 1);
                ctx.drawText(this.textRenderer, fitText(it.getName().getString(), lw - 56), lx + 24, ry + 5, TXT, false);
                String c = "×" + it.getCount();
                ctx.drawText(this.textRenderer, c, lx + lw - 6 - this.textRenderer.getWidth(c), ry + 5, SUB, false);
            }
            if (lib.isEmpty()) ctx.drawText(this.textRenderer, "背包里没有机器", lx + 8, ly + 22, SUB, false);
            ctx.drawText(this.textRenderer, "点击=放 1 台进画布 · 滚轮翻", lx + 6, lb - 12, SUB, false);
        }

        // ===== m85：节点悬停详情（状态/周期/基础产量/产出表）=====
        if (menuLabels.isEmpty() && be != null && mouseY > 20 && mouseY < this.height - 80 && mouseX < workRight()) {
            List<ItemStack> nodes = be.nodes();
            for (int i = 0; i < nodes.size(); i++) {
                int nx = (int) (panX + wnx(be, nodes, i) * zoom), ny = (int) (panY + wny(be, nodes, i) * zoom);
                if (mouseX < nx || mouseX > nx + (int) (NW * zoom) || mouseY < ny || mouseY > ny + (int) (NH * zoom)) continue;
                ItemStack st = nodes.get(i);
                java.util.List<net.minecraft.text.Text> tip = new java.util.ArrayList<>();
                tip.add(Text.literal(st.getName().getString() + " ×" + st.getCount()));
                int stt = be.nodeStatus(i);
                tip.add(Text.literal("状态: " + (!this.handler.isRunning() ? "核心停机"
                        : switch (stt) { case 1 -> "运行中"; case 2 -> "阻塞/关闸"; case 3 -> "缺料"; default -> "待机"; })));
                if (st.getItem() instanceof com.sdzjz.item.MachineItem mi2) {
                    var def = mi2.def();
                    double avg = 0;
                    for (var d : def.outputs()) avg += d.chance() * (d.min() + d.max()) / 2.0;
                    double perMin = avg * (1200.0 / Math.max(1, def.baseIntervalTicks())) * st.getCount();
                    tip.add(Text.literal(String.format("周期 %.1f 秒 · 基础产出 ~%.0f/分", def.baseIntervalTicks() / 20.0, perMin)));
                    StringBuilder sb = new StringBuilder("产出: ");
                    for (int k2 = 0; k2 < def.outputs().size() && k2 < 3; k2++) {
                        if (k2 > 0) sb.append("、");
                        sb.append(new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(def.outputs().get(k2).item()))).getName().getString());
                    }
                    if (def.outputs().size() > 3) sb.append("…");
                    tip.add(Text.literal(sb.toString()));
                    if (def.consumesInputs()) tip.add(Text.literal("消耗输入（对齐原版）"));
                }
                ctx.drawTooltip(this.textRenderer, tip, mouseX, mouseY);
                break;
            }
        }
        ctx.getMatrices().pop();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (libOpen && mouseX >= 8 && mouseX <= 168 && mouseY >= 24 && mouseY <= botTop() - 6) { // m88 机器库滚动
            libScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        if (pickerNode >= 0 || menuOpen || renameGid >= 0 || settingsOpen || helpOpen) return true; // m199 设置窗并入；m219 帮助卡并入
        if (inMap(mouseX, mouseY)) return true; // m110a 地图区不缩放画布
        if (mouseY > 34) {
            zoomToward(verticalAmount > 0 ? 1.1 : 0.9, mouseX, mouseY); // m185 范围走配置 + m186 平滑缓动指哪缩哪
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ================= 右键菜单 =================
    private void openMenu(int x, int y) {
        menuOpen = true;
        menuOpenMs = net.minecraft.util.Util.getMeasuringTimeMs(); // m148 开合动画从零起
        menuHoverP = new float[menuLabels.size()];
        int th = menuTitle != null ? MENU_TITLE_H : 0;
        menuX = Math.min(x, workRight() - MENU_W - 4);
        menuY = Math.min(y, this.height - (menuLabels.size() * MENU_H + th) - 4);
    }

    private void clearMenu() {
        menuLabels.clear();
        menuActions.clear();
        menuIcons.clear();
        menuTexs.clear(); // m313
        menuStyles.clear();
        menuTitle = null;
        menuOpen = false;
    }

    private void addMenu(String label, Runnable action) { addMenu(label, (ItemStack) null, 0, action); }

    private void addMenu(String label, ItemStack icon, Runnable action) { addMenu(label, icon, 0, action); }

    /** m148：style 0=普通 1=危险(红字红条) 2=组首(上方分隔线)。 */
    private void addMenu(String label, ItemStack icon, int style, Runnable action) {
        menuLabels.add(label);
        menuActions.add(action);
        menuIcons.add(icon);
        menuTexs.add(null);
        menuStyles.add(style);
    }

    /** m313：贴图图标行（作者设计的 8 张按钮图，32² 源画 16×16）。 */
    private void addMenu(String label, net.minecraft.util.Identifier tex, int style, Runnable action) {
        menuLabels.add(label);
        menuActions.add(action);
        menuIcons.add(null);
        menuTexs.add(tex);
        menuStyles.add(style);
    }

    private static ItemStack mi(net.minecraft.item.Item it) { return new ItemStack(it); }

    private static net.minecraft.util.Identifier mt(String name) { // m313 菜单贴图路径唯一口径
        return net.minecraft.util.Identifier.of("sdzjz", "textures/gui/menu/" + name + ".png");
    }

    /** m110b 节点设置菜单：右键节点与标题栏齿轮共用同一构建（含单节点启停）。 */
    // ===== m192 画布分组：几何与操作 =====
    private boolean groupsOn() { return com.sdzjz.config.SdzjzConfig.get().canvasGroupsEnabled; }

    /** gid → 成员下标（只收 SCBE 元数据在册且 ≥2 台的组；归属读 NodeTags.nodeGroup，m180 新代码直用不走垫片）。 */
    private java.util.LinkedHashMap<Integer, java.util.List<Integer>> groupMembers(StructureCoreBlockEntity be) {
        java.util.LinkedHashMap<Integer, java.util.List<Integer>> gm = new java.util.LinkedHashMap<>();
        List<ItemStack> nodes = be.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            int g = com.sdzjz.node.NodeTags.nodeGroup(nodes.get(i));
            if (g >= 0 && be.groupsView().containsKey(g)) gm.computeIfAbsent(g, k -> new ArrayList<>()).add(i);
        }
        gm.values().removeIf(l -> l.size() < 2);
        return gm;
    }

    /** 组框矩形（世界坐标）：成员卡包围盒（卡体+升级格行，与悬停判定同口径）外扩 GPAD，
     *  顶上再抬一条 GBAND 标题带。返回 {x1,y1,x2,y2}，标题带=y1..y1+GBAND。 */
    private int[] groupRect(StructureCoreBlockEntity be, List<ItemStack> nodes, java.util.List<Integer> members) {
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
        for (int i : members) {
            if (i >= nodes.size()) continue;
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            x1 = Math.min(x1, nx); y1 = Math.min(y1, ny);
            x2 = Math.max(x2, nx + NW); y2 = Math.max(y2, ny + NH + 26);
        }
        return new int[]{x1 - GPAD, y1 - GPAD - GBAND, x2 + GPAD, y2 + GPAD};
    }

    /** m264 连通分量：从 idx 出发沿机器连线（connections 纯 {from,to} 下标、按无向走）BFS 收齐
     *  "连在一起"的全部节点，升序返回。存储边不算（组是机器下标集合，端点不是节点）。 */
    private java.util.List<Integer> connectedComponent(StructureCoreBlockEntity be, int idx) {
        int n = be.nodes().size();
        java.util.LinkedHashSet<Integer> seen = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        seen.add(idx); q.add(idx);
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (int[] c : be.connections()) { // 双端防越界口径与渲染侧一致
                if (c[0] >= n || c[1] >= n || c[0] < 0 || c[1] < 0) continue;
                int other = c[0] == cur ? c[1] : c[1] == cur ? c[0] : -1;
                if (other >= 0 && seen.add(other)) q.add(other);
            }
        }
        java.util.List<Integer> out = new ArrayList<>(seen);
        java.util.Collections.sort(out);
        return out;
    }

    /** m313 快捷键：当前鼠标悬停的机器节点（与右键命中同一套几何，倒序=上层先中）。 */
    private int hoveredNode() {
        StructureCoreBlockEntity be = be();
        if (be == null) return -1;
        var nodes = be.nodes();
        double wx = wmx(lastMouseX), wy = wmy(lastMouseY);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) return i;
        }
        return -1;
    }

    /** m313：节点所属组（无组=-1）。 */
    private int groupOfNode(int idx) {
        StructureCoreBlockEntity be = be();
        if (be == null || !groupsOn()) return -1;
        for (var ge : groupMembers(be).entrySet())
            if (ge.getValue().contains(idx)) return ge.getKey();
        return -1;
    }

    /** m313 V 键：打开该机器的"主选择器"（与菜单同一套分派条件，取每类第一入口）。 */
    private void openPrimaryPicker(int idx) {
        StructureCoreBlockEntity be = be();
        if (be == null || idx < 0 || idx >= be.nodes().size()) return;
        ItemStack st = be.nodes().get(idx);
        if (st.getItem() instanceof AutoCrafterItem) { openPicker(idx); return; }
        if (st.getItem() instanceof com.sdzjz.item.BrewingTowerItem) { openPotionPicker(idx); return; }
        if (st.getItem() instanceof com.sdzjz.item.EnchantFactoryItem) { openEnchantPicker(idx); return; }
        if (st.getItem() instanceof com.sdzjz.item.VillagerTraderItem) { openTradePicker(idx); return; }
        if (st.getItem() instanceof com.sdzjz.item.CropFarmItem) { openCropPicker(idx); return; }
        if (StructureCoreBlockEntity.machineFilterable(st)) { openMachineFilterPicker(idx); return; }
        if (StructureCoreBlockEntity.isFilter(st) || StructureCoreBlockEntity.isTrash(st)
                || StructureCoreBlockEntity.isExtractor(st)) openFilterPicker(idx);
    }

    /** 建组：当前选中集发服务端（≥2 台才发，服务端还会再验一遍），发完清选。 */
    private void createGroupFromSelection() {
        StructureCoreBlockEntity be = be();
        BlockPos p = this.handler.blockPos();
        if (be == null || p == null) return;
        List<Integer> ms = new ArrayList<>();
        for (int i : selected) if (i >= 0 && i < be.nodes().size()) ms.add(i);
        if (ms.size() < 2) return;
        ClientPlayNetworking.send(new com.sdzjz.net.NodeGroupPayload(p, -1, "", ms));
        selected.clear();
    }

    /** 组菜单（右键标题带）：重命名 / 解散（解散纯视觉，机器与连线不动）。 */
    private void openGroupMenu(int gid, int atX, int atY) {
        StructureCoreBlockEntity be = be();
        BlockPos p = this.handler.blockPos();
        if (be == null || p == null) return;
        clearMenu();
        menuTitle = be.groupsView().getOrDefault(gid, "组" + gid);
        addMenu("重命名组…", mt("group_rename"), 0, () -> openRename(gid)); // m313 用户图标
        addMenu("解散该组", mt("group_disband"), 1,
                () -> ClientPlayNetworking.send(new com.sdzjz.net.NodeGroupPayload(p, gid, "", java.util.List.of())));
        addMenu("取消", (ItemStack) null, 2, () -> {});
        openMenu(atX, atY);
    }

    private void openRename(int gid) {
        StructureCoreBlockEntity be = be();
        if (be == null) return;
        renameGid = gid;
        renameField.setText(be.groupsView().getOrDefault(gid, ""));
        this.setFocused(renameField);
        renameField.setFocused(true);
    }

    private void closeRename() {
        renameGid = -1;
        renameField.setFocused(false);
    }

    private void confirmRename() {
        BlockPos p = this.handler.blockPos();
        String nm = renameField.getText().trim();
        if (p != null && renameGid >= 0 && !nm.isEmpty())
            ClientPlayNetworking.send(new com.sdzjz.net.NodeGroupPayload(p, renameGid, nm, java.util.List.of()));
        closeRename();
    }

    /** 重命名小窗（照 renderPicker 的 pickerField 写法：每帧摆位再渲染）。 */
    private void renderRename(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int w = 200, h = 58, px = (this.width - w) / 2, py = (this.height - h) / 2;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400); // m202 同病同修：抬z防卡内物品穿透
        SciSkin.drawCard(ctx, px, py, w, h, SciSkin.FRAME);
        ctx.drawText(this.textRenderer, "重命名组（回车确认·Esc取消）", px + 8, py + 7, SciSkin.TXT_HI, false);
        renameField.setX(px + 8);
        renameField.setY(py + 26);
        renameField.render(ctx, mouseX, mouseY, delta);
        ctx.getMatrices().pop();
    }

    // ===== m199 画布设置面板（六项画布客户端配置 + 恢复默认；机器数值项仍走 config/sdzjz.json）=====
    private void openSettings() {
        if (menuOpen) clearMenu();
        if (pickerNode >= 0) closePicker();
        if (renameGid >= 0) closeRename();
        wireOutField.setText(com.sdzjz.config.SdzjzConfig.get().canvasWireOutColor); // 开窗对齐配置现值（可能被恢复默认/改文件动过）
        wireInField.setText(com.sdzjz.config.SdzjzConfig.get().canvasWireInColor);
        bgField.setText(com.sdzjz.config.SdzjzConfig.get().canvasBgColor);       // m217
        gridColField.setText(com.sdzjz.config.SdzjzConfig.get().canvasGridColor);
        settingsOpen = true;
    }

    private void closeSettings() {
        settingsOpen = false;
        settSlDrag = -1; // m223 兜底：拖着滑杆按 Esc 关窗不留残拖
        wireOutField.setFocused(false);
        wireInField.setFocused(false);
        bgField.setFocused(false);   // m217
        gridColField.setFocused(false);
        com.sdzjz.config.SdzjzConfig.save(); // 关窗落盘（开关/步进即点即存，颜色打字只写实例，这里兜底）
    }

    /** 面板左上角 {px, py}（居中；行区 py+24 起每行 SETT_ROW，几何被 renderSettings/settingsClick 共用，改必同改）。 */
    private int[] settPos() { return new int[]{(this.width - SETT_W) / 2, Math.max(2, (this.height - SETT_H) / 2)}; } // m217 变高后钳顶：极小视口宁可底部出屏也保头部可达

    // ===== m223 颜色行↔调节目标公共表（渲染与点击同源，改必同改）=====
    /** 面板行号→调节下标（3出线/4进线/6背景/7网格；其余 -1）。 */
    private static int settSelOfRow(int r) { return r == 3 ? 0 : r == 4 ? 1 : r == 6 ? 2 : r == 7 ? 3 : -1; }
    /** 调节下标→输入框（序=setFs 序：出线/进线/背景/网格）。 */
    private TextFieldWidget settColorField(int sel) { return sel == 0 ? wireOutField : sel == 1 ? wireInField : sel == 2 ? bgField : gridColField; }
    /** 调节下标→当前生效色（非法/空自动回退主题/默认，滑杆起点即所见色）。 */
    /** m239 根因修复：scopeCanvas 只在 render 帧内开（m214 try/finally），而滑杆写值走 mouse 事件路径——
     *  作用域是关的，canvasBg() 空值回退落到**终端主题**浅墨（紫晶≈E7EAF3）：点一下滑杆起点就是白、
     *  当场写进背景色="白色阴魂不散/清了又回来"。取色收口函数自己保证画布作用域（保存/恢复，
     *  渲染期已 true 不破坏）。 */
    private static int settColorVal(int sel) {
        boolean prev = SciSkin.scopedCanvas();
        SciSkin.scopeCanvas(true);
        try { return sel == 0 ? SciSkin.wireOut() : sel == 1 ? SciSkin.wireIn() : sel == 2 ? SciSkin.canvasBg() : SciSkin.canvasGridBase(); }
        finally { SciSkin.scopeCanvas(prev); }
    }
    /** m223 按鼠标位写所选色某通道（照 m202 thApplySlider：改串→setText 触发 listener→配置→SciSkin 缓存重解析=即时预览）。 */
    private void settApplySlider(double mx) {
        int px = settPos()[0];
        int v = (int) Math.round(Math.max(0, Math.min(1, (mx - (px + SETT_SL_X)) / (double) (SETT_SL_W - 4))) * 255);
        int cv = settColorVal(settColSel);
        int r = (cv >> 16) & 0xFF, g = (cv >> 8) & 0xFF, b = cv & 0xFF;
        if (settSlDrag == 0) r = v; else if (settSlDrag == 1) g = v; else b = v;
        settColorField(settColSel).setText(String.format("%02X%02X%02X", r, g, b));
    }

    /** RRGGBB 合法性（允许带#，1~6 位十六进制）；只作红线提示，非法值渲染层自会回退默认（m198 parseHex）。 */
    private static boolean hexOk(String s) {
        if (s == null) return false;
        String t = s.trim().replace("#", "");
        if (t.isEmpty() || t.length() > 6) return false;
        for (int i = 0; i < t.length(); i++) if (Character.digit(t.charAt(i), 16) < 0) return false;
        return true;
    }

    /** 设置面板渲染（照 renderRename：每帧摆位再渲染）。 */
    private void renderSettings(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int px = settPos()[0], py = settPos()[1];
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400); // m202 抬z：卡内物品图标画在带深度的高z，z0后画填充会被穿透（终端实锤同病）
        SciSkin.drawCard(ctx, px, py, SETT_W, SETT_H, SciSkin.FRAME);
        ctx.drawText(this.textRenderer, "画布设置", px + 8, py + 7, SciSkin.TXT_HI, false);
        String[] labels = {"缩放平滑动效", "连线宽度随缩放", "线宽封顶倍率", "出线颜色 RRGGBB", "进线颜色 RRGGBB", "跨组连线归并 ×N",
                "背景色(空=随主题)", "网格色(空=随主题)", "网格浓度", "暗角强度"}; // m217 背景四行
        boolean[] tog = {c.canvasSmoothZoom, c.canvasWireScaleWithZoom, false, false, false, c.canvasGroupBundleWires, false, false, false, false};
        for (int r = 0; r < 10; r++) {
            int ry = py + SETT_ROW0 + r * SETT_ROW; // m262 行区起点收口
            ctx.drawText(this.textRenderer, labels[r], px + 10, ry + 3, SciSkin.TXT, false);
            if (r == 0 || r == 1 || r == 5) { // 开关药丸（开=运行绿族，关=离线灰）
                boolean on = tog[r];
                int bx = px + SETT_W - 56;
                boolean hov = mouseX >= bx && mouseX <= bx + 46 && mouseY >= ry && mouseY <= ry + 14;
                ctx.fill(bx - 1, ry - 1, bx + 47, ry + 15, on ? SciSkin.ON : SciSkin.CELL_FRM);
                ctx.fill(bx, ry, bx + 46, ry + 14, on ? SciSkin.ON_DARK : (hov ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE));
                String tx = on ? "开" : "关";
                ctx.drawText(this.textRenderer, tx, bx + 23 - this.textRenderer.getWidth(tx) / 2, ry + 3,
                        on ? SciSkin.ON : SciSkin.OFF_GRAY, false);
            } else if (r == 2 || r == 8 || r == 9) { // 步进器 [−] 值 [+]（照传感器阈值钮，屏幕坐标版）
                stBox(ctx, px + SETT_W - 80, ry, "−", mouseX, mouseY);
                stBox(ctx, px + SETT_W - 24, ry, "+", mouseX, mouseY);
                String v = r == 2 ? "×" + (Math.round(Math.max(0.2, c.canvasWireMaxScale) * 10) / 10.0) // double 拼串恒小数点，免 Locale 逗号坑
                         : r == 8 ? Math.round(Math.max(0, Math.min(3, c.canvasGridStrength)) * 100) + "%"
                                  : Math.round(Math.max(0, Math.min(2, c.canvasVignetteStrength)) * 100) + "%";
                ctx.drawText(this.textRenderer, v, px + SETT_W - 42 - this.textRenderer.getWidth(v) / 2, ry + 3, SciSkin.TXT_HI, false);
            } else { // 颜色行：输入框 + 生效色样片（样片直读生效色=非法/空自动显回退色，红下划线只提示"非空且非法"——背景/网格行空=跟随主题属合法）
                int sel = settSelOfRow(r); // m223 点行/点样片可选中，下方滑杆调它
                TextFieldWidget f = settColorField(sel);
                f.setX(px + SETT_W - 96);
                f.setY(ry);
                f.render(ctx, mouseX, mouseY, delta);
                int sw = settColorVal(sel);
                ctx.fill(px + SETT_W - 25, ry - 1, px + SETT_W - 9, ry + 15,
                        sel == settColSel ? SciSkin.ACCENT : SciSkin.CELL_FRM); // m223 选中环（照 m202 终端选中环工艺）
                ctx.fill(px + SETT_W - 24, ry, px + SETT_W - 10, ry + 14, sw);
                boolean emptyOk = (r == 6 || r == 7) && f.getText().isBlank();
                if (!emptyOk && !hexOk(f.getText())) ctx.fill(px + SETT_W - 96, ry + 16, px + SETT_W - 38, ry + 17, SciSkin.RED);
            }
        }
        // ===== m223 RGB 滑杆调节区（照 m202 终端主题编辑器工艺；拖滑杆写十六进制串→listener→配置→即时预览）=====
        int sy0 = py + SETT_SL_Y;
        ctx.drawText(this.textRenderer, "调节: " + SETT_COLOR_NAMES[settColSel] + "（点色行切换）", px + 10, sy0, SciSkin.TXT, false);
        int cvSel = settColorVal(settColSel);
        String[] chn = {"R", "G", "B"};
        for (int ch = 0; ch < 3; ch++) {
            int v = (cvSel >> (16 - ch * 8)) & 0xFF;
            int sy = sy0 + 14 + ch * SETT_SL_SP; // m239 内距收口
            ctx.drawText(this.textRenderer, chn[ch], px + 14, sy + 1, SciSkin.SUB, false);
            ctx.fill(px + SETT_SL_X - 1, sy - 1, px + SETT_SL_X + SETT_SL_W + 1, sy + 11, SciSkin.CELL_FRM); // 轨
            ctx.fill(px + SETT_SL_X, sy, px + SETT_SL_X + SETT_SL_W, sy + 10, SciSkin.CELL);
            int kx = px + SETT_SL_X + (int) Math.round(v / 255.0 * (SETT_SL_W - 4));
            ctx.fill(px + SETT_SL_X, sy, kx + 2, sy + 10, SciSkin.withAlpha(SciSkin.ACCENT, 0.55f)); // 已填段
            ctx.fill(kx, sy - 1, kx + 4, sy + 11, settSlDrag == ch ? SciSkin.TXT_MAX : SciSkin.ACCENT); // 滑钮
            ctx.fill(kx + 1, sy, kx + 3, sy + 10, SciSkin.TXT_MAX);
            ctx.drawText(this.textRenderer, String.valueOf(v), px + SETT_SL_X + SETT_SL_W + 8, sy + 1, SciSkin.TXT_HI, false);
        }
        int ry6 = py + SETT_PRESET_Y; // m211 主题预设行：5 套一键换肤（m214 只写画布7键；m223 下移至常量位）
        ctx.drawText(this.textRenderer, "主题预设", px + 10, ry6 + 3, SciSkin.TXT, false);
        int hovPk = -1;
        for (int k = 0; k < SciSkin.TERM_PRESET_NAMES.length; k++) {
            int bx = px + SETT_W - 126 + k * 24;
            boolean hv = mouseX >= bx && mouseX <= bx + 20 && mouseY >= ry6 - 1 && mouseY <= ry6 + 15;
            if (hv) hovPk = k;
            ctx.fill(bx - 1, ry6 - 1, bx + 21, ry6 + 15, hv ? SciSkin.BTN_FRM_HOV : SciSkin.CELL_FRM);
            ctx.fill(bx, ry6, bx + 20, ry6 + 14, SciSkin.hex(SciSkin.TERM_PRESETS[k][0], SciSkin.termBase()));   // 底=预设主色（照终端样片工艺）
            ctx.fill(bx + 3, ry6 + 3, bx + 17, ry6 + 11, SciSkin.hex(SciSkin.TERM_PRESETS[k][2], SciSkin.termAccent())); // 心=预设强调
        }
        if (hovPk >= 0) ctx.drawText(this.textRenderer, SciSkin.TERM_PRESET_NAMES[hovPk], px + 62, ry6 + 3, SciSkin.TXT_HI, false);
        int rx = px + (SETT_W - 64) / 2, rby = py + SETT_RESET_Y; // 恢复默认（回本面板十项；m223 下移至常量位）
        boolean rh = mouseX >= rx && mouseX <= rx + 64 && mouseY >= rby && mouseY <= rby + 16;
        ctx.fill(rx - 1, rby - 1, rx + 65, rby + 17, rh ? SciSkin.BTN_FRM_HOV : SciSkin.BTN_FRM);
        ctx.fill(rx, rby, rx + 64, rby + 16, rh ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE);
        ctx.drawText(this.textRenderer, "恢复默认", rx + 32 - this.textRenderer.getWidth("恢复默认") / 2, rby + 4,
                rh ? SciSkin.TXT_MAX : SciSkin.TXT, false);
        String tip = "即改即存 · Esc/点外=关"; // m262 挪标题行右侧（原底行与恢复默认钮重叠且占高）
        ctx.drawText(this.textRenderer, tip, px + SETT_W - 8 - this.textRenderer.getWidth(tip), py + 7, SciSkin.SUB, false);
        ctx.getMatrices().pop();
    }

    /** m219 帮助卡：操作提示行迁入（原底带一行塞不下的内容摊开写；纯静态无交互，点哪都关）。 */
    private void renderHelp(DrawContext ctx) {
        int hw = 236, hh = 124; // m313 快捷键两行加高
        int px = Math.max(8, Math.min(336, workRight() - hw - 8)), py = 22; // 锚"帮助"钮下方，窄屏向左让位
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400); // m202 抬z口径
        SciSkin.drawCard(ctx, px, py, hw, hh, SciSkin.FRAME);
        ctx.drawText(this.textRenderer, "操作帮助", px + 8, py + 7, SciSkin.TXT_HI, false);
        String[] lines = {
                "右键节点=菜单 · 拖节点=移动",
                "绿口拖线=连线 · 滚轮=缩放",
                "状态灯：绿=运行 黄=阻塞 红=缺料",
                "节点色：青=生产 橙=加工 紫=逻辑 绿=农场",
                "快捷键(悬停节点)：P暂停 X断线 Del取出 V选择", // m313
                "G组合所选 · Shift+G解散组 · F2改组名",
        };
        for (int i = 0; i < lines.length; i++)
            ctx.drawText(this.textRenderer, lines[i], px + 10, py + 24 + i * 14, i < 2 ? SciSkin.TXT : SciSkin.SUB, false);
        ctx.drawText(this.textRenderer, "点任意处关闭", px + 8, py + hh - 13, SciSkin.SUB, false);
        ctx.getMatrices().pop();
    }

    /** m199 步进小方钮（纯 fill 不依赖字形）。 */
    private void stBox(DrawContext ctx, int bx, int by, String s, int mouseX, int mouseY) {
        boolean hov = mouseX >= bx && mouseX <= bx + 14 && mouseY >= by && mouseY <= by + 14;
        ctx.fill(bx - 1, by - 1, bx + 15, by + 15, hov ? SciSkin.BTN_FRM_HOV : SciSkin.BTN_FRM);
        ctx.fill(bx, by, bx + 14, by + 14, hov ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE);
        ctx.drawText(this.textRenderer, s, bx + 7 - this.textRenderer.getWidth(s) / 2, by + 3, hov ? SciSkin.TXT_MAX : SciSkin.TXT, false);
    }

    /** m199 设置面板点击派发（几何与 renderSettings 同一套）。恒返回 true=modal 吞穿透（m103 教训）。 */
    private boolean settingsClick(double mouseX, double mouseY, int button) {
        int px = settPos()[0], py = settPos()[1];
        if (mouseX < px || mouseX > px + SETT_W || mouseY < py || mouseY > py + SETT_H) { closeSettings(); return true; } // 窗外点=关
        TextFieldWidget[] setFs = {wireOutField, wireInField, bgField, gridColField}; // m217 四框互斥聚焦（m202 非children输入框必须显式聚焦）
        for (int i = 0; i < setFs.length; i++)
            if (setFs[i].mouseClicked(mouseX, mouseY, button)) {
                for (TextFieldWidget o : setFs) o.setFocused(o == setFs[i]);
                settColSel = i; // m223 点进哪个色框，滑杆就调哪个（setFs 序=调节下标序）
                return true;
            }
        for (TextFieldWidget f : setFs) f.setFocused(false);
        if (button != 0) return true;
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get();
        for (int r = 0; r < 10; r++) { // m217 6→10 行
            int ry = py + SETT_ROW0 + r * SETT_ROW; // m262 行区起点收口
            if (mouseY < ry || mouseY > ry + 14) continue;
            if ((r == 0 || r == 1 || r == 5) && mouseX >= px + SETT_W - 56 && mouseX <= px + SETT_W - 10) {
                if (r == 0) c.canvasSmoothZoom = !c.canvasSmoothZoom;
                else if (r == 1) c.canvasWireScaleWithZoom = !c.canvasWireScaleWithZoom;
                else c.canvasGroupBundleWires = !c.canvasGroupBundleWires;
                com.sdzjz.config.SdzjzConfig.save();
                return true;
            }
            if (r == 2 || r == 8 || r == 9) {
                int d = 0;
                if (mouseX >= px + SETT_W - 80 && mouseX <= px + SETT_W - 66) d = -1;
                else if (mouseX >= px + SETT_W - 24 && mouseX <= px + SETT_W - 10) d = 1;
                if (d != 0) { // 步进0.2，clamp 各行口径，×5/5 防浮点漂移
                    if (r == 2)      c.canvasWireMaxScale     = Math.round(Math.max(0.2, Math.min(8.0, c.canvasWireMaxScale     + d * 0.2)) * 5) / 5.0;
                    else if (r == 8) c.canvasGridStrength     = Math.round(Math.max(0.0, Math.min(3.0, c.canvasGridStrength     + d * 0.2)) * 5) / 5.0;
                    else             c.canvasVignetteStrength = Math.round(Math.max(0.0, Math.min(2.0, c.canvasVignetteStrength + d * 0.2)) * 5) / 5.0;
                    com.sdzjz.config.SdzjzConfig.save();
                    return true;
                }
            }
            int selR = settSelOfRow(r); // m223 点色行（标签区/样片区，输入框命中已在前面处理）=选中该色为调节目标
            if (selR >= 0 && (mouseX <= px + SETT_W - 98 || mouseX >= px + SETT_W - 26)) { settColSel = selR; return true; }
        }
        int sy0 = py + SETT_SL_Y + 14; // m223 滑杆命中（三轨，几何与 renderSettings 同一套）：点轨=起拖并立即写值
        if (mouseX >= px + SETT_SL_X - 2 && mouseX <= px + SETT_SL_X + SETT_SL_W + 2) {
            for (int ch = 0; ch < 3; ch++) {
                int sy = sy0 + ch * SETT_SL_SP; // m239 内距收口
                if (mouseY >= sy - 2 && mouseY <= sy + 12) { settSlDrag = ch; settApplySlider(mouseX); return true; }
            }
        }
        int ry6 = py + SETT_PRESET_Y; // m211 主题预设点击（几何与 renderSettings 同一套，改必同改；m223 常量位）
        if (mouseY >= ry6 - 1 && mouseY <= ry6 + 15) {
            for (int k = 0; k < SciSkin.TERM_PRESET_NAMES.length; k++) {
                int bx = px + SETT_W - 126 + k * 24;
                if (mouseX >= bx && mouseX <= bx + 20) { // m214 分家：这行只写画布 7 键，终端主题在数据面板里自己选
                    String[] pk = SciSkin.TERM_PRESETS[k];
                    c.canvasBase = pk[0]; c.canvasBaseDeep = pk[1]; c.canvasAccent = pk[2]; c.canvasAccentDeep = pk[3];
                    c.canvasInk = pk[4]; c.canvasFrame = pk[5]; c.canvasHi = pk[6];
                    com.sdzjz.config.SdzjzConfig.save();
                    return true;
                }
            }
        }
        int rx = px + (SETT_W - 64) / 2, rby = py + SETT_RESET_Y; // m223 常量位
        if (mouseX >= rx && mouseX <= rx + 64 && mouseY >= rby && mouseY <= rby + 16) { // 恢复默认：new 实例取字段默认，零硬编码重复
            com.sdzjz.config.SdzjzConfig d = new com.sdzjz.config.SdzjzConfig();
            c.canvasSmoothZoom = d.canvasSmoothZoom;
            c.canvasWireScaleWithZoom = d.canvasWireScaleWithZoom;
            c.canvasWireMaxScale = d.canvasWireMaxScale;
            c.canvasGroupBundleWires = d.canvasGroupBundleWires;
            wireOutField.setText(d.canvasWireOutColor); // setText 触发 listener 回写配置串
            wireInField.setText(d.canvasWireInColor);
            bgField.setText(d.canvasBgColor);       // m217 背景四项一并回默认
            gridColField.setText(d.canvasGridColor);
            c.canvasGridStrength = d.canvasGridStrength;
            c.canvasVignetteStrength = d.canvasVignetteStrength;
            com.sdzjz.config.SdzjzConfig.save();
        }
        return true;
    }

    // ===== m193 连线归并 =====
    private static final int GROUP_ENC = 0x40000000; // 归并锚编码高位：置位=组(低位gid)，否则=节点下标

    /** 归并锚点几何（世界坐标）：组=组框，节点=卡。返回 {中心x, 出线y, 左缘x, 右缘x}；查不到=null 跳过。 */
    private double[] mmAnchorGeom(StructureCoreBlockEntity be, List<ItemStack> nodes,
                                  java.util.Map<Integer, int[]> gRect, int enc) {
        if ((enc & GROUP_ENC) != 0) {
            int[] r = gRect.get(enc & ~GROUP_ENC);
            if (r == null) return null;
            return new double[]{(r[0] + r[2]) / 2.0, (r[1] + GBAND + r[3]) / 2.0, r[0], r[2]};
        }
        if (enc < 0 || enc >= nodes.size()) return null;
        int nx = wnx(be, nodes, enc), ny = wny(be, nodes, enc);
        return new double[]{nx + NW / 2.0, ny + NH / 2.0, nx, nx + NW};
    }

    /** 归并线 ×N 徽章：线中点小字（N<2 不画；底衬走 BACKDROP 半透，零新色字面量）。 */
    private void drawBundleBadge(DrawContext ctx, float midX, float midY, int n, boolean lit) {
        if (n < 2) return;
        String bt = "×" + n;
        int tw = this.textRenderer.getWidth(bt);
        int bx = (int) midX - tw / 2, by = (int) midY - 5;
        ctx.fill(bx - 2, by - 2, bx + tw + 2, by + 9, SciSkin.withAlpha(SciSkin.termInk(), 0.78f));
        ctx.drawText(this.textRenderer, bt, bx, by, lit ? SciSkin.TXT_HI : SUB, false);
    }

    /** m235 配方摘要：按配方格序取前两种材料"N×名+N×名+…"。 */
    private static String planLabel(com.sdzjz.machine.CraftPlanner.Plan pl) {
        StringBuilder b = new StringBuilder();
        int k = 0;
        for (var en : pl.needs().entrySet()) {
            if (k++ == 2) { b.append("+…"); break; }
            if (b.length() > 0) b.append('+');
            b.append(en.getValue()).append('×')
             .append(new ItemStack(Registries.ITEM.get(Identifier.of(en.getKey()))).getName().getString());
        }
        return b.toString();
    }

    private void openNodeMenu(int idx, int atX, int atY) {
        StructureCoreBlockEntity be = be();
        if (be == null || idx < 0 || idx >= be.nodes().size()) return;
        final ItemStack st = be.nodes().get(idx);
        BlockPos p = this.handler.blockPos();
        clearMenu();
        menuTitle = st.getName().getString(); // m148 标题带=机器名
        if (StructureCoreBlockEntity.nodePaused(st)) // m313 暂停态图标换用户设计贴图，恢复态保留绿染料
            addMenu("恢复运行", mi(net.minecraft.item.Items.LIME_DYE),
                    () -> { if (p != null) ClientPlayNetworking.send(new com.sdzjz.net.NodePausePayload(p, idx)); });
        else
            addMenu("暂停节点", mt("pause_node"), 0,
                    () -> { if (p != null) ClientPlayNetworking.send(new com.sdzjz.net.NodePausePayload(p, idx)); });
        if (st.getItem() instanceof com.sdzjz.item.MachineItem) { // m123 融合升阶/拆解降阶（普通→超级→神级→GM，8×战力/阶）
            int mt = StructureCoreBlockEntity.machineTier(st);
            String[] TN = {"普通", "超级", "神级", "GM"};
            // m128(F1)：条件改全画布同类同阶总数——m78 后单节点恒为 ×1，按本节点 count 判菜单永不出现；
            // 服务端融合会先跨节点聚敛(gatherSame)再合成，客户端只负责"够不够格显示入口"。
            int sameTotal = 0;
            for (ItemStack o : be.nodes())
                if (o.getItem() == st.getItem() && StructureCoreBlockEntity.machineTier(o) == mt) sameTotal += o.getCount();
            if (mt < 3 && sameTotal >= 4)
                addMenu("融合：4台→" + TN[mt + 1] + "×1", mi(net.minecraft.item.Items.ANVIL), 2,
                        () -> { if (p != null) ClientPlayNetworking.send(new com.sdzjz.net.NodeFusePayload(p, idx, true)); });
            if (mt > 0)
                addMenu("拆解：1台→" + TN[mt - 1] + "×4", mi(net.minecraft.item.Items.GRINDSTONE),
                        () -> { if (p != null) ClientPlayNetworking.send(new com.sdzjz.net.NodeFusePayload(p, idx, false)); });
        }
        addMenu("断开全部连线", mt("disconnect_all"), 2, () -> clearLinksOfMachine(idx)); // m313 用户图标
        if (st.getItem() instanceof AutoCrafterItem) {
            addMenu("选择合成目标", mi(net.minecraft.item.Items.CRAFTING_TABLE), 2, () -> openPicker(idx));
            String tgtR = StructureCoreBlockEntity.craftTarget(st); // m235 多配方目标可手选配方（单配方不显示不添乱）
            if (!tgtR.isEmpty() && this.client != null && this.client.world != null) {
                java.util.List<com.sdzjz.machine.CraftPlanner.Plan> psR =
                        com.sdzjz.machine.CraftPlanner.plans(this.client.world, tgtR);
                if (psR.size() > 1) {
                    String curR = StructureCoreBlockEntity.craftRecipe(st);
                    String lbl = "配方: 自动(按库存)";
                    for (var pp : psR) if (pp.recipeId().equals(curR)) { lbl = "配方: " + planLabel(pp); break; }
                    addMenu(lbl + " → 换", mi(net.minecraft.item.Items.KNOWLEDGE_BOOK),
                            () -> { if (p != null) ClientPlayNetworking.send(new NodeFilterPayload(p, idx, "#cr")); });
                }
            }
        }
        if (st.getItem() instanceof com.sdzjz.item.BrewingTowerItem)
            addMenu("选择目标药水", mi(net.minecraft.item.Items.BREWING_STAND), 2, () -> openPotionPicker(idx));
        if (st.getItem() instanceof com.sdzjz.item.EnchantFactoryItem)
            addMenu("选择目标附魔", mi(net.minecraft.item.Items.ENCHANTED_BOOK), 2, () -> openEnchantPicker(idx));
        if (st.getItem() instanceof com.sdzjz.item.VillagerTraderItem)
            addMenu("选择交易条目", mi(net.minecraft.item.Items.EMERALD), 2, () -> openTradePicker(idx));
        if (st.getItem() instanceof com.sdzjz.item.CropFarmItem)
            addMenu("选择种植作物", mi(net.minecraft.item.Items.WHEAT), 2, () -> openCropPicker(idx));
        if (StructureCoreBlockEntity.isFilter(st)) {
            addMenu("配置过滤物品…", mi(net.minecraft.item.Items.COMPARATOR), 2, () -> openFilterPicker(idx));
            addMenu(StructureCoreBlockEntity.filterBlacklist(st) ? "切为白名单" : "切为黑名单", mi(net.minecraft.item.Items.PAPER),
                    () -> { if (p != null) ClientPlayNetworking.send(new NodeFilterPayload(p, idx, "")); });
        }
        if (StructureCoreBlockEntity.isSwitch(st)) {
            addMenu(StructureCoreBlockEntity.switchOn(st) ? "切为:关闭" : "切为:开启", mi(net.minecraft.item.Items.LEVER), 2,
                    () -> { if (p != null) ClientPlayNetworking.send(new NodeSwitchPayload(p, idx)); });
        }
        if (StructureCoreBlockEntity.isExtractor(st)) { // m154 启停复用开关收包口
            addMenu(StructureCoreBlockEntity.extractorOn(st) ? "停止抽取" : "开始抽取", mi(net.minecraft.item.Items.PISTON), 2,
                    () -> { if (p != null) ClientPlayNetworking.send(new NodeSwitchPayload(p, idx)); });
            addMenu("抽取量: " + StructureCoreBlockEntity.extractorRate(st) + "/轮 → 换挡", // m163a 五挡循环 64→512→4096→32768→262144
                    mi(net.minecraft.item.Items.HOPPER),
                    () -> { if (p != null) ClientPlayNetworking.send(new NodeFilterPayload(p, idx, "#xr")); });
            int flN = StructureCoreBlockEntity.filterList(st).size(); // m160 内置白名单
            addMenu("抽取白名单" + (flN > 0 ? "(" + flN + ")" : "") + "…", mi(net.minecraft.item.Items.COMPARATOR),
                    () -> openFilterPicker(idx));
            addMenu("自动启停·监测物品…", mi(net.minecraft.item.Items.OBSERVER), 2, () -> openSensorPicker(idx)); // m160
            if (!StructureCoreBlockEntity.sensorItem(st).isEmpty()) {
                addMenu(StructureCoreBlockEntity.sensorLess(st) ? "改为:高于阈值才抽" : "改为:低于阈值才抽",
                        mi(net.minecraft.item.Items.REPEATER),
                        () -> { if (p != null) ClientPlayNetworking.send(new NodeSensorPayload(p, idx, "",
                                StructureCoreBlockEntity.sensorThreshold(st), !StructureCoreBlockEntity.sensorLess(st))); });
                addMenu("清除自动启停", mi(net.minecraft.item.Items.BARRIER),
                        () -> { if (p != null) ClientPlayNetworking.send(new NodeSensorPayload(p, idx, "§clear",
                                StructureCoreBlockEntity.sensorThreshold(st), StructureCoreBlockEntity.sensorLess(st))); });
            }
        }
        if (StructureCoreBlockEntity.isSensor(st)) {
            addMenu("监测物品…", mi(net.minecraft.item.Items.OBSERVER), 2, () -> openSensorPicker(idx));
            addMenu(StructureCoreBlockEntity.sensorLess(st) ? "改为:高于阈值放行" : "改为:低于阈值放行", mi(net.minecraft.item.Items.REPEATER),
                    () -> { if (p != null) ClientPlayNetworking.send(new NodeSensorPayload(p, idx, "",
                            StructureCoreBlockEntity.sensorThreshold(st), !StructureCoreBlockEntity.sensorLess(st))); });
        }
        if (StructureCoreBlockEntity.machineFilterable(st)) { // m149 二级界面：熔炉选烧什么/多产物机选产物（m313 双图标分家）
            boolean sm313 = st.getItem() instanceof com.sdzjz.item.MachineItem mif && com.sdzjz.machine.Machines.smelterFamily(mif.def().id());
            addMenu(sm313 ? "选择烧什么…" : "选择产物…", mt(sm313 ? "pick_smelt" : "pick_product"), 2, () -> openMachineFilterPicker(idx));
        }
        if (StructureCoreBlockEntity.isTrash(st)) { // m160 安全桶：白名单空=连啥吞啥
            int tfN = StructureCoreBlockEntity.filterList(st).size();
            addMenu("吞噬白名单" + (tfN > 0 ? "(" + tfN + ")" : "·全吞") + "…", mi(net.minecraft.item.Items.COMPARATOR), 2,
                    () -> openFilterPicker(idx));
        }
        if (groupsOn()) { // m264 组合两入口（作者点名：Shift左键多选后能组合，相连的也能组合）——纯客户端拼成员集走 m191 建组包
            java.util.LinkedHashSet<Integer> selPlus = new java.util.LinkedHashSet<>(selected);
            selPlus.removeIf(k -> k < 0 || k >= be.nodes().size());
            selPlus.add(idx); // 右键的这台自动并入，Shift 选完不必再补选它
            if (selPlus.size() >= 2 && selPlus.size() <= 512) { // 512=服务端伪造包熔断上限，超限不给静默哑口（m99 教训）
                final java.util.List<Integer> msSel = new ArrayList<>(selPlus);
                addMenu("组合所选(" + msSel.size() + "台)", mt("group_selected"), 2, () -> { // m313 用户图标
                    if (p != null) { ClientPlayNetworking.send(new com.sdzjz.net.NodeGroupPayload(p, -1, "", msSel)); selected.clear(); }
                });
            }
            final java.util.List<Integer> comp = connectedComponent(be, idx); // 沿连线收齐"连在一起"的整串
            if (comp.size() >= 2 && comp.size() <= 512)
                addMenu("组合相连(" + comp.size() + "台)", mi(net.minecraft.item.Items.CHAIN), selPlus.size() >= 2 ? 0 : 2, () -> {
                    if (p != null) { ClientPlayNetworking.send(new com.sdzjz.net.NodeGroupPayload(p, -1, "", comp)); selected.clear(); }
                });
        }
        addMenu("取出机器", mt("remove_machine"), 1,
                () -> { if (p != null) ClientPlayNetworking.send(new NodeRemovePayload(p, idx)); }); // m148 危险项垫底红显；m313 用户图标
        addMenu("取消", (ItemStack) null, 2, () -> {});
        openMenu(atX, atY);
    }

    /** m110b 标题栏齿轮（滑杆式设置图标，纯 fill 不依赖字体字形）。 */
    private void drawGear(DrawContext ctx, int x, int y) {
        ctx.fill(x, y + 1, x + 9, y + 2, SUB);
        ctx.fill(x, y + 4, x + 9, y + 5, SUB);
        ctx.fill(x, y + 7, x + 9, y + 8, SUB);
        ctx.fill(x + 2, y, x + 4, y + 3, TXT);
        ctx.fill(x + 5, y + 3, x + 7, y + 6, TXT);
        ctx.fill(x + 1, y + 6, x + 3, y + 9, TXT);
    }

    /** m148 菜单 3A 化：110ms 弹入（缩放+淡入 easeOutCubic）、逐行悬停指数缓动（底色渐显+
     *  强调条滑出+文字右移 2px+颜色插值）、标题带（机器名+青下划线）、组分隔线、行图标（0.8×物品）、
     *  危险项红系。全程 DrawContext 原语，零贴图零 shader。 */
    private void renderMenu(DrawContext ctx, int mouseX, int mouseY) {
        int th = menuTitle != null ? MENU_TITLE_H : 0;
        int h = menuLabels.size() * MENU_H + th;
        float ease = SciSkin.easeOut((net.minecraft.util.Util.getMeasuringTimeMs() - menuOpenMs) / 110f);
        if (menuHoverP.length != menuLabels.size()) menuHoverP = new float[menuLabels.size()];
        ctx.getMatrices().push(); // 弹入：以菜单左上角为锚 0.92→1.0
        // m316 整体抬 z=400（m202/m283 同病同刀）：画布节点的物品图标/升级角标由 drawItem 画在
        // z100~200 带深度测试，菜单 z0 平面填充画得再晚也被穿透（作者截图实锤：节点端口图标叠在
        // 菜单标题带上）。重命名/设置/帮助/选择器四浮层早已各自抬 400，唯菜单漏网，本笔补齐。
        ctx.getMatrices().translate(0, 0, 400);
        ctx.getMatrices().translate(menuX, menuY, 0);
        float sc = 0.92f + 0.08f * ease;
        ctx.getMatrices().scale(sc, sc, 1f);
        ctx.getMatrices().translate(-menuX, -menuY, 0);
        ctx.fill(menuX + 3, menuY + 4, menuX + MENU_W + 3, menuY + h + 4, SciSkin.withAlpha(0x66000000, ease));
        ctx.fill(menuX - 1, menuY - 1, menuX + MENU_W + 1, menuY + h + 1, SciSkin.withAlpha(NODEFRM, ease));
        ctx.fill(menuX, menuY, menuX + MENU_W, menuY + h, SciSkin.withAlpha(SciSkin.withAlpha8(SciSkin.CELL, 0xF0), ease)); // m207 归队
        if (th > 0) {
            ctx.fill(menuX, menuY, menuX + MENU_W, menuY + th, SciSkin.withAlpha(0xF00E2438, ease));
            ctx.fill(menuX, menuY + th - 1, menuX + MENU_W, menuY + th, SciSkin.withAlpha(CYAN, ease * 0.9f));
            String tt = menuTitle;
            while (tt.length() > 1 && this.textRenderer.getWidth(tt) > MENU_W - 12) tt = tt.substring(0, tt.length() - 1);
            ctx.drawText(this.textRenderer, tt, menuX + 6, menuY + 3,
                    SciSkin.withAlpha(SciSkin.TXT_HI, Math.max(0.3f, ease)), false);
        }
        int tick = SciSkin.lighten(NODEFRM); // 四角刻与卡片语言呼应
        ctx.fill(menuX, menuY, menuX + 4, menuY + 1, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX, menuY, menuX + 1, menuY + 4, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX + MENU_W - 4, menuY, menuX + MENU_W, menuY + 1, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX + MENU_W - 1, menuY, menuX + MENU_W, menuY + 4, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX, menuY + h - 1, menuX + 4, menuY + h, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX, menuY + h - 4, menuX + 1, menuY + h, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX + MENU_W - 4, menuY + h - 1, menuX + MENU_W, menuY + h, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX + MENU_W - 1, menuY + h - 4, menuX + MENU_W, menuY + h, SciSkin.withAlpha(tick, ease));
        for (int i = 0; i < menuLabels.size(); i++) {
            int y0 = menuY + th + i * MENU_H;
            boolean hov = mouseX >= menuX && mouseX < menuX + MENU_W && mouseY >= y0 && mouseY < y0 + MENU_H;
            menuHoverP[i] += ((hov ? 1f : 0f) - menuHoverP[i]) * 0.35f; // 指数趋近（~60fps 手感）
            float pv = menuHoverP[i];
            boolean danger = menuStyles.get(i) == 1;
            if (menuStyles.get(i) == 2)
                ctx.fill(menuX + 6, y0, menuX + MENU_W - 6, y0 + 1, SciSkin.withAlpha(SciSkin.withAlpha8(SciSkin.ACCENT, 0x55), ease)); // m207 归队
            if (pv > 0.02f)
                ctx.fill(menuX, y0 + 1, menuX + MENU_W, y0 + MENU_H - 1,
                        SciSkin.withAlpha(danger ? 0xFF3A1420 : SciSkin.HOVER, ease * pv));
            int barW = Math.round(3 * pv);
            if (barW > 0)
                ctx.fill(menuX, y0 + 1, menuX + barW, y0 + MENU_H - 1,
                        SciSkin.withAlpha(danger ? SciSkin.RED : CYAN, ease));
            int tx = menuX + 6 + Math.round(2 * pv);
            net.minecraft.util.Identifier tex = menuTexs.get(i); // m313 贴图图标优先
            if (tex != null) {
                ctx.drawTexture(tex, tx, y0 + 2, 16, 16, 0f, 0f, 32, 32, 32, 32);
                tx += 20; // m316：贴图满幅 16px，原 +16 零间隙文字贴脸（作者截图点名），补 4px 呼吸位
            }
            ItemStack ic = menuIcons.get(i);
            if (ic != null && !ic.isEmpty()) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(tx, y0 + 2, 0);
                ctx.getMatrices().scale(0.8f, 0.8f, 1f);
                ctx.drawItem(ic, 0, 0);
                ctx.getMatrices().pop();
                tx += 18; // m316：0.8×实占 12.8px，原 +16 只剩 3px，与贴图行对齐补到约 5px
            }
            int col = danger ? SciSkin.mix(SciSkin.RED_SOFT, SciSkin.RED, pv) : SciSkin.mix(TXT, SciSkin.TXT_MAX, pv);
            ctx.drawText(this.textRenderer, menuLabels.get(i), tx, y0 + 5,
                    SciSkin.withAlpha(col, Math.max(0.3f, ease)), false);
        }
        ctx.getMatrices().pop();
    }

    /** 断开某机器节点的全部连线（机器边 + 存储边，逐条 toggle）。 */
    private void clearLinksOfMachine(int idx) {
        StructureCoreBlockEntity be = be();
        BlockPos p = this.handler.blockPos();
        if (be == null || p == null) return;
        for (int[] c : new ArrayList<>(be.connections()))
            if (c[0] == idx || c[1] == idx) ClientPlayNetworking.send(new NodeLinkPayload(p, c[0], c[1]));
        List<String> dims = endDimsOf(be);
        List<long[]> ends = endsOf(be);
        for (long[] e : new ArrayList<>(be.storageEdgesView()))
            if (e[0] == idx) {
                int j = endpointIndex(ends, e[1]);
                String dim = j >= 0 && j < dims.size() ? dims.get(j) : "";
                ClientPlayNetworking.send(new StorageLinkPayload(p, (int) e[0], e[1], (int) e[2], dim));
            }
    }

    private void clearLinksOfStorage(long pl) {
        StructureCoreBlockEntity be = be();
        BlockPos p = this.handler.blockPos();
        if (be == null || p == null) return;
        List<String> dims = endDimsOf(be);
        List<long[]> ends = endsOf(be);
        int j = endpointIndex(ends, pl);
        String dim = j >= 0 && j < dims.size() ? dims.get(j) : "";
        for (long[] e : new ArrayList<>(be.storageEdgesView()))
            if (e[1] == pl) ClientPlayNetworking.send(new StorageLinkPayload(p, (int) e[0], e[1], (int) e[2], dim));
    }

    /** 整理布局：机器排网格，存储排右列。 */
    private void autoLayout() {
        StructureCoreBlockEntity be = be();
        BlockPos p = this.handler.blockPos();
        if (be == null || p == null) return;
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get(); // m221 间距收紧+进配置（作者截图点名"离得有点远"）：
        int rows = Math.max(1, c.canvasLayoutRows);                          // 旧 150/130 定值退役；步距=卡实占+间隙
        int stepX = NW + Math.max(0, c.canvasLayoutGapX);                    // 卡宽 100 + 默认间隙 30 = 130
        int stepY = NH + 28 + Math.max(0, c.canvasLayoutGapY);               // 卡高 52+28(升级格/徽章带，fitView 同口径) + 默认间隙 24 = 104
        for (int i = 0; i < be.nodes().size(); i++) // m149 竖排（用户点名照截图：单列往下码，满 rows 台换列）
            ClientPlayNetworking.send(new NodeMovePayload(p, i, 20 + (i / rows) * stepX, 20 + (i % rows) * stepY));
        List<long[]> ends = endsOf(be);
        for (int j = 0; j < ends.size(); j++) { // m265 只整理已放置的卡（右列竖排）；停靠卡不被强行拖下画布
            long pl = ends.get(j)[0];
            if (!endPlaced(be, pl)) continue;
            holdHome(pl, new int[]{760, 20 + j * 72});
            be.setStorageNodePos(pl, 760, 20 + j * 72);
            ClientPlayNetworking.send(new StorageNodeMovePayload(p, pl, 760, 20 + j * 72, false));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (helpOpen) { helpOpen = false; return true; } // m219 帮助卡：任意点即关（modal 吞穿透，m103 口径）
        if (settingsOpen) return settingsClick(mouseX, mouseY, button); // m199 设置面板 modal 最先判
        if (renameGid >= 0) { // m192 重命名小窗 modal：窗内点进字段，窗外点=关
            int rw = 200, rh = 58, rpx = (this.width - rw) / 2, rpy = (this.height - rh) / 2;
            if (renameField.mouseClicked(mouseX, mouseY, button)) return true;
            if (mouseX < rpx || mouseX > rpx + rw || mouseY < rpy || mouseY > rpy + rh) closeRename();
            return true;
        }
        // m93 总线大小滑块抓取
        int trxC = busTrackX();
        if (button == 0 && mouseX >= trxC - 3 && mouseX <= trxC + BUS_TRACK_W + 3 && mouseY >= 26 && mouseY <= 40) {
            busScaleDrag = true;
            busScaleFromMouse(mouseX);
            return true;
        }
        // m91 总线收起/展开开关
        int tbx = workRight() - 34;
        if (button == 0 && mouseX >= tbx && mouseX <= tbx + 22 && mouseY >= 26 && mouseY <= 40) {
            busCollapsed = !busCollapsed;
            return true;
        }
        // m88 机器库侧栏：点击行=放 1 台进画布；面板区吞掉其余点击
        if (libOpen && mouseX >= 8 && mouseX <= 168 && mouseY >= 24 && mouseY <= botTop() - 6) {
            if (button == 0) {
                List<ItemStack> lib = libItems();
                int ly = 24, lb = botTop() - 6, rowH = 20;
                int visible = Math.max(1, (lb - ly - 30) / rowH);
                int r = (int) ((mouseY - (ly + 16)) / rowH);
                if (mouseY >= ly + 16 && r >= 0 && r < visible && r + libScroll < lib.size()) {
                    BlockPos p = this.handler.blockPos();
                    if (p != null) ClientPlayNetworking.send(new com.sdzjz.net.NodeAddPayload(p,
                            Registries.ITEM.getId(lib.get(r + libScroll).getItem()).toString()));
                }
            }
            return true;
        }
        if (menuOpen) {
            int thM = menuTitle != null ? MENU_TITLE_H : 0; // m148 标题带占位
            int h = menuLabels.size() * MENU_H + thM;
            if (button == 0 && mouseX >= menuX && mouseX < menuX + MENU_W && mouseY >= menuY + thM && mouseY < menuY + h) {
                int idx = (int) ((mouseY - menuY - thM) / MENU_H);
                Runnable act = idx >= 0 && idx < menuActions.size() ? menuActions.get(idx) : null;
                clearMenu();
                if (act != null) {
                    MinecraftClient.getInstance().getSoundManager().play( // m148 点选音（原版按钮同款）
                            net.minecraft.client.sound.PositionedSoundInstance.master(
                                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    act.run();
                }
                return true;
            }
            clearMenu();
            return true;
        }
        if (pickerNode >= 0) {
            int px = (this.width - PICK_W) / 2, py = (this.height - PICK_H) / 2;
            if (pickerField.mouseClicked(mouseX, mouseY, button)) return true;
            if (pickerMode == 4) { // m131b 药水目标：形态按钮→重过滤；点药水→设目标关窗
                for (int f = 0; f < 3; f++) {
                    int fx = px + PICK_W - 118 + f * 38, fy = py + 5;
                    if (mouseX >= fx && mouseX < fx + 34 && mouseY >= fy && mouseY < fy + 14) {
                        if (brewForm != f) { brewForm = f; refilterPicker(); }
                        return true;
                    }
                }
                int pgx = px + 8, pgy = py + 44;
                for (int k = 0; k < potionFiltered.size(); k++) {
                    int cx = pgx + (k % PICK_COLS) * 21, cy = pgy + (k / PICK_COLS) * 21;
                    if (mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20) {
                        BlockPos bpp = this.handler.blockPos();
                        if (bpp != null && button == 0)
                            ClientPlayNetworking.send(new NodeTargetPayload(bpp, pickerNode, potionFilteredIds.get(k)));
                        closePicker();
                        return true;
                    }
                }
                if (mouseX < px || mouseX > px + PICK_W || mouseY < py || mouseY > py + PICK_H) closePicker();
                return true;
            }
            if (pickerMode == 6) { // m146 交易目标：点行=设目标关窗
                int rgx = px + 8, rgy = py + 44;
                for (int k = 0; k < tradeFilteredIds.size(); k++) {
                    int ry = rgy + k * ENCH_ROW_H;
                    if (mouseX >= rgx && mouseX < rgx + PICK_W - 16 && mouseY >= ry && mouseY < ry + ENCH_ROW_H - 2) {
                        BlockPos bpt = this.handler.blockPos();
                        if (bpt != null && button == 0)
                            ClientPlayNetworking.send(new NodeTargetPayload(bpt, pickerNode, tradeFilteredIds.get(k)));
                        closePicker();
                        return true;
                    }
                }
                if (mouseX < px || mouseX > px + PICK_W || mouseY < py || mouseY > py + PICK_H) closePicker();
                return true;
            }
            if (pickerMode == 5) { // m132 附魔目标：点行=设目标关窗
                int rgx = px + 8, rgy = py + 44;
                for (int k = 0; k < enchFilteredIds.size(); k++) {
                    int ry = rgy + k * ENCH_ROW_H;
                    if (mouseX >= rgx && mouseX < rgx + PICK_W - 16 && mouseY >= ry && mouseY < ry + ENCH_ROW_H - 2) {
                        BlockPos bpe = this.handler.blockPos();
                        if (bpe != null && button == 0)
                            ClientPlayNetworking.send(new NodeTargetPayload(bpe, pickerNode, enchFilteredIds.get(k)));
                        closePicker();
                        return true;
                    }
                }
                if (mouseX < px || mouseX > px + PICK_W || mouseY < py || mouseY > py + PICK_H) closePicker();
                return true;
            }
            int gx = px + 8, gy = py + 44;
            for (int k = 0; k < pickerFiltered.size(); k++) {
                int cx = gx + (k % PICK_COLS) * 21, cy = gy + (k / PICK_COLS) * 21;
                if (mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20) {
                    BlockPos bp = this.handler.blockPos();
                    String iid = Registries.ITEM.getId(pickerFiltered.get(k)).toString();
                    if (bp != null) {
                        if (pickerMode == 1) { // 过滤多选：切名单项，不关窗
                            ClientPlayNetworking.send(new NodeFilterPayload(bp, pickerNode, iid));
                            return true;
                        }
                        if (pickerMode == 2) { // 传感器：换监测物品，保留阈值/方向
                            StructureCoreBlockEntity be2 = be();
                            ItemStack ns = be2 != null && pickerNode < be2.nodes().size() ? be2.nodes().get(pickerNode) : ItemStack.EMPTY;
                            ClientPlayNetworking.send(new NodeSensorPayload(bp, pickerNode, iid,
                                    StructureCoreBlockEntity.sensorThreshold(ns), StructureCoreBlockEntity.sensorLess(ns)));
                            closePicker();
                            return true;
                        }
                        ClientPlayNetworking.send(new NodeTargetPayload(bp, pickerNode, iid));
                        if (pickerMode == 3) return true; // m93 多选作物：toggle 后不关面板，继续点选
                    }
                    closePicker();
                    return true;
                }
            }
            if (mouseX < px || mouseX > px + PICK_W || mouseY < py || mouseY > py + PICK_H) closePicker();
            return true;
        }
        // m110a 小地图：左键=跳转视角并开始拖拽；面板区吞掉其余点击
        if (inMap(mouseX, mouseY)) {
            if (button == 0) {
                StructureCoreBlockEntity beM = be();
                if (beM != null) { mapGeomDrag = mapGeom(beM); mapJump(mouseX, mouseY); mapDragging = true; }
            }
            return true;
        }
        if (mouseY > 34 && (button == 0 || button == 1)) {
            StructureCoreBlockEntity be = be();
            if (be != null) {
                List<ItemStack> nodes = be.nodes();
                List<long[]> ends = endsOf(be);
                double wx = wmx(mouseX), wy = wmy(mouseY);
                // 升级格：左键加、右键取
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    if (StructureCoreBlockEntity.isFilter(nodes.get(i)) || StructureCoreBlockEntity.isSensor(nodes.get(i))
                            || StructureCoreBlockEntity.isSwitch(nodes.get(i))
                            || StructureCoreBlockEntity.isDistributor(nodes.get(i))) continue;
                    int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                    for (int k = 0; k < 3; k++) {
                        int sx = nx + 4 + k * 32, sy = ny + NH + 4;
                        if (wx >= sx && wx <= sx + 24 && wy >= sy && wy <= sy + 18) {
                            BlockPos p = this.handler.blockPos();
                            // m115a：Shift+点击=批量（一次至多64个，服务端按背包/格内实况截断）
                            if (p != null) ClientPlayNetworking.send(new NodeUpgradePayload(p, i, k, button == 0, hasShiftDown() ? 64 : 1));
                            return true;
                        }
                    }
                }
                // m110b 标题栏齿轮：左键打开节点设置菜单（世界坐标，随缩放）
                if (button == 0) {
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx + NW - 26 && wx <= nx + NW - 13 && wy >= ny + 2 && wy <= ny + 15) {
                            openNodeMenu(i, (int) mouseX, (int) mouseY);
                            return true;
                        }
                    }
                }
                if (button == 1) {
                    // 停靠栏优先：右键端点节点 → 菜单（屏幕坐标；m265 停靠卡仍随总线收起跳过，放置卡永在）
                    for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0];
                        if (!busVisible() && !endPlaced(be, pl)) continue;
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (mouseX >= sx && mouseX <= sx + bw() && mouseY >= sy && mouseY <= sy + bh()) {
                            clearMenu();
                            menuTitle = "存储连线"; // m148
                            if (endPlaced(be, pl) && endsPlaceableOn()) { // m265 放置卡多一条：收回总线（拖回带内同义）
                                BlockPos pDock = this.handler.blockPos();
                                addMenu("收回总线", mi(net.minecraft.item.Items.ENDER_PEARL), () -> {
                                    if (pDock != null) {
                                        holdHome(pl, null);
                                        StructureCoreBlockEntity beD = be();
                                        if (beD != null) beD.dockStorageNode(pl); // 客户端 BE 顺手同写（后备读一致）
                                        ClientPlayNetworking.send(new StorageNodeMovePayload(pDock, pl, 0, 0, true));
                                    }
                                });
                            }
                            addMenu("断开全部连线", mt("disconnect_all"), 1, () -> clearLinksOfStorage(pl)); // m313 用户图标
                            addMenu("取消", () -> {});
                            openMenu((int) mouseX, (int) mouseY);
                            return true;
                        }
                    }
                    // 右键机器节点 → 菜单（m110b 与标题栏齿轮共用 openNodeMenu）
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                            openNodeMenu(i, (int) mouseX, (int) mouseY);
                            return true;
                        }
                    }
                    // m192 右键组标题带 → 组菜单（重命名/解散）；卡片先判所以盖在带上的卡不误触
                    if (groupsOn()) for (var ge : groupMembers(be).entrySet()) {
                        int[] r = groupRect(be, nodes, ge.getValue());
                        if (wx >= r[0] && wx <= r[2] && wy >= r[1] && wy <= r[1] + GBAND) {
                            openGroupMenu(ge.getKey(), (int) mouseX, (int) mouseY);
                            return true;
                        }
                    }
                    // 右键空白 → 画布菜单
                    clearMenu();
                    menuTitle = "画布"; // m148
                    if (groupsOn() && selected.size() >= 2) // m192 框选后从这里成组（另有 G 键快捷）
                        addMenu("打组所选(" + selected.size() + "台)", mi(net.minecraft.item.Items.LEAD), this::createGroupFromSelection);
                    if (groupsOn() && !selected.isEmpty())
                        addMenu("清除选择", mi(net.minecraft.item.Items.GLASS_PANE), selected::clear);
                    addMenu("整理布局", mi(net.minecraft.item.Items.COMPASS), this::autoLayout);
                    addMenu("重置视角", mi(net.minecraft.item.Items.SPYGLASS), () -> setViewInstant(0, 0, 1.0));
                    addMenu("取消", (ItemStack) null, 2, () -> {});
                    openMenu((int) mouseX, (int) mouseY);
                    return true;
                }
                if (button == 0) {
                    // 停靠栏优先（屏幕坐标）：供料口(绿) → 存储/面板→机器 供料连线；面板供料=取自它聚合的网络
                    for (int j = ends.size() - 1; j >= 0; j--) { // m265 放置卡收起态也可拉线
                        if (ends.get(j)[1] == 6) continue; // 输出接口无供料口
                        long pl = ends.get(j)[0];
                        if (!busVisible() && !endPlaced(be, pl)) continue;
                        int oxp = snx(be, pl, j) + bw() - 14, oyp = sny(be, pl, j) + bh();
                        if (Math.abs(mouseX - oxp) <= 12 && Math.abs(mouseY - oyp) <= 12) { // m122 抓取半径放宽
                            linking = true; linkStor = pl; linkFrom = -1; return true;
                        }
                    }
                    // 停靠栏节点体：起手拖拽（m265 拖出带=钉到画布/拖回带=收回停靠/微动=原地不动）——
                    // 旧版仅吞点击防误触其下机器与画布平移，语义完整保留（微动即等价旧吞）。
                    for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0];
                        if (!busVisible() && !endPlaced(be, pl)) continue;
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (mouseX >= sx - 4 && mouseX <= sx + bw() + 6 && mouseY >= sy && mouseY <= sy + bh()) {
                            if (endsPlaceableOn()) {
                                dragEnd = pl; dragEndWasPlaced = endPlaced(be, pl);
                                dragEndOffX = mouseX - sx; dragEndOffY = mouseY - sy;
                                dragEndX = sx; dragEndY = sy;
                                dragEndPressX = mouseX; dragEndPressY = mouseY;
                            }
                            return true; // 开关关=照旧只吞
                        }
                    }
                    // 开关节点：点按钮切换 开/关
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        if (!StructureCoreBlockEntity.isSwitch(nodes.get(i))) continue;
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx + 43 && wx <= nx + 91 && wy >= ny + 23 && wy <= ny + 45) {
                            BlockPos p = this.handler.blockPos();
                            if (p != null) ClientPlayNetworking.send(new NodeSwitchPayload(p, i));
                            return true;
                        }
                    }
                    // 传感器阈值 [−][+]：步进100，Shift=1000（m160 抽取节点感应行同用，几何各异）
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        boolean senH = StructureCoreBlockEntity.isSensor(nodes.get(i));
                        boolean extH = StructureCoreBlockEntity.isExtractor(nodes.get(i));
                        if (!senH && !extH) continue;
                        if (StructureCoreBlockEntity.sensorItem(nodes.get(i)).isEmpty()) continue;
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        int hit = 0;
                        if (senH && wx >= nx + 57 && wx <= nx + 71 && wy >= ny + 36 && wy <= ny + 49) hit = -1;
                        else if (senH && wx >= nx + 74 && wx <= nx + 88 && wy >= ny + 36 && wy <= ny + 49) hit = 1;
                        else if (extH && wx >= nx + 62 && wx <= nx + 76 && wy >= ny + 46 && wy <= ny + 59) hit = -1;
                        else if (extH && wx >= nx + 79 && wx <= nx + 93 && wy >= ny + 46 && wy <= ny + 59) hit = 1;
                        if (hit != 0) {
                            long step = hasShiftDown() ? 1000 : 100;
                            long th = Math.max(0, StructureCoreBlockEntity.sensorThreshold(nodes.get(i)) + hit * step);
                            BlockPos p = this.handler.blockPos();
                            if (p != null) ClientPlayNetworking.send(new NodeSensorPayload(p, i, "", th,
                                    StructureCoreBlockEntity.sensorLess(nodes.get(i))));
                            return true;
                        }
                    }
                    // 目标徽章：自动合成机=全物品选择器；全自动农场=作物选择器；酿造塔=药水选择器(m131b)
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        boolean auto = nodes.get(i).getItem() instanceof AutoCrafterItem;
                        boolean crop = nodes.get(i).getItem() instanceof com.sdzjz.item.CropFarmItem;
                        boolean brew = nodes.get(i).getItem() instanceof com.sdzjz.item.BrewingTowerItem;
                        boolean trade = nodes.get(i).getItem() instanceof com.sdzjz.item.VillagerTraderItem; // m146
                        if (!auto && !crop && !brew && !trade) continue;
                        int bx = wnx(be, nodes, i) + NW - 30, by = wny(be, nodes, i) + 14;
                        if (wx >= bx && wx <= bx + 20 && wy >= by && wy <= by + 20) {
                            if (crop) openCropPicker(i); else if (brew) openPotionPicker(i); else if (trade) openTradePicker(i); else openPicker(i);
                            return true;
                        }
                    }
                    // 机器输出口(绿) → 连线
                    double pR = Math.max(7, 10 / zoom); // m122 抓取半径随缩放反比——屏幕上恒 ~10px，低倍率下也点得中
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int oxp = wnx(be, nodes, i) + NW, oyp = wny(be, nodes, i) + NH / 2;
                        if (Math.abs(wx - oxp) <= pR && Math.abs(wy - oyp) <= pR) {
                            linking = true; linkFrom = i; linkStor = Long.MIN_VALUE; return true;
                        }
                    }
                    // 机器节点体 → 拖动
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                            if (groupsOn() && hasShiftDown()) { // m192 Shift+点卡=切换选中（升级格的Shift批量在前已判，不冲突）
                                if (!selected.remove(Integer.valueOf(i))) selected.add(i);
                                return true;
                            }
                            dragIndex = i; dragStor = Long.MIN_VALUE; dragOffX = wx - nx; dragOffY = wy - ny; dragCurX = nx; dragCurY = ny; return true; // m196 覆盖坐标起手定格
                        }
                    }
                    // m192 组框标题带：左键=整组拖动（成员坐标快照，拖动时快照+增量绝对写，松手一包发增量）
                    if (groupsOn()) for (var ge : groupMembers(be).entrySet()) {
                        int[] r = groupRect(be, nodes, ge.getValue());
                        if (wx >= r[0] && wx <= r[2] && wy >= r[1] && wy <= r[1] + GBAND) {
                            dragGid = ge.getKey(); dragGidWx = wx; dragGidWy = wy; dragGidDx = 0; dragGidDy = 0;
                            dragGidSnap.clear();
                            for (int i : ge.getValue()) if (i < nodes.size())
                                dragGidSnap.put(i, new int[]{wnx(be, nodes, i), wny(be, nodes, i)});
                            return true;
                        }
                    }
                    if (groupsOn() && hasShiftDown()) { // m192 Shift+拖空白=框选加选（普通左拖=平移，行为不变）
                        boxSelecting = true;
                        boxX0 = boxX1 = wx; boxY0 = boxY1 = wy;
                        return true;
                    }
                    selected.clear(); // m192 左键点空白=清选（不吞事件，随后拖动=平移照旧）
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (settingsOpen && settSlDrag >= 0) { settApplySlider(mouseX); return true; } // m223 设置面板 RGB 滑杆拖动（必须先于下方 settingsOpen 吞穿透）
        if (busScaleDrag) { busScaleFromMouse(mouseX); return true; } // m93 总线大小滑块
        if (mapDragging) { mapJump(mouseX, mouseY); return true; }    // m110a 小地图拖拽跳转
        if (pickerNode >= 0 || menuOpen || renameGid >= 0 || settingsOpen || helpOpen) return true; // m199 设置窗并入；m219 帮助卡并入
        if (linking) return true;
        if (button == 0 && dragEnd != Long.MIN_VALUE) { // m265 端点卡拖拽：本地覆盖坐标（m196 口径）
            dragEndX = (int) (mouseX - dragEndOffX);
            dragEndY = (int) (mouseY - dragEndOffY);
            return true;
        }
        if (button == 0 && dragIndex >= 0) {
            StructureCoreBlockEntity be = be();
            if (be != null && dragIndex < be.nodes().size()) {
                dragCurX = (int) (wmx(mouseX) - dragOffX); // m196 写覆盖坐标（渲染真源），BE 顺手同写但不依赖它
                dragCurY = (int) (wmy(mouseY) - dragOffY);
                be.setNodePos(dragIndex, dragCurX, dragCurY);
            }
            return true;
        }
        if (button == 0 && dragGid >= 0) { // m192 组拖动：每帧快照+增量绝对写，中途被全量同步覆盖下一帧自愈
            StructureCoreBlockEntity be = be();
            if (be != null) {
                dragGidDx = (int) (wmx(mouseX) - dragGidWx);
                dragGidDy = (int) (wmy(mouseY) - dragGidWy);
                for (var en : dragGidSnap.entrySet())
                    if (en.getKey() < be.nodes().size())
                        be.setNodePos(en.getKey(), en.getValue()[0] + dragGidDx, en.getValue()[1] + dragGidDy);
            }
            return true;
        }
        if (boxSelecting) { boxX1 = wmx(mouseX); boxY1 = wmy(mouseY); return true; } // m192 框选拉框
        if (button == 0 && mouseY > 34) {
            zoomAnim = false; zoomTarget = zoom; // m186 手动平移终止缩放动效防隔帧抢写
            panX += deltaX;
            panY += deltaY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (settSlDrag >= 0) { settSlDrag = -1; return true; } // m223 设置面板 RGB 滑杆收拖（拖动中 setText 只写实例，落盘由 closeSettings 兜底——m199 颜色框同口径）
        if (busScaleDrag) { // m215 松手一次性落盘（拖动中不写文件）
            com.sdzjz.config.SdzjzConfig.get().canvasBusScale = busScale;
            com.sdzjz.config.SdzjzConfig.save();
        }
        busScaleDrag = false; // m93
        if (mapDragging) { mapDragging = false; mapGeomDrag = null; } // m110a
        if (button == 0 && dragEnd != Long.MIN_VALUE) { // m265 端点卡收拖：拖出带=钉画布/拖回带=收回/微动=不动
            long pl = dragEnd;
            boolean moved = Math.abs(mouseX - dragEndPressX) >= 4 || Math.abs(mouseY - dragEndPressY) >= 4;
            int relX = dragEndX, relY = dragEndY;
            dragEnd = Long.MIN_VALUE; // 先清，snx/sny 回真源
            StructureCoreBlockEntity be = be();
            BlockPos p = this.handler.blockPos();
            if (be != null && p != null && moved && endsPlaceableOn()) {
                List<long[]> ends = endsOf(be);
                int bandBot = Math.min(busVisible()
                        ? busCardTop() + Math.max(1, (ends.size() + busCols() - 1) / busCols()) * busRowStep() + 2
                        : 44, botTop()); // m263 同式
                if (relY + bh() / 2 > bandBot) { // 卡心落带下=放置/移动（世界坐标=屏幕反投影，双端 ±1e6 钳）
                    int wx = Math.max(-1_000_000, Math.min(1_000_000, (int) Math.round((relX - panX) / zoom)));
                    int wy = Math.max(-1_000_000, Math.min(1_000_000, (int) Math.round((relY - panY) / zoom)));
                    holdHome(pl, new int[]{wx, wy});
                    be.setStorageNodePos(pl, wx, wy); // 客户端 BE 顺手同写（缓存未热身的后备读一致）
                    ClientPlayNetworking.send(new StorageNodeMovePayload(p, pl, wx, wy, false));
                } else if (dragEndWasPlaced) { // 拖回带内=收回停靠
                    holdHome(pl, null);
                    be.dockStorageNode(pl);
                    ClientPlayNetworking.send(new StorageNodeMovePayload(p, pl, 0, 0, true));
                }
            }
            return true;
        }
        if (button == 0 && linking) {
            StructureCoreBlockEntity be = be();
            BlockPos p = this.handler.blockPos();
            if (be != null && p != null) {
                List<ItemStack> nodes = be.nodes();
                List<long[]> ends = endsOf(be);
                List<String> dims = endDimsOf(be);
                double wx = wmx(mouseX), wy = wmy(mouseY);
                if (linkFrom >= 0) {
                    // 优先看是否落在存储节点上 → 机器→存储 定向产出
                    boolean done = false;
                    for (int j = ends.size() - 1; j >= 0; j--) { // m265 放置卡收起态也可作落点
                        long pl = ends.get(j)[0]; // 数据面板也可连（存进它聚合的整个网络）
                        if (!busVisible() && !endPlaced(be, pl)) continue;
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (mouseX >= sx - 6 && mouseX <= sx + bw() + 6 && mouseY >= sy - 4 && mouseY <= sy + bh() + 10) { // m122 覆盖下缘凸出的收料口
                            ClientPlayNetworking.send(new StorageLinkPayload(p, linkFrom, pl, 0, dims.get(j)));
                            done = true;
                            break;
                        }
                    }
                    if (!done) {
                        double pad = 6 / zoom; // m122 落点外扩（屏幕恒定 ~6px）
                        for (int i = nodes.size() - 1; i >= 0; i--) {
                            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                            if (wx >= nx - pad && wx <= nx + NW + pad && wy >= ny - pad && wy <= ny + NH + pad) {
                                if (i != linkFrom) ClientPlayNetworking.send(new NodeLinkPayload(p, linkFrom, i));
                                break;
                            }
                        }
                    }
                } else if (linkStor != Long.MIN_VALUE) {
                    // 存储→机器 定向供料
                    int j = endpointIndex(ends, linkStor);
                    String dim = j >= 0 && j < dims.size() ? dims.get(j) : "";
                    double pad2 = 6 / zoom; // m122 落点外扩
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx - pad2 && wx <= nx + NW + pad2 && wy >= ny - pad2 && wy <= ny + NH + pad2) {
                            ClientPlayNetworking.send(new StorageLinkPayload(p, i, linkStor, 1, dim));
                            break;
                        }
                    }
                }
            }
            linking = false; linkFrom = -1; linkStor = Long.MIN_VALUE;
            return true;
        }
        if (button == 0 && dragIndex >= 0) {
            StructureCoreBlockEntity be = be();
            BlockPos p = this.handler.blockPos();
            if (be != null && p != null && dragIndex < be.nodes().size()) {
                be.setNodePos(dragIndex, dragCurX, dragCurY); // m196 本地定格 + 发包用覆盖坐标——从 BE 回读会被中途同步盖成旧值(漂移元凶)
                ClientPlayNetworking.send(new NodeMovePayload(p, dragIndex, dragCurX, dragCurY));
            }
            dragIndex = -1;
            return true;
        }
        if (button == 0 && dragGid >= 0) { // m192 组拖动松手：一包发总增量（服务端批量改+单次同步，防N连发全量同步）
            BlockPos p = this.handler.blockPos();
            StructureCoreBlockEntity gbe = be(); // m196 覆盖失效前按快照+增量本地定格——否则松手到服务端回同步之间闪回旧位置
            if (gbe != null) for (var en : dragGidSnap.entrySet())
                if (en.getKey() < gbe.nodes().size())
                    gbe.setNodePos(en.getKey(), en.getValue()[0] + dragGidDx, en.getValue()[1] + dragGidDy);
            if (p != null && (dragGidDx != 0 || dragGidDy != 0))
                ClientPlayNetworking.send(new com.sdzjz.net.NodeGroupMovePayload(p, dragGid, dragGidDx, dragGidDy));
            dragGid = -1;
            dragGidSnap.clear();
            return true;
        }
        if (button == 0 && boxSelecting) { // m192 框选收口：矩形∩卡体=加选（Shift起手天然是加选语义）
            boxSelecting = false;
            StructureCoreBlockEntity be = be();
            if (be != null) {
                double bx1 = Math.min(boxX0, boxX1), by1 = Math.min(boxY0, boxY1);
                double bx2 = Math.max(boxX0, boxX1), by2 = Math.max(boxY0, boxY1);
                List<ItemStack> nodes = be.nodes();
                for (int i = 0; i < nodes.size(); i++) {
                    int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                    if (nx < bx2 && nx + NW > bx1 && ny < by2 && ny + NH > by1) selected.add(i);
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX; lastMouseY = mouseY; // m313 快捷键悬停命中缓存
        SciSkin.scopeCanvas(true); // m214 主题分家：本帧 term*() 全族改读画布 7 色（默认暗夜），finally 必关防漏染别屏
        try {
            super.render(ctx, mouseX, mouseY, delta);
            if (pickerNode >= 0) renderPicker(ctx, mouseX, mouseY, delta);
            if (menuOpen) renderMenu(ctx, mouseX, mouseY);
            if (renameGid >= 0) renderRename(ctx, mouseX, mouseY, delta); // m192 组重命名小窗压最上层
            if (settingsOpen) renderSettings(ctx, mouseX, mouseY, delta); // m199 设置面板压最上层（与重命名互斥，openSettings 已清场）
        if (helpOpen) renderHelp(ctx); // m219 帮助卡
        } finally {
            SciSkin.scopeCanvas(false);
        }
    }

    // ================= 自动合成机目标选择器 =================
    private void openPicker(int node) {
        pickerMode = 0;
        pickerNode = node;
        pickerOpenMs = net.minecraft.util.Util.getMeasuringTimeMs(); // m148 淡入
        if (craftables == null) buildCraftables();
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** m131b 药水目标选择器：单选，含形态切换（普通/喷溅/滞留）；延长/强化是独立药水项直接列出。 */
    private void openPotionPicker(int node) {
        pickerMode = 4;
        pickerNode = node;
        pickerOpenMs = net.minecraft.util.Util.getMeasuringTimeMs(); // m148 淡入
        if (potionIds == null) {
            potionIds = new ArrayList<>(Registries.POTION.getIds());
            potionIds.sort(java.util.Comparator.comparing(net.minecraft.util.Identifier::toString));
        }
        StructureCoreBlockEntity beP = be();
        if (beP != null && node >= 0 && node < beP.nodes().size()) { // 已有目标→回显形态
            String t = StructureCoreBlockEntity.craftTarget(beP.nodes().get(node));
            brewForm = t.endsWith("|s") ? 1 : t.endsWith("|l") ? 2 : 0;
        }
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** m132 附魔目标选择器：单选行式列表；每级独立成行（锋利I..V），等级降序、附魔按名排序。 */
    private void openEnchantPicker(int node) {
        pickerMode = 5;
        pickerNode = node;
        pickerOpenMs = net.minecraft.util.Util.getMeasuringTimeMs(); // m148 淡入
        enchAllIds = new ArrayList<>();
        enchAllNames = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            var reg = mc.world.getRegistryManager().getWrapperOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
            var es = reg.streamEntries().collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            es.sort(java.util.Comparator.comparing(e -> net.minecraft.enchantment.Enchantment.getName(e, 1).getString()));
            for (var e : es) {
                String id = e.registryKey().getValue().toString();
                for (int lv = e.value().getMaxLevel(); lv >= 1; lv--) {
                    enchAllIds.add(id + "|" + lv);
                    enchAllNames.add(net.minecraft.enchantment.Enchantment.getName(e, lv));
                }
            }
        }
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** m146 交易目标选择器：行式列表（职业：付出→获得），照附魔模式5样式。 */
    private void openTradePicker(int node) {
        pickerMode = 6;
        pickerNode = node;
        pickerOpenMs = net.minecraft.util.Util.getMeasuringTimeMs(); // m148 淡入
        tradeAllIds = com.sdzjz.machine.TradePlanner.allTargets();
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    private void refilterTrades() {
        tradeFiltered.clear();
        tradeFilteredIds.clear();
        tradeFilteredNames.clear();
        if (tradeAllIds == null) return;
        String q = pickerField.getText().trim().toLowerCase();
        for (String tgt : tradeAllIds) {
            net.minecraft.text.Text nm = com.sdzjz.machine.TradePlanner.displayName(tgt);
            if (!q.isEmpty() && !nm.getString().toLowerCase().contains(q) && !tgt.contains(q)) continue;
            ItemStack ic = com.sdzjz.machine.TradePlanner.iconStack(tgt);
            if (ic == null) continue;
            tradeFiltered.add(ic);
            tradeFilteredIds.add(tgt);
            tradeFilteredNames.add(nm);
            if (tradeFiltered.size() >= ENCH_ROWS) break;
        }
    }

    private void refilterEnchants() {
        enchFiltered.clear();
        enchFilteredIds.clear();
        enchFilteredNames.clear();
        if (enchAllIds == null) return;
        String q = pickerField.getText().trim().toLowerCase();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        for (int k = 0; k < enchAllIds.size(); k++) {
            String tgt = enchAllIds.get(k);
            net.minecraft.text.Text nm = enchAllNames.get(k);
            if (!q.isEmpty() && !nm.getString().toLowerCase().contains(q) && !tgt.contains(q)) continue;
            ItemStack bs = com.sdzjz.machine.EnchantPlanner.targetStack(mc.world, tgt);
            if (bs == null) continue;
            enchFiltered.add(bs);
            enchFilteredIds.add(tgt);
            enchFilteredNames.add(nm);
            if (enchFiltered.size() >= ENCH_ROWS) break;
        }
    }

    private static char brewFormChar(int f) { return f == 1 ? 's' : f == 2 ? 'l' : 'p'; }
    private static final String[] BREW_FORM_NAMES = {"普通", "喷溅", "滞留"};

    private void refilterPotions() {
        potionFiltered.clear();
        potionFilteredIds.clear();
        String q = pickerField.getText().trim().toLowerCase();
        for (net.minecraft.util.Identifier pid : potionIds) {
            if (brewForm == 0 && pid.getPath().equals("water")) continue; // 普通水瓶无酿造意义（喷溅水=水+火药可酿，保留）
            String tgt = pid + "|" + brewFormChar(brewForm);
            ItemStack ps = com.sdzjz.machine.BrewPlanner.targetStack(tgt);
            if (ps == null) continue;
            if (q.isEmpty() || ps.getName().getString().toLowerCase().contains(q) || pid.getPath().contains(q)) {
                potionFiltered.add(ps);
                potionFilteredIds.add(tgt);
                if (potionFiltered.size() >= PICK_COLS * PICK_ROWS) break;
            }
        }
    }

    /** 过滤名单多选：点选=加/移，不关窗。 */
    /** m149 机器加工二级界面：复用模式1多选选择器全套（同 fl 名单/同 NodeFilterPayload/
     *  已选置顶/Esc 完成），只换候选源与窗题。熔炉=全部可熔炼输入；多产物机=自家掉落表。 */
    private void openMachineFilterPicker(int node) {
        StructureCoreBlockEntity beM = be();
        if (beM == null || node < 0 || node >= beM.nodes().size()) return;
        ItemStack stM = beM.nodes().get(node);
        if (!(stM.getItem() instanceof com.sdzjz.item.MachineItem mif)) return;
        List<Item> src = new ArrayList<>();
        LinkedHashSet<Item> set = new LinkedHashSet<>();
        if (com.sdzjz.machine.Machines.smelterFamily(mif.def().id())) { // m173 熔炉族
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null)
                for (var e : mc.world.getRecipeManager().listAllOfType(net.minecraft.recipe.RecipeType.SMELTING))
                    for (var ing : e.value().getIngredients())
                        for (ItemStack ms : ing.getMatchingStacks()) set.add(ms.getItem());
            // m283 候选=仓库现有可烧（作者点名"读取存储终端里有什么可以烧的，不是全选出来"）——
            // m163b 同款刀法：busIds 通道(m85/m163b,含精确条目,零新协议)∩可熔炼输入集，按仓库存量序排列
            // （busTopIds 本就多者在前=常烧的矿在最前）。端点没同步到（列表空）回退全表不堵人；
            // 仓里确实没可烧的=空网格+标题说明；旧名单里已烧空的项照样走 m116 已选置顶可移除。
            java.util.List<String> busM = busIdsOf(beM);
            if (!busM.isEmpty()) {
                LinkedHashSet<Item> inStore = new LinkedHashSet<>();
                for (String id : busM) {
                    Item it = Registries.ITEM.get(net.minecraft.util.Identifier.of(id));
                    if (it != net.minecraft.item.Items.AIR && set.contains(it)) inStore.add(it);
                }
                set = inStore;
                pickerTitleOverride = "选择烧什么（候选=仓库可烧·空=全烧·Esc完成）";
            } else {
                pickerTitleOverride = "选择烧什么（空=全烧·点选=加/移·Esc完成）";
            }
        } else {
            for (var d : mif.def().outputs())
                set.add(Registries.ITEM.get(net.minecraft.util.Identifier.of(d.item())));
            pickerTitleOverride = "选择产物（空=全出·点选=加/移·Esc完成）";
        }
        src.addAll(set);
        pickerSrcOverride = src;
        pickerMode = 1;
        pickerNode = node;
        pickerOpenMs = net.minecraft.util.Util.getMeasuringTimeMs();
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    private void openFilterPicker(int node) {
        pickerMode = 1;
        pickerNode = node;
        pickerOpenMs = net.minecraft.util.Util.getMeasuringTimeMs(); // m148 淡入
        if (allItems == null) buildAllItems();
        // m163b 抽取白名单候选=仓库现有（用户点名：别给我列全物品表）——复用 m149 的 pickerSrcOverride
        // 机制 + m85/m163b 总线库存同步通道（busIdsCache，含精确条目），零新协议。已选置顶逻辑按 id
        // 独立解析，仓里已抽空的旧名单项照样置顶可移除。端点还没同步到（列表空）回退全物品表不堵人。
        StructureCoreBlockEntity beF = be();
        if (beF != null && node >= 0 && node < beF.nodes().size()
                && StructureCoreBlockEntity.isExtractor(beF.nodes().get(node))) {
            List<Item> src = new ArrayList<>();
            for (String id : busIdsOf(beF)) {
                Item it = Registries.ITEM.get(net.minecraft.util.Identifier.of(id));
                if (it != net.minecraft.item.Items.AIR && !src.contains(it)) src.add(it);
            }
            if (!src.isEmpty()) {
                pickerSrcOverride = src;
                pickerTitleOverride = "抽取白名单（候选=仓库现有·点选=加/移·Esc完成）";
            }
        }
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** 传感器监测物品单选。 */
    private void openSensorPicker(int node) {
        pickerMode = 2;
        pickerNode = node;
        pickerOpenMs = net.minecraft.util.Util.getMeasuringTimeMs(); // m148 淡入
        if (allItems == null) buildAllItems();
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** 作物选择（全自动农场，固定 9 种）。 */
    private List<Item> cropItems;

    private void openCropPicker(int node) {
        pickerMode = 3;
        pickerNode = node;
        pickerOpenMs = net.minecraft.util.Util.getMeasuringTimeMs(); // m148 淡入
        if (cropItems == null) {
            cropItems = new ArrayList<>();
            for (String id : com.sdzjz.machine.CropFarms.KEYS)
                cropItems.add(Registries.ITEM.get(Identifier.of(id)));
        }
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** 全物品表（过滤/传感器可选任意物品，不限可合成）。 */
    private void buildAllItems() {
        allItems = new ArrayList<>();
        for (Item it : Registries.ITEM) {
            if (it != net.minecraft.item.Items.AIR) allItems.add(it);
        }
    }

    private void closePicker() {
        pickerNode = -1;
        pickerSrcOverride = null;  // m149
        pickerTitleOverride = null;
        pickerField.setFocused(false);
        this.setFocused(null);
    }

    private void buildCraftables() {
        craftables = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        LinkedHashSet<Item> set = new LinkedHashSet<>();
        for (RecipeEntry<CraftingRecipe> e : mc.world.getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
            try {
                ItemStack out = e.value().getResult(mc.world.getRegistryManager());
                if (out != null && !out.isEmpty()) set.add(out.getItem());
            } catch (Exception ignored) {}
        }
        craftables.addAll(set);
    }

    private void refilterPicker() {
        pickerFiltered.clear();
        if (pickerMode == 4) { refilterPotions(); return; } // m131b 药水目标走独立表
        if (pickerMode == 5) { refilterEnchants(); return; } // m132 附魔目标走独立表
        if (pickerMode == 6) { refilterTrades(); return; } // m146 交易目标走独立表
        List<Item> src = pickerSrcOverride != null ? pickerSrcOverride // m149 机器加工过滤候选源
                : pickerMode == 0 ? craftables : pickerMode == 3 ? cropItems : allItems;
        if (src == null) return;
        String q = pickerField.getText().trim().toLowerCase();
        // m116：已选项置顶——窗口只显示一页 70 格，1400+ 物品里已选的经常根本翻不到（用户点名）。
        // 按 id 直接解析已选（数量个位数级），不进大扫描，每键成本不回退 m107a。
        if (pickerMode == 1 || pickerMode == 3) {
            StructureCoreBlockEntity beS = be();
            if (beS != null && pickerNode >= 0 && pickerNode < beS.nodes().size()) {
                List<String> sel = pickerMode == 1
                        ? StructureCoreBlockEntity.filterList(beS.nodes().get(pickerNode))
                        : StructureCoreBlockEntity.cropList(beS.nodes().get(pickerNode));
                for (String sid : sel) {
                    Item it = Registries.ITEM.get(net.minecraft.util.Identifier.of(sid));
                    if (q.isEmpty()
                            || new ItemStack(it).getName().getString().toLowerCase().contains(q)
                            || Registries.ITEM.getId(it).getPath().contains(q)) {
                        if (!pickerFiltered.contains(it)) pickerFiltered.add(it);
                    }
                }
            }
        }
        for (Item it : src) {
            if (pickerFiltered.contains(it)) continue; // 已置顶的不重复
            if (q.isEmpty()
                    || new ItemStack(it).getName().getString().toLowerCase().contains(q)
                    || Registries.ITEM.getId(it).getPath().contains(q)) {
                pickerFiltered.add(it);
                if (pickerFiltered.size() >= PICK_COLS * PICK_ROWS) break;
            }
        }
    }

    private void renderPicker(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // m283 整体抬 z=400（m202 同病同刀）：画布节点物品/数量角标画在 z100~200 带深度测试，
        // 选择器 z0 后画的填充会被剔除=底下机器图标穿透面板（作者点名"面板会透出来层级"）。
        // 面板内 drawItem 自带 +150 落在 ~550，仍低于原版 tooltip 常规带之上无冲突；命中判定全用屏幕坐标不受影响。
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);
        int px = (this.width - PICK_W) / 2, py = (this.height - PICK_H) / 2;
        float easeP = SciSkin.easeOut((net.minecraft.util.Util.getMeasuringTimeMs() - pickerOpenMs) / 130f); // m148 淡入
        ctx.fill(0, 0, this.width, this.height, SciSkin.withAlpha(0xA0000000, 0.35f + 0.65f * easeP));
        ctx.fill(px + 3, py + 4, px + PICK_W + 3, py + PICK_H + 4, SciSkin.withAlpha(0x66000000, easeP)); // 投影
        ctx.fill(px - 1, py - 1, px + PICK_W + 1, py + PICK_H + 1, NODEFRM);
        ctx.fill(px, py, px + PICK_W, py + PICK_H, SciSkin.withAlpha8(SciSkin.CELL, 0xF0)); // m207 归队
        ctx.fill(px, py, px + PICK_W, py + 3, SciSkin.withAlpha(CYAN, 0.5f + 0.5f * easeP));
        int tickP = SciSkin.lighten(NODEFRM); // m148 下沿角刻与卡片语言呼应
        ctx.fill(px, py + PICK_H - 1, px + 4, py + PICK_H, tickP);
        ctx.fill(px, py + PICK_H - 4, px + 1, py + PICK_H, tickP);
        ctx.fill(px + PICK_W - 4, py + PICK_H - 1, px + PICK_W, py + PICK_H, tickP);
        ctx.fill(px + PICK_W - 1, py + PICK_H - 4, px + PICK_W, py + PICK_H, tickP);
        String ptitle = pickerTitleOverride != null ? pickerTitleOverride // m149
                : pickerMode == 1 ? "配置过滤名单（点选=加/移·可多选·Esc完成）"
                : pickerMode == 2 ? "选择监测物品（中文/英文搜索）"
                : pickerMode == 3 ? "选择种植作物（可多选≤8，再点=取消）"
                : pickerMode == 4 ? "选择目标药水"
                : pickerMode == 5 ? "选择目标附魔（中文/英文搜索）"
                : pickerMode == 6 ? "选择交易条目（职业：付出→获得·可搜索）"
                : "选择目标产物（中文/英文搜索）";
        ctx.drawText(this.textRenderer, ptitle, px + 8, py + 8, TXT, false);
        if (pickerMode == 4) { // m131b 形态切换按钮
            for (int f = 0; f < 3; f++) {
                int fx = px + PICK_W - 118 + f * 38, fy = py + 5;
                boolean on = brewForm == f;
                boolean hovF = mouseX >= fx && mouseX < fx + 34 && mouseY >= fy && mouseY < fy + 14;
                ctx.fill(fx - 1, fy - 1, fx + 35, fy + 15, NODEFRM);
                ctx.fill(fx, fy, fx + 34, fy + 14, on ? SciSkin.ON_DARK : hovF ? SciSkin.HOVER : SciSkin.BTN_FACE);
                ctx.drawText(this.textRenderer, BREW_FORM_NAMES[f], fx + 5, fy + 3, on ? ON : SUB, false);
            }
        }
        List<String> selIds = java.util.Collections.emptyList();
        if (pickerMode == 1 || pickerMode == 3) { // m93：作物多选沿用白名单的已选高亮
            StructureCoreBlockEntity be3 = be();
            if (be3 != null && pickerNode >= 0 && pickerNode < be3.nodes().size())
                selIds = pickerMode == 1
                        ? StructureCoreBlockEntity.filterList(be3.nodes().get(pickerNode))
                        : StructureCoreBlockEntity.cropList(be3.nodes().get(pickerNode));
        }
        pickerField.setX(px + 8);
        pickerField.setY(py + 22);
        pickerField.render(ctx, mouseX, mouseY, delta);
        int gx = px + 8, gy = py + 44;
        Item hovered = null;
        ItemStack hoveredStack = null;
        String hoverName = null; // m132 模式5行悬停名（附魔书 getName 恒为"附魔书"，提示行用附魔名）
        if (pickerMode == 6) { // m146 交易行式列表：产出图标+「职业：付出→获得」，当前目标=绿框
            String curT = "";
            StructureCoreBlockEntity beT = be();
            if (beT != null && pickerNode >= 0 && pickerNode < beT.nodes().size())
                curT = StructureCoreBlockEntity.craftTarget(beT.nodes().get(pickerNode));
            for (int k = 0; k < tradeFiltered.size(); k++) {
                int ry = gy + k * ENCH_ROW_H;
                boolean hov = mouseX >= gx && mouseX < gx + PICK_W - 16 && mouseY >= ry && mouseY < ry + ENCH_ROW_H - 2;
                boolean sel = tradeFilteredIds.get(k).equals(curT);
                if (sel) ctx.fill(gx - 1, ry - 1, gx + PICK_W - 15, ry + ENCH_ROW_H - 1, ON);
                ctx.fill(gx, ry, gx + PICK_W - 16, ry + ENCH_ROW_H - 2, hov ? SciSkin.HOVER : sel ? SciSkin.ON_DARK : SciSkin.BTN_FACE);
                ctx.drawItem(tradeFiltered.get(k), gx + 1, ry);
                ctx.drawText(this.textRenderer, tradeFilteredNames.get(k), gx + 22, ry + 4, TXT, false);
                if (hov) { hoveredStack = tradeFiltered.get(k); hoverName = tradeFilteredNames.get(k).getString(); }
            }
            if (tradeFiltered.isEmpty())
                ctx.drawText(this.textRenderer, "无匹配交易（试试物品名或职业id）", gx, gy + 4, SUB, false);
        } else if (pickerMode == 5) { // m132 附魔行式列表：图标+原版名字（罗马数字/诅咒红字），当前目标=绿框
            String curT = "";
            StructureCoreBlockEntity beE = be();
            if (beE != null && pickerNode >= 0 && pickerNode < beE.nodes().size())
                curT = StructureCoreBlockEntity.craftTarget(beE.nodes().get(pickerNode));
            for (int k = 0; k < enchFiltered.size(); k++) {
                int ry = gy + k * ENCH_ROW_H;
                boolean hov = mouseX >= gx && mouseX < gx + PICK_W - 16 && mouseY >= ry && mouseY < ry + ENCH_ROW_H - 2;
                boolean sel = enchFilteredIds.get(k).equals(curT);
                if (sel) ctx.fill(gx - 1, ry - 1, gx + PICK_W - 15, ry + ENCH_ROW_H - 1, ON);
                ctx.fill(gx, ry, gx + PICK_W - 16, ry + ENCH_ROW_H - 2, hov ? SciSkin.HOVER : sel ? SciSkin.ON_DARK : SciSkin.BTN_FACE);
                ctx.drawItem(enchFiltered.get(k), gx + 1, ry);
                ctx.drawText(this.textRenderer, enchFilteredNames.get(k), gx + 22, ry + 4, TXT, false);
                if (hov) { hoveredStack = enchFiltered.get(k); hoverName = enchFilteredNames.get(k).getString(); }
            }
            if (enchFiltered.isEmpty())
                ctx.drawText(this.textRenderer, "无匹配附魔（试试中文名或英文id）", gx, gy + 4, SUB, false);
        } else if (pickerMode == 4) { // m131b 药水网格：栈直绘（带药水配色），当前目标=绿框
            String curT = "";
            StructureCoreBlockEntity beC = be();
            if (beC != null && pickerNode >= 0 && pickerNode < beC.nodes().size())
                curT = StructureCoreBlockEntity.craftTarget(beC.nodes().get(pickerNode));
            for (int k = 0; k < potionFiltered.size(); k++) {
                int cx = gx + (k % PICK_COLS) * 21, cy = gy + (k / PICK_COLS) * 21;
                boolean hov = mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20;
                boolean sel = potionFilteredIds.get(k).equals(curT);
                if (sel) ctx.fill(cx - 1, cy - 1, cx + 21, cy + 21, ON);
                ctx.fill(cx, cy, cx + 20, cy + 20, hov ? SciSkin.HOVER : sel ? SciSkin.ON_DARK : SciSkin.BTN_FACE);
                ctx.drawItem(potionFiltered.get(k), cx + 2, cy + 2);
                if (hov) hoveredStack = potionFiltered.get(k);
            }
        } else {
            for (int k = 0; k < pickerFiltered.size(); k++) {
                int cx = gx + (k % PICK_COLS) * 21, cy = gy + (k / PICK_COLS) * 21;
                boolean hov = mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20;
                boolean sel = (pickerMode == 1 || pickerMode == 3) && selIds.contains(Registries.ITEM.getId(pickerFiltered.get(k)).toString());
                if (sel) ctx.fill(cx - 1, cy - 1, cx + 21, cy + 21, ON); // 多选已选=绿框
                ctx.fill(cx, cy, cx + 20, cy + 20, hov ? SciSkin.HOVER : sel ? SciSkin.ON_DARK : SciSkin.BTN_FACE);
                ctx.drawItem(new ItemStack(pickerFiltered.get(k)), cx + 2, cy + 2);
                if (hov) hovered = pickerFiltered.get(k);
            }
        }
        String tip = hoverName != null ? hoverName
                : hoveredStack != null ? hoveredStack.getName().getString()
                : hovered != null ? new ItemStack(hovered).getName().getString() : "点击图标设为目标 · Esc 关闭";
        ctx.drawText(this.textRenderer, tip, px + 8, py + PICK_H - 14, (hovered != null || hoveredStack != null) ? ON : SUB, false);
        ctx.getMatrices().pop(); // m283 与顶部 translate(0,0,400) 配对
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (helpOpen) { if (keyCode == 256) helpOpen = false; return true; } // m219 帮助卡：Esc=关，其余吞（modal 同口径）
        if (settingsOpen) { // m199 设置窗：Esc=关；其余喂两个颜色框（未聚焦的 TextFieldWidget 自不吃）
            if (keyCode == 256) { closeSettings(); return true; }
            wireOutField.keyPressed(keyCode, scanCode, modifiers);
            wireInField.keyPressed(keyCode, scanCode, modifiers);
            bgField.keyPressed(keyCode, scanCode, modifiers);      // m217
            gridColField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (renameGid >= 0) { // m192 重命名窗：回车确认 / Esc取消，其余进输入框
            if (keyCode == 257 || keyCode == 335) { confirmRename(); return true; }
            if (keyCode == 256) { closeRename(); return true; }
            renameField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (menuOpen && keyCode == 256) { clearMenu(); return true; }
        if (pickerNode >= 0) {
            if (keyCode == 256) { closePicker(); return true; }
            pickerField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (!menuOpen) { // m313 画布快捷键（作者点名"要支持快捷键"）：悬停节点即生效；各 modal 已在上方截前
            int hv = hoveredNode();
            BlockPos kp = this.handler.blockPos();
            if (keyCode == 71 && hasShiftDown() && hv >= 0 && kp != null) { // Shift+G=解散悬停节点所属组（解散纯视觉，先于普通 G）
                int gid = groupOfNode(hv);
                if (gid >= 0) {
                    ClientPlayNetworking.send(new com.sdzjz.net.NodeGroupPayload(kp, gid, "", java.util.List.of()));
                    return true;
                }
            }
            if (hv >= 0 && kp != null) {
                switch (keyCode) {
                    case 80 -> { ClientPlayNetworking.send(new com.sdzjz.net.NodePausePayload(kp, hv)); return true; } // P=暂停/恢复
                    case 88 -> { clearLinksOfMachine(hv); return true; }                                                // X=断开全部连线
                    case 261 -> { ClientPlayNetworking.send(new NodeRemovePayload(kp, hv)); return true; }              // Del=取出机器
                    case 86 -> { openPrimaryPicker(hv); return true; }                                                  // V=选择(产物/烧什么/目标…)
                    case 291 -> { int gid = groupOfNode(hv); if (gid >= 0) { openRename(gid); return true; } }          // F2=重命名所属组
                }
            }
        }
        if (keyCode == 71 && groupsOn() && selected.size() >= 2 && !menuOpen) { // m192 G=打组所选（无输入框聚焦时才到这）
            createGroupFromSelection();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (settingsOpen) { wireOutField.charTyped(chr, modifiers); wireInField.charTyped(chr, modifiers); bgField.charTyped(chr, modifiers); gridColField.charTyped(chr, modifiers); return true; } // m199/m217
        if (renameGid >= 0) return renameField.charTyped(chr, modifiers); // m192
        if (pickerNode >= 0) return pickerField.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

}
