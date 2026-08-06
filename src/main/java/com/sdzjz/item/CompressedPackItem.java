package com.sdzjz.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 压缩材料包（m241 / 方案A，作者拍板）：两件通用物品承载"原版物品的数量压缩"——
 *   一级「压缩材料包」= 64 × 内容物；二级「超级压缩材料包」= 4096（64²）× 内容物。
 * 内容物物品 id 记在 CUSTOM_DATA（CaptureCageItem 同款通道，零新组件类型），
 * 组件不同的包天然不混堆（组件参与栈相等性），同内容的包正常堆叠。
 *
 * 设计口径（作者原话）："检查物品就是原版的物品数量压缩"——包在超大工作台配方核算里
 * 一律按 内容物 × 倍率 折算成原版计数参与匹配/扣料（m242 匹配内核接线），
 * 工程款 BOM 仍记原版总数，包只是搬运介质。
 *
 * 压/拆入口：超大工作台右下"压缩区"两钮（服务端权威，见 SuperBenchScreenHandler）。
 * 只压"无组件差异的普通物品"——附魔书/药水这类组件物品跳过，防压包抹组件（精确条目教训同源）。
 */
public class CompressedPackItem extends Item {
    private static final String KEY = "pack"; // CUSTOM_DATA 下的内容物 id 键

    /** 压缩倍率：一级 64，二级 4096。 */
    public final int ratio;

    public CompressedPackItem(Settings settings, int ratio) {
        super(settings);
        this.ratio = ratio;
    }

    /** 造一个装着 innerId 的包（count 个）。 */
    public static ItemStack of(Item packItem, String innerId, int count) {
        ItemStack s = new ItemStack(packItem, count);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(KEY, innerId);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return s;
    }

    /** 取包的内容物物品 id（没装东西的裸包返回 null——裸包不参与任何核算）。 */
    public static String innerId(ItemStack stack) {
        if (!(stack.getItem() instanceof CompressedPackItem)) return null;
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null) return null;
        NbtCompound nbt = c.copyNbt();
        return nbt.contains(KEY) ? nbt.getString(KEY) : null;
    }

    /** 该栈折算成原版计数的量（非包/裸包=0）。 */
    public static long rawCount(ItemStack stack) {
        String id = innerId(stack);
        if (id == null) return 0L;
        return (long) stack.getCount() * ((CompressedPackItem) stack.getItem()).ratio;
    }

    private Item inner(ItemStack stack) {
        String id = innerId(stack);
        return id == null ? null : Registries.ITEM.get(Identifier.of(id));
    }

    @Override
    public Text getName(ItemStack stack) { // yarn method_7864 已核
        Item in = inner(stack);
        if (in == null) return super.getName(stack);
        return Text.translatable(this.getTranslationKey()).copy().append(" · ").append(in.getName());
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, java.util.List<Text> tooltip,
                              net.minecraft.item.tooltip.TooltipType type) {
        Item in = inner(stack);
        if (in != null) {
            tooltip.add(Text.literal("= " + ratio + " × ").append(in.getName())
                    .formatted(net.minecraft.util.Formatting.AQUA));
            tooltip.add(Text.literal("配方核算按原版计数折算（本栈合计 "
                    + fmt(rawCount(stack)) + " 件）").formatted(net.minecraft.util.Formatting.GRAY));
        } else {
            tooltip.add(Text.literal("空包 · 在超大工作台压缩区把 " + ratio + " 件同种普通物品压成 1 包")
                    .formatted(net.minecraft.util.Formatting.GRAY));
        }
        tooltip.add(Text.literal("超大工作台右下压缩区可压缩/拆开").formatted(net.minecraft.util.Formatting.DARK_GRAY));
    }

    /** 大数缩写（与数据面板口径一致的 K/M/B）。 */
    private static String fmt(long n) {
        if (n >= 1_000_000_000L) return String.format("%.1fB", n / 1_000_000_000.0);
        if (n >= 1_000_000L) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000L) return String.format("%.1fK", n / 1_000.0);
        return Long.toString(n);
    }
}
