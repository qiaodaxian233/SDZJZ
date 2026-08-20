package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * m399 无限距离信标（作者点名新增）——原版信标的三条枷锁一次拆干净：
 * **不要金字塔、不要天空可见、不看距离**。放上画布即工作：每周期从存储网络扣一份信标料
 * （铁锭/金锭/绿宝石/钻石/下界合金锭，原版收料表同款，从便宜到贵依次扣），
 * 把选定的效果刷给**全服在线玩家**（默认跨维度，config 可收成"只管本核心所在维度"）。
 *
 * <p>效果六选一（菜单循环）：急迫/速度/抗性提升/跳跃提升/力量/生命恢复；等级 I/II 切换，
 * II 级每周期料 ×`infiniteBeaconLevel2Cost`。效果是**刷新式**（每周期续时长，不叠加不延长），
 * 所以速度/数量升级对本机没有收益——tooltip 明写，别让人白灌升级（m99）。
 *
 * <p>接线五件：tick 专属分支 / accepts=恒假（掉落表空+consumesInputs=false，走 accepts0 尾兜自然为假，
 * 料从存储扣不吃路由）/ setNodeTarget=不适用（效果走菜单两哨兵 #bfx #bfl）/ 客户端徽章=副行 canvasLine /
 * chainWants=显式零需求（不吃线上料自然不拉料，与复制机同律）。
 */
public class InfiniteBeaconItem extends MachineItem {

    /** 效果表（双端同源唯一权威：服务端 tick 反查注册表 / 客户端菜单取名；序号存节点 NBT 键 bfx）。
     *  刻意用 id 串反查注册表而不是引用 StatusEffects 常量：常量名核不到官方映射，id 串是数据层稳定契约。 */
    public static final String[] FX_ID = {
            "minecraft:haste", "minecraft:speed", "minecraft:resistance",
            "minecraft:jump_boost", "minecraft:strength", "minecraft:regeneration"};
    public static final String[] FX_NAME = {"急迫", "速度", "抗性提升", "跳跃提升", "力量", "生命恢复"};

    /** 信标料表（原版信标收料表同款，按从便宜到贵依次扣）。 */
    public static final String[] FUELS = {
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:emerald",
            "minecraft:diamond", "minecraft:netherite_ingot"};

    public InfiniteBeaconItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    private static int clampFx(int i) { return (i < 0 || i >= FX_ID.length) ? 0 : i; }

    /** 当前效果序号（越界读收 0=急迫，防旧档/伪造包脏值）。 */
    public static int effectIndex(ItemStack s) { return clampFx(com.sdzjz.node.NodeTags.beaconEffect(s)); }

    /** 当前等级放大值（0=I 级，1=II 级）。 */
    public static int level(ItemStack s) { return com.sdzjz.node.NodeTags.beaconLevel(s) >= 1 ? 1 : 0; }

    public static int nextEffect(int cur) { return (clampFx(cur) + 1) % FX_ID.length; }

    public static String effectName(int idx) { return FX_NAME[clampFx(idx)]; }

    /** 效果注册表项（id 串反查；查不到=null，tick 侧报红灯说人话，不静默不生效）。 */
    public static net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effectEntry(int idx) {
        net.minecraft.util.Identifier ident = net.minecraft.util.Identifier.tryParse(FX_ID[clampFx(idx)]);
        if (ident == null) return null;
        return net.minecraft.registry.Registries.STATUS_EFFECT.getEntry(ident).orElse(null);
    }

    /** 画布节点副行文案（客户端徽章行唯一口径）。 */
    public static String canvasLine(ItemStack s) {
        com.sdzjz.config.SdzjzConfig cfg = com.sdzjz.config.SdzjzConfig.get();
        int lv = level(s);
        int need = Math.max(1, cfg.infiniteBeaconFuelPerCycle) * (lv == 1 ? Math.max(1, cfg.infiniteBeaconLevel2Cost) : 1);
        return effectName(effectIndex(s)) + (lv == 1 ? " II" : " I")
                + (cfg.infiniteBeaconCrossDimension ? " · 全维度" : " · 本维度")
                + " · " + need + " 料/周期";
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("不要金字塔、不要天空、不看距离：放上画布即给全服玩家上 buff").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("每周期从存储网络扣一份信标料（铁锭/金锭/绿宝石/钻石/下界合金锭，先扣便宜的）").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("当前：" + canvasLine(stack) + "；效果/等级在画布节点菜单切换").formatted(Formatting.GREEN));
        tooltip.add(Text.literal("效果是刷新式（每周期续时长，不叠加不延长）——所以速度/数量升级对本机没有收益，别白灌").formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("要接存储网络：料从仓里扣，没料=红灯停发（不赊账）").formatted(Formatting.RED));
    }
}
