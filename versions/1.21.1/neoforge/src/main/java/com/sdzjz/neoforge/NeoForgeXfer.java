package com.sdzjz.neoforge;

import com.sdzjz.storage.Xfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** m535（F1d-1）{@link Xfer.Impl} 的 NeoForge 实现——**消费侧**五口（我们去摸别人的容器），句柄真身 {@link IItemHandler}
 *  （Fabric 侧是 {@code Storage<ItemVariant>}，m404 早预言「语义差别大到不值得强行统一类型」）。**提供侧**（存储核心暴露
 *  IItemHandler 给别人）随 F1d-3：{@code RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.ItemHandler.BLOCK, …)}
 *  + 账本的有界槽位视图，用 BE 的 {@code transferAdapter} 槽（m534）。
 *  <p><b>语义对位（评估报告 §14.1）</b>：Fabric 走事务（模拟=开事务不提交）；IItemHandler 走 simulate 布尔。本类的 {@code move}
 *  按「先双侧模拟、只搬取得出且存得进的量、再真抽真塞」写——结算以真塞的余量为准，模拟结果只用于规划；真塞余量若与模拟不符
 *  （第三方容器口是心非）原路塞回源槽，**绝不落地也绝不蒸发**。 */
public final class NeoForgeXfer implements Xfer.Impl {
    @Override
    public Object find(Level world, BlockPos np, Direction side) {
        return world.getCapability(Capabilities.ItemHandler.BLOCK, np, side); // side=null 即不分面（BlockCapability 的 @Nullable Direction 上下文）
    }

    @Override
    public boolean canInsert(Object handle) {
        return handle instanceof IItemHandler h && h.getSlots() > 0; // IItemHandler 无 supportsInsertion，有槽即视为可试
    }

    @Override
    public boolean canExtract(Object handle) {
        return handle instanceof IItemHandler h && h.getSlots() > 0;
    }

    /** 单笔插入并提交。exact=false 走裸物品（与 Fabric {@code ItemVariant.of(Item)} 同义：丢组件）；exact=true 连组件。
     *  amount 可大于一组：按堆叠上限分批喂，喂不进即停，返回实际收下量。 */
    @Override
    public long insert(Object handle, ItemStack template, boolean exact, long amount) {
        if (!(handle instanceof IItemHandler target) || amount <= 0 || template.isEmpty()) return 0;
        ItemStack unit = exact ? template.copyWithCount(1) : new ItemStack(template.getItem());
        long left = amount;
        while (left > 0) {
            int n = (int) Math.min(left, unit.getMaxStackSize());
            ItemStack rem = ItemHandlerHelper.insertItem(target, unit.copyWithCount(n), false);
            int ins = n - rem.getCount();
            if (ins <= 0) break;
            left -= ins;
        }
        return amount - left;
    }

    /** 从 from 搬进 to（m231 回收拍）。filter=null 全收。逐源槽：模拟抽→模拟塞→按能塞下的量真抽→真塞；返回实际搬动件数。 */
    @Override
    public long move(Object from, Object to, java.util.function.Predicate<ItemStack> filter, long max) {
        if (!(from instanceof IItemHandler src) || !(to instanceof IItemHandler dst) || max <= 0) return 0;
        long moved = 0;
        for (int i = 0; i < src.getSlots() && moved < max; i++) {
            while (moved < max) {
                ItemStack sim = src.extractItem(i, (int) Math.min(max - moved, Integer.MAX_VALUE), true);
                if (sim.isEmpty() || (filter != null && !filter.test(sim))) break;
                ItemStack remSim = ItemHandlerHelper.insertItem(dst, sim.copy(), true);
                int fit = sim.getCount() - remSim.getCount();
                if (fit <= 0) break; // 目标一件都吃不下：换下一槽
                ItemStack real = src.extractItem(i, fit, false);
                if (real.isEmpty()) break;
                ItemStack rem = ItemHandlerHelper.insertItem(dst, real, false);
                moved += real.getCount() - rem.getCount();
                if (!rem.isEmpty()) { // 模拟说能收、真塞没全收：余量原路塞回源槽，塞不回再试源容器任意槽；仍有余量=对方容器口是心非，记日志停手
                    ItemStack back = src.insertItem(i, rem, false);
                    if (!back.isEmpty()) back = ItemHandlerHelper.insertItem(src, back, false);
                    if (!back.isEmpty()) org.slf4j.LoggerFactory.getLogger("sdzjz-neoforge").warn("[生电终结者] Xfer.move 余量 {} 无处可回（目标容器模拟与真塞不一致），已停止本拍搬运", back);
                    break;
                }
            }
        }
        return moved;
    }
}
