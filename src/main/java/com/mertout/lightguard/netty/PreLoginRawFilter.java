package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetSocketAddress;

public class PreLoginRawFilter extends ChannelInboundHandlerAdapter {

    private final LightGuard plugin;
    private int totalReadBytes = 0;
    private static final int PRE_LOGIN_MAX_BYTES = 10240;

    public PreLoginRawFilter(LightGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            totalReadBytes += buf.readableBytes();

            if (totalReadBytes > PRE_LOGIN_MAX_BYTES) {
                String ip = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
                plugin.getLogger().warning("§c[LightGuard] Blocked Pre-Login Spam from IP: " + ip);
                ctx.close();
                return;
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}