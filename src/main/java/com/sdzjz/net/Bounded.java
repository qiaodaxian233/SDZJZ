package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * m291 有界解码（外部审计 P1：此前 PacketCodecs.STRING/toList 先全量解码分配、业务层才截断——
 * 业务层 CPU 放大已被 m/前轮防住，但恶意超长包仍能打出解码线程内存分配与瞬时 GC）。
 * 口径：**解码阶段越界=直接 DecoderException 断连拒收**（不读完再截）；编码阶段静默截断
 * （自家客户端字段本就在界内，截断只是防御性自保不崩发送端）。
 * 上限对齐服务端业务层 sanitize（搜索 128 / 单 id 128 / 匹配表 256），双层防御并存。
 * readString(int)/writeString(String,int)=method_10800/method_10788，PacketCodec.of=method_56438（编码器值先行）。
 */
public final class Bounded {

    private Bounded() {}

    /** 有界字符串：解码超长抛 DecoderException（readString(max) 原版语义），编码超长截断。 */
    public static PacketCodec<RegistryByteBuf, String> string(int max) {
        return PacketCodec.of(
                (v, buf) -> buf.writeString(v.length() > max ? v.substring(0, max) : v, max),
                buf -> buf.readString(max));
    }

    /** 有界字符串表：先读声明长度，越界立即拒绝——ArrayList 预分配也按夹紧后的值走，不给分配放大留口。 */
    public static PacketCodec<RegistryByteBuf, List<String>> stringList(int maxEach, int maxItems) {
        return PacketCodec.of((v, buf) -> {
            int n = Math.min(v.size(), maxItems);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                String s = v.get(i);
                buf.writeString(s.length() > maxEach ? s.substring(0, maxEach) : s, maxEach);
            }
        }, buf -> {
            int n = buf.readVarInt();
            if (n < 0 || n > maxItems)
                throw new io.netty.handler.codec.DecoderException("sdzjz: string list too long: " + n + " > " + maxItems);
            List<String> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(buf.readString(maxEach));
            return out;
        });
    }

    /** 有界整数表（VarInt 元素）。 */
    public static PacketCodec<RegistryByteBuf, List<Integer>> intList(int maxItems) {
        return PacketCodec.of((v, buf) -> {
            int n = Math.min(v.size(), maxItems);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeVarInt(v.get(i));
        }, buf -> {
            int n = buf.readVarInt();
            if (n < 0 || n > maxItems)
                throw new io.netty.handler.codec.DecoderException("sdzjz: int list too long: " + n + " > " + maxItems);
            List<Integer> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(buf.readVarInt());
            return out;
        });
    }
}
