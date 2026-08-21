package com.sdzjz.client;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.node.NodeTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.Util;
import net.minecraft.world.phys.Vec3;

/**
 * m384 选区高亮器 / m393 双源重做（作者三点名之二三："东西放在核心里也显示边框""手持都看不见边框"）——
 * 世界内浮现能量框罩住整个 w×w 选区（全高线框+呼吸脉动+分块格线+穿墙透视线）。两个源：
 * ①**工作态**（青绿）=附近核心画布里已绑定未清完的移除器节点，作者点名"放进核心也要有框"；
 * ②**瞄准态**（紫）=手持已绑定的移除器。同一区域两源都有时工作态优先，不重画。
 * 纯客户端零协议：节点栈在 m275 渲染子集（machineNodes）里，随区块初始包就到客户端；
 * 手持则直读手上 NBT（m180 铁律直连 NodeTags）。
 *
 * <p><b>m393 根因（m384 起从未真的画出来过）</b>：原挂 WorldRenderEvents.AFTER_TRANSLUCENT——
 * fabric 官方 javadoc 白纸黑字：{@code WorldRenderContext#consumers()} 在 BEFORE_ENTITIES 之前
 * 与 BEFORE_DEBUG_RENDER 之后**恒为 null**（缓冲区那时已全部画完），该阶段的渲染必须直接写帧缓冲。
 * 于是每帧都走 consumers==null 早退，m385 的诊断日志其实一直在报"首次被闸：consumers=null"，
 * 只是没等到日志回传。改挂 **AFTER_ENTITIES**（该阶段 matrixStack 与 consumers 双双非 null，
 * 且是官方推荐的"往实体缓冲追加方块相关面片"的位置），线条随原版 LINES 层一并冲出。
 *
 * <p>drawBox=LevelRenderer.method_22980（1.21.1 尚未搬 VertexRendering，yarn 编译级）；分块格线用
 * 退化盒（x1==x2 塌成矩形框）省手搓顶点；透视线走 RenderType.getDebugLineStrip（原版 F3+G 区块
 * 边界同款，穿墙可见），strip 是连续笔画故**每源画完即冲**，否则两个区域会被连成一条飞线。
 * 开关随 chunkFxEnabled，另有 chunkHighlightRunning/Xray/ScanChunks 三键（m393）。
 */
public final class ChunkRegionHighlighter {

    private ChunkRegionHighlighter() {}

    /** m385 远程诊断（m320b 四态刀法瘦身版）：首次真绘/首次被闸各出声一次，
     *  日志搜 ChunkRegionHighlighter 一行定位：有"已绘制"无框=渲染层问题；只有"被闸"=数据/开关问题。 */
    private static boolean loggedDraw = false, loggedGate = false;

    /** m393 工作态源缓存：附近核心里的移除器节点选区 {cx, cz, r}，每 500ms 重扫一次（扫描=遍历
     *  周边已加载分块的方块实体表，不建票不强载；客户端线程独占，无并发）。 */
    private static final java.util.ArrayList<int[]> WORKING = new java.util.ArrayList<>();
    private static long nextScanMs = 0L;
    private static String scanDim = "";

    private static void gateLog(String why) {
        if (loggedGate) return;
        loggedGate = true;
        com.sdzjz.Sdzjz.LOGGER.info("[ChunkRegionHighlighter] 首次被闸：" + why);
    }

    public static void register() {
        // m393：AFTER_TRANSLUCENT → AFTER_ENTITIES（原阶段 consumers 恒 null=画了个寂寞，见类注释根因）
        // m405：改经 ClientHooks 世界渲染口挂载（三样原版东西直接进参，阶段选择与判空留在口里）
        ClientHooks.onWorldDrawAfterEntities(ChunkRegionHighlighter::render);
    }

