package com.sdzjz.storage;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * m404 物品传输平台口——多加载器路线 P1 第二刀（耦合尺排名第二：transfer 69 用点 / 6 文件）。
 *
 * <p><b>句柄一律不透明（{@code Object}）</b>：Fabric 侧真身是 {@code Storage<ItemVariant>}，
 * NeoForge 侧将是 {@code IItemHandler}（能力系统），语义差别大到不值得强行统一类型；
 * 业务侧（抽取口/数据面板/连线视觉）只当"邻面上有个能收能给的东西"用——这正是 m362 起
 * `RecipeAccess` 的 {@code World→Object} 不透明句柄范式，全仓一以贯之。
 *
 * <p><b>行为逐位一致</b>：本类只是把原来散在业务里的 Fabric 调用原样搬进来——
 * {@code ItemStorage.SIDED.find}、单笔 {@code Transaction.openOuter()+commit}、
 * {@code StorageUtil.move}（"取得出且存得进"的量才搬，仓容不足余量留在机器里绝不落地）
 * 一个字没改，只是换了个门牌。
 *
 * <p><b>刻意留在原地的两处</b>：①{@code StorageCoreBlockEntity.FabricLedger}=我们**提供**给别的模组的
 * 那个视图（含 m278 增量事务日志），它天生属于加载器层，抽口没有意义——等目录分层时整体搬进
 * `versions/&lt;loader&gt;`；②GameTest 里的 FTA 调用是测试代码，同理归加载器侧。
 */
public final class Xfer {

    private Xfer() { }

    /** 找邻面视图（side=从邻块的哪一面接入；null=不分面）。无视图返回 null。 */
    public static Object find(World world, BlockPos np, Direction side) {
        return net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(world, np, side);
    }

    public static boolean canInsert(Object handle) {
        return handle instanceof net.fabricmc.fabric.api.transfer.v1.storage.Storage<?> s && s.supportsInsertion();
    }

    public static boolean canExtract(Object handle) {
        return handle instanceof net.fabricmc.fabric.api.transfer.v1.storage.Storage<?> s && s.supportsExtraction();
    }

    /** 单笔插入并提交，返回实际收下的数量。
     *  {@code exact=false} 走"裸物品"变体（与 m225 的 {@code ItemVariant.of(Item)} 逐位同义），
     *  {@code exact=true} 连组件（附魔书/药水）。 */
    @SuppressWarnings("unchecked")
    public static long insert(Object handle, ItemStack template, boolean exact, long amount) {
        if (!(handle instanceof net.fabricmc.fabric.api.transfer.v1.storage.Storage<?> raw) || amount <= 0) return 0;
        var target = (net.fabricmc.fabric.api.transfer.v1.storage.Storage<
                net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>) raw;
        var v = exact ? net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(template)
                : net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(template.getItem());
        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long ins = target.insert(v, amount, tx);
            tx.commit();
            return ins;
        }
    }

    /** 从邻面视图搬进本模组存储核心（m231 回收拍）。filter=null 表示全收（省掉每候选一次 toStack）。
     *  返回实际搬动件数；"取得出且存得进"的量才搬，余量留在机器里绝不落地。 */
    @SuppressWarnings("unchecked")
    public static long moveToCore(Object src, com.sdzjz.block.StorageCoreBlockEntity core,
                                  java.util.function.Predicate<ItemStack> filter, long max) {
        if (!(src instanceof net.fabricmc.fabric.api.transfer.v1.storage.Storage<?> raw) || max <= 0) return 0;
        var from = (net.fabricmc.fabric.api.transfer.v1.storage.Storage<
                net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>) raw;
        java.util.function.Predicate<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> pred =
                filter == null ? v -> true : v -> filter.test(v.toStack());
        return net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil.move(
                from, core.fabricStorage(), pred, max, null);
    }
}
