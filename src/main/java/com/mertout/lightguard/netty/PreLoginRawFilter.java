package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class PreLoginRawFilter extends ChannelInboundHandlerAdapter {

    private final LightGuard plugin;
    private int totalReadBytes = 0;

    private final int preLoginMaxBytes;

    public PreLoginRawFilter(LightGuard plugin) {
        this.plugin = plugin;
        this.preLoginMaxBytes = plugin.getConfig().getInt("checks.raw-packet.pre-login-max-size", 131072);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            int readable = buf.readableBytes();

            InetAddress address = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();

            totalReadBytes += readable;

            if (totalReadBytes > preLoginMaxBytes) {
                String ip = address.getHostAddress();

                int limitInKB = preLoginMaxBytes / 1024;
                plugin.getLogger().warning("Pre-Login Flooder Blocked: " + ip + " (Sent >" + limitInKB + "KB raw data)");

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