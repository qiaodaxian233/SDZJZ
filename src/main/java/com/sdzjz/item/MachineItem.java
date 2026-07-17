package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;

/** 机器物品：携带自己的 MachineDef。结构核心读取 def 即知产什么/多久/几个。 */
public class MachineItem extends Item {
    private final MachineDef def;

    public MachineItem(Settings settings, MachineDef def) {
        super(settings);
        this.def = def;
    }

    public MachineDef def() {
        return def;
    }
}
