package com.sdzjz.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * m408 NeoForge 1.21.1 入口——**第二个加载器的第一块地基**（作者问"别的版本框架在哪"）。
 *
 * <p>现阶段职责只有一条：**证明 Core 层跨加载器可用**。启动时打印一行日志，把
 * {@code common/} 里的机器总数报出来——那份代码原封不动地同时被 Fabric 1.21.1 构建、
 * 26.2 构建、以及本 NeoForge 构建编译，一个字没有为谁特化。这是三层架构的第一次跨加载器实证。
 *
 * <p><b>为什么还没有业务</b>：xplat 业务层写的是 <b>Yarn 映射名</b>（{@code net.minecraft.item.ItemStack}），
 * 而 NeoForge 用 <b>Mojang 官方名</b>（{@code net.minecraft.world.item.ItemStack}）——同一份源码两边编不过。
 * 这是全仓级拍板项，见 DEVLOG m408「映射分歧」一节。地基先立着，业务按拍板结果分批接进来。
 *
 * <p>入口类天生一家一份：Fabric 那边是 {@code implements ModInitializer}，这边是 {@code @Mod}，
 * 这正是 m405 判定里"结构性接口"那一类——不抽口，各写各的，body 调共享逻辑。
 */
@Mod(SdzjzNeoForge.MODID)
public class SdzjzNeoForge {

    public static final String MODID = "sdzjz";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("sdzjz-neoforge");

    public SdzjzNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        // 摸一把 Core 层的真数据（同一份 common/ 源码，Fabric 侧也在用）——编得过=跨加载器共用成立
        LOGGER.info("[生电终结者] NeoForge 骨架在岗：Core 层可用（区块移除器周期 {} 拍、熔炉族判定 {}）",
                com.sdzjz.machine.Machines.CHUNK_REMOVER.baseIntervalTicks(),
                com.sdzjz.machine.Machines.smelterFamily("super_smelter"));
    }
}
