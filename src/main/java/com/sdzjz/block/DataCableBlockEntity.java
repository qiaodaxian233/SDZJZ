package com.sdzjz.block;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** 数据线方块实体：能量脉冲动画的渲染载体（BER 挂点）；m225 起兼作「抽取口」——扳手启用后，
 *  周期性把相连存储核心账本里的物品（按过滤，空=全部）塞进邻接的任意 Fabric Transfer API 存储。 */
public class DataCableBlockEntity extends BlockEntity {

    // ===== m225 抽取口状态（NBT 持久化；过滤模板由 m226 配置界面编辑）=====
    private boolean extractOn = false;
    private final List<ItemStack> filter = new ArrayList<>(); // 模板 count 恒 1，≤9 条；空=全部抽取
    private int rrCursor = 0;                                  // 全部模式的类型轮转游标（跨拍公平，不持久化）
    private boolean opTargetsFull = false;                     // 本拍"目标已塞满"标志（extractSpec 置位，两模式统一收工）
    private long coresScanTick = Long.MIN_VALUE;               // 相连核心 40t 缓存（m108c 同款语义）
    private List<BlockPos> coresCache = List.of();

    public DataCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_CABLE_BE, pos, state);
    }

    public boolean extractOn() { return extractOn; }
    public void setExtractOn(boolean on) { extractOn = on; markDirty(); }
    /** 过滤模板视图（m226 界面读写；写入方自行 markDirty）。 */
    public List<ItemStack> filterView() { return filter; }

    /** m224 邻接可抽取存储探测：任意暴露 Fabric Transfer API 的容器都算（原版箱子有 Fabric 内建适配；
     *  Tom's Simple Storage / AE2 / Storage Drawers / Create 等一切 Fabric 物流模组即插即用——走标准
     *  API 不做逐模组集成，即多版本兼容口径）。自家网络方块一律排除：存储核心自身就暴露 FTA（m161c），
     *  抽给它=左手倒右手；结构核心实现 Inventory 会被 Fabric 兜底适配捞到，同样排除。 */
    public static List<Storage<ItemVariant>> adjacentStorages(World world, BlockPos pos) {
        List<Storage<ItemVariant>> out = new ArrayList<>();
        if (world == null || world.isClient) return out;
        for (Direction d : Direction.values()) {
            BlockPos np = pos.offset(d);
            BlockEntity be = world.getBlockEntity(np);
            if (be != null && be.getClass().getName().startsWith("com.sdzjz")) continue; // 自家网络方块不作抽取目标
            Storage<ItemVariant> st = ItemStorage.SIDED.find(world, np, d.getOpposite()); // 先按贴线面查
            if (st == null) st = ItemStorage.SIDED.find(world, np, null);                 // 部分模组只登记无侧访问
            if (st != null && st.supportsInsertion()) out.add(st);
        }
        return out;
    }

    /** m225 抽取口主拍：pos 哈希移相（m218c 口径，多口不挤同一全局 tick），每拍最多搬 extractPortBatch 件。 */
    public static void tick(World world, BlockPos pos, BlockState state, DataCableBlockEntity be) {
        if (world.isClient || !be.extractOn) return;
        int period = Math.max(1, SdzjzConfig.get().extractPortPeriodTicks);
        if (Math.floorMod(world.getTime() + pos.hashCode(), period) != 0) return;
        List<Storage<ItemVariant>> targets = adjacentStorages(world, pos);
        if (targets.isEmpty()) return;
        List<StorageCoreBlockEntity> cores = be.cores(world, pos);
        if (cores.isEmpty()) return;
        long budget = Math.max(1, SdzjzConfig.get().extractPortBatch);
        be.opTargetsFull = false;
        List<ItemStack> want = new ArrayList<>();
        for (ItemStack f : be.filter) if (!f.isEmpty()) want.add(f);
        if (want.isEmpty()) be.extractAll(cores, targets, budget);      // 空过滤=全部抽取（游标轮转）
        else for (ItemStack tpl : want) {                                // 白名单：逐模板抽
            budget -= be.extractSpec(cores, targets, tpl, budget);
            if (budget <= 0 || be.opTargetsFull) break;                  // 目标满：本拍收工（余下模板下拍再来）
        }
    }

    /** 相连存储核心（数据线 BFS，40t 缓存防每拍裸扫——m218b 精确支路同教训；存位置逐拍 loadedCoreAt
     *  解引用，绝不缓存 BE 引用跨卸载）。 */
    private List<StorageCoreBlockEntity> cores(World world, BlockPos pos) {
        if (coresScanTick == Long.MIN_VALUE || world.getTime() - coresScanTick >= 40) {
            coresScanTick = world.getTime();
            List<BlockPos> ps = new ArrayList<>();
            for (StorageCoreBlockEntity c : StorageCoreBlockEntity.connectedCores(world, pos)) ps.add(c.getPos().toImmutable());
            coresCache = ps;
        }
        List<StorageCoreBlockEntity> out = new ArrayList<>(coresCache.size());
        for (BlockPos p : coresCache) {
            StorageCoreBlockEntity c = StorageCoreBlockEntity.loadedCoreAt(world, p);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** 按单个模板抽取：无组件=普通账本按 id；带组件=精确账本按模板（附魔书/药水连组件搬，m130 口径）。
     *  塞不下的一律回账本绝不落地；返回实际搬动件数。 */
    private long extractSpec(List<StorageCoreBlockEntity> cores, List<Storage<ItemVariant>> targets, ItemStack tpl, long max) {
        if (tpl.isEmpty() || max <= 0) return 0;
        boolean exact = !tpl.getComponentChanges().isEmpty();
        String id = exact ? null : Registries.ITEM.getId(tpl.getItem()).toString();
        ItemVariant v = exact ? ItemVariant.of(tpl) : ItemVariant.of(tpl.getItem());
        long moved = 0;
        for (StorageCoreBlockEntity core : cores) {
            while (moved < max) {
                int ask = (int) Math.min(max - moved, Integer.MAX_VALUE);
                int take = exact ? core.withdrawExact(tpl, ask) : core.withdraw(id, ask);
                if (take <= 0) break;
                long ins = insertInto(targets, v, take);
                if (ins < take) { // 目标满：余量回账本（绝不落地），置位统一收工标志
                    opTargetsFull = true;
                    ItemStack back = exact ? tpl.copyWithCount((int) (take - ins)) : new ItemStack(tpl.getItem(), (int) (take - ins));
                    if (exact) core.depositExact(back); else core.deposit(back);
                    return moved + ins;
                }
                moved += ins;
            }
            if (moved >= max) break;
        }
        return moved;
    }

    /** 全部模式：普通账本 id 全表 + 精确账本模板全表拼成候选序列，游标轮转起步（跨拍公平）；
     *  一旦出现"取得出塞不进"即判目标满，整拍收工。 */
    private void extractAll(List<StorageCoreBlockEntity> cores, List<Storage<ItemVariant>> targets, long budget) {
        List<ItemStack> specs = new ArrayList<>();
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (StorageCoreBlockEntity core : cores) {
            ids.addAll(core.storeView().keySet());
            for (ItemStack t : core.exactTemplates()) specs.add(t.copyWithCount(1));
        }
        List<ItemStack> plain = new ArrayList<>(ids.size());
        for (String id : ids) {
            net.minecraft.item.Item it = Registries.ITEM.get(net.minecraft.util.Identifier.of(id));
            ItemStack st = new ItemStack(it);
            if (!st.isEmpty()) plain.add(st);
        }
        plain.addAll(specs);
        if (plain.isEmpty()) return;
        int n = plain.size(), start = Math.floorMod(rrCursor, n);
        for (int k = 0; k < n && budget > 0; k++) {
            int i = (start + k) % n;
            budget -= extractSpec(cores, targets, plain.get(i), budget);
            rrCursor = i + 1;
            if (opTargetsFull) break; // 目标满：整拍收工，游标停在此处下拍续
        }
    }

    private long insertInto(List<Storage<ItemVariant>> targets, ItemVariant v, long amount) {
        long done = 0;
        for (Storage<ItemVariant> t : targets) {
            if (done >= amount) break;
            try (Transaction tx = Transaction.openOuter()) {
                long ins = t.insert(v, amount - done, tx);
                tx.commit();
                done += ins;
            }
        }
        return done;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putBoolean("extractOn", extractOn);
        NbtList fl = new NbtList(); // 过滤模板持久化（精确账本 m130 同款 encode）
        for (ItemStack f : filter) if (!f.isEmpty()) fl.add(f.encode(lookup));
        nbt.put("filter", fl);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        extractOn = nbt.getBoolean("extractOn");
        filter.clear();
        NbtList fl = nbt.getList("filter", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < fl.size(); i++) {
            ItemStack t = ItemStack.fromNbt(lookup, fl.getCompound(i)).orElse(ItemStack.EMPTY);
            if (!t.isEmpty()) filter.add(t.copyWithCount(1)); // 解析失败/物品已卸载静默跳过，不炸档
        }
    }
}
