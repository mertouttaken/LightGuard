package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * 🛡️ PRE-LOGIN RAW FILTER
 * Oyuncu henüz sunucuya "Login" olmadan önce gönderilen ham byte trafiğini denetler.
 * Bot saldırılarını ve "Login Crash" denemelerini ağ katmanında durdurur.
 */
public class PreLoginRawFilter extends ChannelInboundHandlerAdapter {

    private final LightGuard plugin;
    private int totalReadBytes = 0;

    // 🛡️ CİLA: Eşik değeri 10KB'tan 128KB'a yükseltildi.
    // Modern protokollerde 10KB meşru girişler için bile çok düşüktür.
    private static final int PRE_LOGIN_MAX_BYTES = 131072; // 128 KB

    public PreLoginRawFilter(LightGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            int readable = buf.readableBytes();

            InetAddress address = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();

            totalReadBytes += readable;

            if (totalReadBytes > PRE_LOGIN_MAX_BYTES) {
                String ip = address.getHostAddress();
                plugin.getLogger().warning("§c[LightGuard] Pre-Login Flooder Blocked: " + ip + " (Sent >128KB raw data)");

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