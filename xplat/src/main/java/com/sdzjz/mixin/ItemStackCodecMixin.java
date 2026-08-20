package com.sdzjz.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * m310 原生大堆叠①：放宽 ItemStack 各存档/校验 Codec 里写死的计数钳位 1..99——
 * 不放宽则任何 >99 的栈存档再读回即被钳/判非法（ItemStackProMax 同款靶点，代码自写）。
 * 只改 (1,99) 这一组常量参的调用，其余 rangedInt 原样直传，外科手术式；
 * 无条件放宽是安全的：Codec 只是"允许"，行为端由 getMaxCount/Slot 的配置开关把门。
 * 上界取 2^30：int 物理天花板下，原版合并算术 a+b 不溢出的最大安全值（配置同顶）。
 *
 * m310c 靶点修正（首版 <clinit> 咬空，CI 九号用例 "[1;99]: 1000000" 实锤）：1.21 的
 * CODEC=Codec.lazyInitialized(lambda)，rangedInt 调用在 **lambda 合成方法**里——与被弃的
 * 组件 mixin 同陷阱。改 method="*" 通配（lambda 合成方法也是本类成员方法，全覆盖），
 * 处理器按常量组合过滤保持外科手术式。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackCodecMixin {

    @Redirect(method = "*", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/dynamic/Codecs;rangedInt(II)Lcom/mojang/serialization/Codec;"))
    private static com.mojang.serialization.Codec<Integer> sdzjz$widenCountRange(int min, int max) {
        if (min == 1 && max == 99) max = 1_073_741_823; // 只动计数钳位这一组
        return net.minecraft.util.dynamic.Codecs.rangedInt(min, max);
    }
}
