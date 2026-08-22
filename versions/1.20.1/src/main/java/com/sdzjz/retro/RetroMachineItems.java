package com.sdzjz.retro;

import com.sdzjz.machine.MachineDef;
import com.sdzjz.machine.Machines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * m453（C2-②）：机器物品批量注册——**Machines.java（common）是唯一数据源**，本类零机器名单：
 * 反射枚举 Machines 的 public static final MachineDef 字段（101 台，主线加机器=本世代自动跟上，
 * 零双写），按 id 排序定序（反射字段序 JVM 不保证，排序换确定性——创造页与 Legacy 手排序不同，
 * 记档为世代差非漏抄）。物品 id 与 Legacy 逐台同名（item.sdzjz.<id> lang 键同源共用）。
 *
 * <p>本世代机器物品=占位收藏品（tooltip 注明画布移植中）：Legacy 的 MachineItem 族（画布放置
 * /徽章选择等 20+ 专用类）随 C2-③ 画布到位后逐类接行为，注册面今日先立住让 101 台在创造页/
 * 面板/仓储全链路可见可流转。
 */
final class RetroMachineItems {

    private RetroMachineItems() { }

    private static final List<Item> ITEMS = new ArrayList<>();

    /** 注册全部机器物品（RetroBlocks.register 内调，先于创造页事件消费）。 */
    static void registerAll() {
        List<MachineDef> defs = allDefs();
        for (MachineDef def : defs) {
            Item item = Registry.register(BuiltInRegistries.ITEM,
                    new ResourceLocation("sdzjz", def.id()), new RetroMachineItem(def));
            ITEMS.add(item);
        }
    }

    /** 已注册机器物品（创造页/判官消费；注册序=id 序，确定性见类注）。 */
    static List<Item> items() { return ITEMS; }

    /** 反射枚举唯一数据源（排序定序）。 */
    static List<MachineDef> allDefs() {
        List<MachineDef> defs = new ArrayList<>();
        for (Field f : Machines.class.getFields()) {
            if (f.getType() == MachineDef.class && Modifier.isStatic(f.getModifiers())) {
                try {
                    defs.add((MachineDef) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("[sdzjz] Machines 字段反射失败: " + f.getName(), e); // public 字段不该发生，硬失败别吞
                }
            }
        }
        defs.sort(Comparator.comparing(MachineDef::id));
        return defs;
    }

    /** 占位机器物品：持 def（画布 C2-③ 起消费），tooltip 注明状态。 */
    static final class RetroMachineItem extends Item {

        final MachineDef def;

        RetroMachineItem(MachineDef def) {
            super(new Item.Properties());
            this.def = def;
        }

        /** 1.20.1 签名：四参带 @Nullable Level（1.20.5 起改 TooltipContext——版本差行内指认）。 */
        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("sdzjz.machine.wip"));
        }
    }
}
