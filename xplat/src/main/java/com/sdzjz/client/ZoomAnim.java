package com.sdzjz.client;

/**
 * m512（真移植·A6）：**画布缩放平滑动效（m186）两代共用一份**——主线 {@code StructureCoreScreen} 的
 * {@code // ===== m186 缩放平滑动效 =====} 块整段搬：六个状态字段（原 65~69）+ m185 {@code clampZoom}（原 441）+
 * {@code setViewInstant}（原 449）+ {@code zoomToward}（原 454）+ {@code tickZoomAnim}（原 468）+ 三处"手动跳转/平移/适应
 * 终止动效"的两句（{@code zoomAnim = false; zoomTarget = zoom;}，原 501/838/2270 → {@link #stop}）+ {@code removed()} 里
 * "结算未完动效再存视图"那一句（原 365 → {@link #settle}）。
 *
 * <p><b>世代差为零</b>：全是算术 + {@code System.nanoTime()}（两代同名）+ common 配置 {@code canvasSmoothZoom/canvasZoomMin/Max}。
 * 两代屏各自的视图状态收进 {@link Host}：主线 {@code panX/panY/zoom} 直存直取；1.20.1 是 {@code viewX/viewY}（视口左上的画布坐标）
 * + float zoom——同一件事两种记法（m490 小地图 / m507 组框同款换算：{@code panX = -viewX*zoom}），换算在 Host 里做一次，
 * 本件按主线原文的 pan 记法写。写视图只走 {@link Host#view} 一口（三个量一次落，两代都不会出现"zoom 改了 pan 还没改"的半帧）。
 *
 * <p>原文里对 {@code zoom/panX/panY} 的连续多次赋值，这里改成先算局部再一次 {@code h.view(...)}——读写顺序上原文各次赋值之间
 * 没有读回，末态逐位相同（{@code tickZoomAnim} 原文先写 zoom 再用新 zoom 算 pan，本件同序）。
 */
public final class ZoomAnim {

    /** 两代屏的视图状态口：pan 记法（主线原生；1.20.1 由 viewX/viewY 换算）。 */
    public interface Host {
        double panX();
        double panY();
        double zoom();
        void view(double panX, double panY, double zoom);
    }

    private final Host h;

    // ===== m186 缩放平滑动效（anime.js easeOutExpo 思路移植：速度∝剩余距离，帧率无关）=====
    private double zoomTarget = 1.0;           // 缓动目标缩放
    private boolean zoomAnim = false;          // 动效进行中
    private double zoomAnchorSx, zoomAnchorSy; // 锚点屏幕坐标（指哪缩哪）
    private double zoomAnchorWx, zoomAnchorWy; // 锚点世界坐标
    private long zoomAnimNs = 0;               // 上帧时间戳

    public ZoomAnim(Host h) {
        this.h = h;
    }

    /** m185 缩放钳位统一出口：范围走配置（默认 5%~800%），下限兜底 0.01 防除零；配置写反自动纠序。 */
    public static double clampZoom(double z) {
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get();
        double lo = Math.max(0.01, Math.min(c.canvasZoomMin, c.canvasZoomMax));
        double hi = Math.max(lo, Math.max(c.canvasZoomMin, c.canvasZoomMax));
        return Math.max(lo, Math.min(hi, z));
    }

    /** m186 视图直设：重置/适应/恢复等瞬时路径统一走这里，顺手终止缩放动效防隔帧抢写。 */
    public void setViewInstant(double px, double py, double z) {
        h.view(px, py, z); zoomTarget = z; zoomAnim = false;
    }

    /** m186 朝目标缩放：锚点 (sx,sy) 屏幕点始终指着同一世界点；连滚累积在目标上；配置关平滑=瞬时跳变（旧行为）。 */
    public void zoomToward(double factor, double sx, double sy) {
        double zoom = h.zoom(), panX = h.panX(), panY = h.panY();
        double nz = clampZoom((zoomAnim ? zoomTarget : zoom) * factor);
        double wx = (sx - panX) / zoom, wy = (sy - panY) / zoom;
        if (!com.sdzjz.config.SdzjzConfig.get().canvasSmoothZoom) {
            zoomTarget = nz; zoomAnim = false;
            h.view(sx - wx * nz, sy - wy * nz, nz);
            return;
        }
        zoomAnchorSx = sx; zoomAnchorSy = sy; zoomAnchorWx = wx; zoomAnchorWy = wy;
        zoomTarget = nz;
        if (!zoomAnim) { zoomAnim = true; zoomAnimNs = System.nanoTime(); }
    }

    /** m186 每帧推进：指数趋近（1-e^{-14·dt}，半衰≈50ms），收敛吸附；锚点公式保证屏幕锚点纹丝不动。
     *  每帧最先调（先于一切使用 pan/zoom 的绘制）。 */
    public void tick() {
        if (!zoomAnim) return;
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - zoomAnimNs) / 1.0e9);
        zoomAnimNs = now;
        double zoom = h.zoom();
        zoom += (zoomTarget - zoom) * (1 - Math.exp(-14.0 * dt));
        if (Math.abs(zoomTarget - zoom) < zoomTarget * 0.002) { zoom = zoomTarget; zoomAnim = false; }
        h.view(zoomAnchorSx - zoomAnchorWx * zoom, zoomAnchorSy - zoomAnchorWy * zoom, zoom);
    }

    /** m186 结算未完动效再存视图（关屏时调：目标值当场落地，存下去的是终态不是中间帧）。 */
    public void settle() {
        if (zoomAnim) {
            double zoom = zoomTarget;
            h.view(zoomAnchorSx - zoomAnchorWx * zoom, zoomAnchorSy - zoomAnchorWy * zoom, zoom);
            zoomAnim = false;
        }
    }

    /** m186 手动跳转/平移/适应视图终止缩放动效防隔帧抢写（原文两句 {@code zoomAnim = false; zoomTarget = zoom;}）。 */
    public void stop() {
        zoomAnim = false; zoomTarget = h.zoom();
    }
}
