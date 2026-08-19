package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

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
                buf.release();
                ctx.close();
                return;
            }
        }
        super.channelRead(ctx, msg);
    }
}