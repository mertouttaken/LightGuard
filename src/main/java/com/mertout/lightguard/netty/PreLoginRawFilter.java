package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetSocketAddress;

public class PreLoginRawFilter extends ChannelInboundHandlerAdapter {

    private final LightGuard plugin;
    private final int preLoginMaxBytes;
    private int totalReadBytes = 0;

    public PreLoginRawFilter(LightGuard plugin) {
        this.plugin = plugin;
        this.preLoginMaxBytes = plugin.getConfig().getInt("checks.raw-packet.pre-login-max-size", 131072);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            totalReadBytes += buf.readableBytes();

            if (totalReadBytes > preLoginMaxBytes) {
                try {
                    String ip = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
                    int limitInKB = preLoginMaxBytes >> 10;
                    plugin.getLogger().warning("Pre-Login Flooder Blocked: " + ip + " (Sent >" + limitInKB + "KB raw data)");
                } catch (Throwable ignored) {}

                buf.release();
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