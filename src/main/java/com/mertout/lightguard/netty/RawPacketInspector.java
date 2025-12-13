package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class RawPacketInspector extends ChannelInboundHandlerAdapter {

    private final LightGuard plugin;
    private final String playerName;

    private static final int MAX_PACKET_SIZE = 2097152;

    public RawPacketInspector(LightGuard plugin, String playerName) {
        this.plugin = plugin;
        this.playerName = playerName;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            int size = buf.readableBytes();

            int limit = plugin.getConfig().getInt("checks.raw-packet.max-raw-size", MAX_PACKET_SIZE);
            if (size > limit) {
                plugin.getLogger().warning("§c[LightGuard] " + playerName + " sent oversized raw packet (" + size + " bytes).");

                ctx.close();
                return;
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        plugin.getLogger().warning("[RawInspector] Netty error for " + playerName + ": " + cause.getMessage());
        ctx.close();
    }
}