    private static void render(PoseStack ms, MultiBufferSource consumers, Vec3 cam) {
        SdzjzConfig cfg = SdzjzConfig.get();
        if (!cfg.chunkFxEnabled) { gateLog("chunkFxEnabled=false"); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        String dim = mc.level.dimension().location().toString();
        double y1 = mc.level.getMinBuildHeight(), y2 = mc.level.getMaxBuildHeight();
        ms.pushPose();
        ms.translate(-cam.x, -cam.y, -cam.z); // 顶点必须相对相机（fabric 原文 IMPORTANT 条）
        float pulse = 0.55f + 0.35f * (float) Math.sin(Util.getMillis() / 300.0);
        java.util.HashSet<Long> seen = new java.util.HashSet<>(8); // 同区域去重（键=中心分块，工作态先画即占位）
        boolean any = false;

        // ① 工作态：附近核心里已绑定未清完的移除器（作者点名"放进核心也显示边框"）
        if (cfg.chunkHighlightRunning) {
            long now = Util.getMillis();
            if (now >= nextScanMs || !dim.equals(scanDim)) { nextScanMs = now + 500L; scanDim = dim; rescan(mc, dim, cfg); }
            for (int k = 0; k < WORKING.size(); k++) {
                int[] a = WORKING.get(k);
                if (!seen.add(key(a[0], a[1]))) continue;
                drawRegion(ms, consumers, cfg, a[0], a[1], a[2], y1, y2, 0.25f, 1.00f, 0.80f, pulse); // 青绿=激光工作中
                any = true;
            }
        }

        // ② 瞄准态：手持已绑定的移除器
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof com.sdzjz.item.ChunkRemoverItem)) held = mc.player.getOffhandItem();
        if (held.getItem() instanceof com.sdzjz.item.ChunkRemoverItem
                && NodeTags.chunkBound(held)
                && dim.equals(NodeTags.chunkDim(held))) {
            int cx = NodeTags.chunkX(held), cz = NodeTags.chunkZ(held);
            int r = Math.max(0, Math.min(NodeTags.chunkRadius(held), Math.max(0, cfg.chunkRemoverMaxRadius)));
            if (seen.add(key(cx, cz))) {
                drawRegion(ms, consumers, cfg, cx, cz, r, y1, y2, 0.72f, 0.35f, 1.00f, pulse); // 紫=瞄准中
                any = true;
            }
        }
        ms.popPose();

