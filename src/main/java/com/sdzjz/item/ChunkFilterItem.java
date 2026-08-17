package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import com.sdzjz.node.NodeTags;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * m377 区块过滤器——区块移除器的规则挂件（区块机器线第二台，招牌组合成对）。
 * 画布上与区块移除器连线（**任一方向，连上即生效**；多台过滤器=规则 AND 叠加；暂停即隔离 m110b），
 * 给移除器加两类规则：
 *  ① 方块名单（复用过滤节点 fl/fb 全套 UI：白名单=只挖名单内 / 黑名单=名单内不挖；
 *     **空名单=不限方块**——与物品过滤节点"白名单空=全拦"刻意相反，只配 Y 挡不配名单是核心用例）；
 *  ② Y 挡位（#xr 同款五挡循环换挡，零新协议）：全高度 / 地表下Y≤62 / 深层Y≤0 / 深板岩-64..0 / 地上Y≥63。
 * 本体不收不产不转发物品（accepts/chainWants 恒假），只当规则牌坊。
 * 游标单向：Y 挡缩窗会跳过窗外层且不回卷，要全量重扫=重绑移除器。
 */
public class ChunkFilterItem extends MachineItem {

    /** Y 挡位表（双端同源唯一权威：服务端 tick 取范围 / 客户端卡面菜单取名；序号存节点 NBT 键 zp）。 */
    public static final int PRESETS = 5;
    private static final String[] P_NAME = {"全高度", "地表下 Y≤62", "深层 Y≤0", "深板岩 -64..0", "地上 Y≥63"};
    private static final int[] P_MIN = {-2032, -2032, -2032, -64, 63};
    private static final int[] P_MAX = {2031, 62, 0, 0, 2031};

    public ChunkFilterItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    /** 当前挡位（越界读收 0=全高度，防旧档/伪造包脏值）。 */
    public static int preset(ItemStack s) {
        int p = NodeTags.chunkFilterPreset(s);
        return (p < 0 || p >= PRESETS) ? 0 : p;
    }

    public static String presetName(ItemStack s) { return P_NAME[preset(s)]; }

    public static int presetMinY(ItemStack s) { return P_MIN[preset(s)]; }

    public static int presetMaxY(ItemStack s) { return P_MAX[preset(s)]; }

    /** 方块名单判定（tick 侧唯一口径）：空名单=不限方块；白名单=只挖名单内，黑名单=名单内不挖。
     *  id=方块的物品形态 id（Block.asItem，无物品形态的方块判白名单永不中=保守不挖，黑名单不中=照挖）。 */
    public static boolean allowsBlock(ItemStack filterNode, String blockItemId) {
        List<String> l = NodeTags.filterList(filterNode);
        if (l.isEmpty()) return true;
        boolean in = l.contains(blockItemId);
        return NodeTags.filterBlacklist(filterNode) ? !in : in;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("区块移除器的规则挂件：画布上与移除器连线即生效（方向随意）").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("方块名单：白名单=只挖名单内 · 黑名单=名单内不挖 · 空=不限").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Y 挡位五挡循环：全高度/地表下/深层/深板岩/地上——\"只挖矿不拆建筑\"就靠它").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("多台过滤器规则叠加(AND)；本体不收不转发物品").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("放入画布后右键节点配置").formatted(Formatting.GRAY));
    }
}
