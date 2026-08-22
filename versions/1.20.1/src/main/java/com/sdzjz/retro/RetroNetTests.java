package com.sdzjz.retro;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * m446 刀①行为判官：Net120 地基——有界解码（m291 红线，蓝本=Legacy oversized_panel_view_payload_
 * rejected 用例口径：解码期拒收不分配）与重复注册硬失败（m99/m433 口径）。
 */
public final class RetroNetTests implements FabricGameTest {

    /** 判官专用最小包（write/构造器对偶照 0.92 FabricPacket 官方 javadoc 样例形）。 */
    private record TestPing(int value) implements FabricPacket {
        static final PacketType<TestPing> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "test_ping_m446"), TestPing::new);

        TestPing(FriendlyByteBuf buf) { this(buf.readVarInt()); }

        @Override public void write(FriendlyByteBuf buf) { buf.writeVarInt(value); }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 恶意声明一百万条：readBoundedCount 必须在分配前抛 DecoderException；合法条数原样放行。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void bounded_count_rejects_oversized_declaration(GameTestHelper ctx) {
        FriendlyByteBuf evil = PacketByteBufs.create();
        evil.writeVarInt(1_000_000);
        boolean rejected = false;
        try {
            Net120.readBoundedCount(evil, 4096);
        } catch (io.netty.handler.codec.DecoderException e) {
            rejected = true; // 期望路径：分配前拒收
        }
        ctx.assertTrue(rejected, "超长声明必须在解码期抛 DecoderException 拒收");
        FriendlyByteBuf ok = PacketByteBufs.create();
        ok.writeVarInt(5);
        ctx.assertTrue(Net120.readBoundedCount(ok, 4096) == 5, "合法条数应原样放行=5");
        ctx.succeed();
    }

    /** 有界读串：超长串解码期自抛（readUtf(max) 原版语义，锚点口 readBoundedUtf 过一遍）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void bounded_utf_rejects_overlong_string(GameTestHelper ctx) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf("A".repeat(300));
        boolean rejected = false;
        try {
            Net120.readBoundedUtf(buf, 128);
        } catch (io.netty.handler.codec.DecoderException e) {
            rejected = true;
        }
        ctx.assertTrue(rejected, "超长串必须在解码期抛 DecoderException 拒收");
        FriendlyByteBuf ok = PacketByteBufs.create();
        ok.writeUtf("q");
        ctx.assertTrue("q".equals(Net120.readBoundedUtf(ok, 128)), "合法串应原样读回");
        ctx.succeed();
    }

    /** 注册期硬失败：同一 PacketType 二次 onServer 必须抛 IllegalStateException（首次成功）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void duplicate_payload_registration_hard_fails(GameTestHelper ctx) {
        Net120.onServer(TestPing.TYPE, (packet, player) -> { }); // 首次：应静默成功
        boolean failedHard = false;
        try {
            Net120.onServer(TestPing.TYPE, (packet, player) -> { });
        } catch (IllegalStateException e) {
            failedHard = true; // 期望路径：不静默吞（registerGlobalReceiver 只返 false）
        }
        ctx.assertTrue(failedHard, "重复注册必须硬失败（m99 静默无效教训的注册期版本）");
        ctx.succeed();
    }
}
