package com.sdzjz.legacy;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.DataOutputStream;
import java.io.OutputStream;

/** m365 Legacy 调试工具：nbtSize 自 CoreProfiler 原文平移（Profiler 升 Common，NBT 尺子归代际侧）。 */
public final class LegacyDebugUtil {
    private LegacyDebugUtil() { }

    public static long nbtSize(NbtCompound nbt) {
        final long[] n = {0};
        try (DataOutputStream d = new DataOutputStream(new OutputStream() {
            @Override public void write(int b) { n[0]++; }
            @Override public void write(byte[] b, int off, int len) { n[0] += len; }
        })) {
            NbtIo.write(nbt, d);
        } catch (Exception ignored) {}
        return n[0];
    }
}
