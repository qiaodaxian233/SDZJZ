package com.sdzjz.compat;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.math.BigInteger;

/**
 * m229 ProjectEF（github.com/wchiway/ProjectEF，Fabric 版 ProjectE）软兼容：**全反射零编译依赖**
 * （"前置仅 Fabric API"铁律不破）。拉源核实：转化桌 projecte:transmutation_table 是纯 GUI 方块
 * **没有 BlockEntity**，EMC 记在玩家身上（IKnowledgeProvider）——标准物流口（FTA）永远连不上，
 * "卖物品"只能走它的 API：IEMCProxy.INSTANCE.getValue(ItemStack) 估值 →
 * ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(UUID) 拿知识提供者 → setEmc(旧+值)。
 * 方法名全是 ProjectE API 自有名不过混淆映射；参数里的 MC 类用运行时 Class 对象查
 * （intermediary 下与对方 Mojmap 编译产物是同一个运行时类）。API 文档明言离线 UUID 返回的提供者
 * **不可变**（写=白写），故只在所有者在线时卖。任何一步反射失败=整体降级不卖，绝不半残。
 */
public final class ProjectEFCompat {

    private static int state = 0; // 0=未探测 1=可用 -1=不可用（探测一次定终身）
    private static Object emcProxy, txProxy;
    private static Method mGetValue, mKpFor, mGetEmc, mSetEmc, mSyncEmc;

    private ProjectEFCompat() {}

    public static boolean available() {
        if (state == 0) bootstrap();
        return state == 1;
    }

    private static synchronized void bootstrap() {
        if (state != 0) return;
        try {
            if (!com.sdzjz.loader.Env.isModLoaded("projecte")) { state = -1; return; } // m405 环境口
            Class<?> emcCls = Class.forName("moze_intel.projecte.api.proxy.IEMCProxy");
            Class<?> txCls  = Class.forName("moze_intel.projecte.api.proxy.ITransmutationProxy");
            Class<?> kpCls  = Class.forName("moze_intel.projecte.api.capabilities.IKnowledgeProvider");
            emcProxy  = emcCls.getField("INSTANCE").get(null);
            txProxy   = txCls.getField("INSTANCE").get(null);
            mGetValue = emcCls.getMethod("getValue", ItemStack.class);
            mKpFor    = txCls.getMethod("getKnowledgeProviderFor", java.util.UUID.class);
            mGetEmc   = kpCls.getMethod("getEmc");
            mSetEmc   = kpCls.getMethod("setEmc", BigInteger.class);
            mSyncEmc  = kpCls.getMethod("syncEmc", ServerPlayer.class);
            state = 1;
        } catch (Throwable t) { state = -1; } // 对方改版签名变了=整体降级，不抛不炸
    }

    /** 邻块是不是转化桌（按注册 id 判**无需反射**——未装 ProjectEF 时 id 不会命中；客户端也安全）。 */
    public static boolean isTransmutationTable(BlockState s) {
        return "projecte:transmutation_table".equals(BuiltInRegistries.BLOCK.getId(s.getBlock()).toString());
    }

    /** 单件 EMC 估值（0=无价不可卖）。 */
    public static long unitValue(ItemStack one) {
        if (!available()) return 0;
        try { return (Long) mGetValue.invoke(emcProxy, one); } catch (Throwable t) { return 0; }
    }

    /** 给在线所有者记 emc 并即时同步客户端（转化桌界面开着能看见数字涨）。返回是否成功。 */
    public static boolean credit(ServerPlayer owner, long emc) {
        if (!available() || owner == null || emc <= 0) return false;
        try {
            Object kp = mKpFor.invoke(txProxy, owner.getUuid());
            BigInteger cur = (BigInteger) mGetEmc.invoke(kp);
            mSetEmc.invoke(kp, cur.add(BigInteger.valueOf(emc)));
            mSyncEmc.invoke(kp, owner);
            return true;
        } catch (Throwable t) { return false; }
    }
}