        if (any && !loggedDraw) {
            loggedDraw = true;
            com.sdzjz.Sdzjz.LOGGER.info("[ChunkRegionHighlighter] 已绘制（m393 挂 AFTER_ENTITIES）：工作态源 "
                    + WORKING.size() + " 个；若游戏内仍无框=渲染层问题请报画质档位");
        }
    }

    /** 区域键=中心分块（同一台机器手持态与工作态共用一个键，工作态先画即占位不重画）。 */
    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    /** 一个选区：主框（双画内缩伪加粗）+ r>0 分块格线 + 穿墙透视线。 */
    private static void drawRegion(PoseStack ms, MultiBufferSource consumers, SdzjzConfig cfg,
                                   int cx, int cz, int r, double y1, double y2,
                                   float cr, float cg, float cb, float pulse) {
        int w = 2 * r + 1;
        double x1 = (double) ((cx - r) << 4), z1 = (double) ((cz - r) << 4);
        double x2 = x1 + w * 16.0, z2 = z1 + w * 16.0;
        VertexConsumer vc = consumers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(ms, vc, x1, y1, z1, x2, y2, z2, cr, cg, cb, pulse);
        LevelRenderer.renderLineBox(ms, vc, x1 + 0.06, y1, z1 + 0.06, x2 - 0.06, y2, z2 - 0.06,
                Math.min(1.0f, cr + 0.13f), Math.min(1.0f, cg + 0.20f), Math.min(1.0f, cb + 0.10f), pulse * 0.8f); // m385 双画内缩伪加粗
        if (r > 0) { // 内部分块格线（退化盒=恒淡竖直隔板框，玩家一眼读出 3×3/5×5）
            for (int gi = 1; gi < w; gi++) {
                double gx = x1 + gi * 16.0, gz = z1 + gi * 16.0;
                LevelRenderer.renderLineBox(ms, vc, gx, y1, z1, gx, y2, z2, cr * 0.85f, cg * 0.85f, cb * 0.90f, 0.25f);
                LevelRenderer.renderLineBox(ms, vc, x1, y1, gz, x2, y2, gz, cr * 0.85f, cg * 0.85f, cb * 0.90f, 0.25f);
            }
        }
        if (cfg.chunkHighlightXray) xray(ms, consumers, x1, y1, z1, x2, y2, z2, cr, cg, cb);
    }

    /** m393 穿墙透视线（原版 F3+G 区块边界同款层）：站在坑里/地下也能看见自己圈了多大一片。
     *  DEBUG_LINE_STRIP 是**连续笔画**，故一次单笔走完 12 条棱（回描不影响观感）并当场冲出，
     *  否则下一个区域的首点会与本区域末点连成一条横跨天际的飞线。 */
    private static void xray(PoseStack ms, MultiBufferSource consumers,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             float cr, float cg, float cb) {
        RenderType layer = RenderType.debugLineStrip(2.0);
        VertexConsumer vc = consumers.getBuffer(layer);
        org.joml.Matrix4f m = ms.last().getPositionMatrix();
        float r = cr * 0.75f, g = cg * 0.75f, b = cb * 0.75f, a = 0.60f;
        float ax = (float) x1, bx = (float) x2, az = (float) z1, bz = (float) z2, ay = (float) y1, by = (float) y2;
        // 单笔路径：底框四棱 → 立柱与顶框交替（每根立柱上去、走一条顶棱、原路回描下来）
        pt(vc, m, ax, ay, az, r, g, b, a); pt(vc, m, bx, ay, az, r, g, b, a);
        pt(vc, m, bx, ay, bz, r, g, b, a); pt(vc, m, ax, ay, bz, r, g, b, a);
        pt(vc, m, ax, ay, az, r, g, b, a);
        pt(vc, m, ax, by, az, r, g, b, a); pt(vc, m, bx, by, az, r, g, b, a);
        pt(vc, m, bx, ay, az, r, g, b, a); pt(vc, m, bx, by, az, r, g, b, a);
        pt(vc, m, bx, by, bz, r, g, b, a); pt(vc, m, bx, ay, bz, r, g, b, a);
        pt(vc, m, bx, by, bz, r, g, b, a); pt(vc, m, ax, by, bz, r, g, b, a);
        pt(vc, m, ax, ay, bz, r, g, b, a); pt(vc, m, ax, by, bz, r, g, b, a);
        pt(vc, m, ax, by, az, r, g, b, a);
        if (consumers instanceof MultiBufferSource.BufferSource imm) imm.endBatch(layer);
    }

    private static void pt(VertexConsumer vc, org.joml.Matrix4f m, float x, float y, float z,
                           float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z).color(r, g, b, a);
    }

    /** m393 工作态重扫：遍历玩家周边已加载分块的方块实体表，收核心画布里的移除器节点选区。
     *  只读客户端已有数据（m275 渲染子集里就有 machineNodes），零协议零请求；未加载分块直接跳过，
     *  绝不调用会触发加载的口（客户端也不该替玩家拉区块）。封顶 32 个选区防病态刷屏。 */
    private static void rescan(Minecraft mc, String dim, SdzjzConfig cfg) {
        WORKING.clear();
        int rad = Math.max(1, Math.min(16, cfg.chunkHighlightScanChunks));
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        net.minecraft.world.level.chunk.ChunkSource cm = mc.level.getChunkManager();
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                int cx = pcx + dx, cz = pcz + dz;
                if (!cm.hasChunk(cx, cz)) continue;
                net.minecraft.world.level.chunk.LevelChunk ch = cm.getChunkNow(cx, cz);
                if (ch == null) continue;
                for (net.minecraft.world.level.block.entity.BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof com.sdzjz.block.StructureCoreBlockEntity core)) continue;
                    for (ItemStack st : core.nodes()) {
                        if (!(st.getItem() instanceof com.sdzjz.item.ChunkRemoverItem)) continue;
                        if (NodeTags.nodePaused(st) || !NodeTags.chunkBound(st) || NodeTags.chunkDone(st)) continue;
                        if (!dim.equals(NodeTags.chunkDim(st))) continue;
                        int r = Math.max(0, Math.min(NodeTags.chunkRadius(st), Math.max(0, cfg.chunkRemoverMaxRadius)));
                        WORKING.add(new int[]{NodeTags.chunkX(st), NodeTags.chunkZ(st), r});
                        if (WORKING.size() >= 32) return;
                    }
                }
            }
        }
    }
}
