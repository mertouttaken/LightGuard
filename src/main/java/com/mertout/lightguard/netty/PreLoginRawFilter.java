package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

public class PreLoginRawFilter extends ChannelInboundHandlerAdapter {

    private final int preLoginMaxBytes;
    private final int maxSingleFrameBytes;
    private int totalReadBytes = 0;

    public PreLoginRawFilter(LightGuard plugin) {
        this.preLoginMaxBytes = plugin.getConfig().getInt("checks.raw-packet.pre-login-max-size", 131072);
        this.maxSingleFrameBytes = plugin.getConfig().getInt("checks.raw-packet.max-raw-size", 40960);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            int readable = buf.readableBytes();
            totalReadBytes += readable;

            if (readable > maxSingleFrameBytes || totalReadBytes > preLoginMaxBytes) {
                ReferenceCountUtil.safeRelease(msg);
                ctx.close();
                return;
            }
        }
        super.channelRead(ctx, msg);
    }
}