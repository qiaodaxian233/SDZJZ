package com.sdzjz.storage;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * m404 物品传输平台口——多加载器路线 P1 第二刀（耦合尺排名第二：transfer 69 用点 / 6 文件）。
 *
 * <p><b>句柄一律不透明（{@code Object}）</b>：Fabric 侧真身是 {@code Storage<ItemVariant>}，
 * NeoForge 侧将是 {@code IItemHandler}（能力系统），语义差别大到不值得强行统一类型；
 * 业务侧（抽取口/数据面板/连线视觉）只当"邻面上有个能收能给的东西"用——这正是 m362 起
 * `RecipeAccess` 的 {@code Level→Object} 不透明句柄范式，全仓一以贯之。
 *
 * <p><b>m434 接口化（漏斗销账第三名）</b>：门面迁 xplat，Fabric 内脏抽成 {@link Impl}
 * 进 loader/FabricXfer（Sdzjz.onInitialize 首段安装）。顺手修一处层次毛病：原
 * {@code moveToCore(src, StorageCoreBlockEntity core, …)} 摸了 loader 层的存储核心类型，
 * 泛化为双不透明句柄 {@link #move}（调用侧自取 {@code core.fabricStorage()} 当 dst）——
 * 语义逐位不变（"取得出且存得进"的量才搬，余量留在机器里绝不落地），且这正是
 * <b>机械动力对接面</b>：Create 的 Fabric 侧同走 transfer API，find/insert/move 对
 * 传送带/漏斗/置物台开箱即通，1.20.1 格照此同款（该版 fabric-api 同有此 API）。
 *
 * <p><b>刻意留在原地的两处</b>：①{@code StorageCoreBlockEntity.FabricLedger}=我们**提供**给别的模组的
 * 那个视图（含 m278 增量事务日志），它天生属于加载器层，抽口没有意义——**m533（F1c）已整段搬成
 * {@code src/loader/FabricStorageAdapter}**（BE 只留不透明缓存槽 {@code transferAdapter(Supplier)}，
 * 自家消费点 {@code DataCableBlockEntity.coreStorage} 改走本类 {@link #find} 按核心坐标取）；
 * ②GameTest 里的 FTA 调用是测试代码，同理归加载器侧（F1d 随判官注册口一起拆）。
 */
public final class Xfer {

    private Xfer() { }

    /** 加载器要给的五个口（m434）：语义见各静态门面注释。 */
    public interface Impl {
        Object find(Level world, BlockPos np, Direction side);
        boolean canInsert(Object handle);
        boolean canExtract(Object handle);
        long insert(Object handle, ItemStack template, boolean exact, long amount);
        long move(Object from, Object to, java.util.function.Predicate<ItemStack> filter, long max);
    }

    private static Impl impl;

    /** 加载器入口最先段调（重复安装直接炸出来）。 */
    public static void install(Impl i) {
        if (impl != null) throw new IllegalStateException("Xfer 平台实现重复安装");
        impl = i;
    }

    private static Impl req() {
        if (impl == null) throw new IllegalStateException("Xfer 平台实现未安装：加载器入口须先调 Xfer.install(...)（Fabric=Sdzjz.onInitialize 首段）");
        return impl;
    }

    /** 找邻面视图（side=从邻块的哪一面接入；null=不分面）。无视图返回 null。 */
    public static Object find(Level world, BlockPos np, Direction side) {
        return req().find(world, np, side);
    }

    public static boolean canInsert(Object handle) {
        return req().canInsert(handle);
    }

    public static boolean canExtract(Object handle) {
        return req().canExtract(handle);
    }

    /** 单笔插入并提交，返回实际收下的数量。
     *  {@code exact=false} 走"裸物品"变体（与 m225 的 {@code ItemVariant.of(Item)} 逐位同义），
     *  {@code exact=true} 连组件（附魔书/药水）。 */
    public static long insert(Object handle, ItemStack template, boolean exact, long amount) {
        return req().insert(handle, template, exact, amount);
    }

    /** 从 from 句柄搬进 to 句柄（m231 回收拍；m434 前身 moveToCore，dst 泛化为句柄）。
     *  filter=null 表示全收（省掉每候选一次 toStack）。返回实际搬动件数；
     *  "取得出且存得进"的量才搬，余量留在机器里绝不落地。 */
    public static long move(Object from, Object to, java.util.function.Predicate<ItemStack> filter, long max) {
        return req().move(from, to, filter, max);
    }
}
