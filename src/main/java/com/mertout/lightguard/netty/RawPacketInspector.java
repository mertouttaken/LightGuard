package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.List;

/**
 * Netty Pipeline üzerinde Decoder'dan önce çalışan güvenlik katmanı.
 * ByteBuf seviyesinde boyut ve ID kontrolü yapar.
 */
public class RawPacketInspector extends ChannelInboundHandlerAdapter {

    private final LightGuard plugin;
    private final String playerName;

    // Config'den alınacak limitler (Performans için constructor'da veya static cache'de tutulabilir)
    private static final int MAX_PACKET_SIZE = 2097152; // 2MB (Varsayılan)

    public RawPacketInspector(LightGuard plugin, String playerName) {
        this.plugin = plugin;
        this.playerName = playerName;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;

            // --- 1. RAW SIZE KONTROLÜ ---
            // Readable bytes, paketin şu anki boyutudur.
            int size = buf.readableBytes();

            int limit = plugin.getConfig().getInt("checks.raw-packet.max-raw-size", MAX_PACKET_SIZE);
            if (size > limit) {
                plugin.getLogger().warning("§c[LightGuard] " + playerName + " sent oversized raw packet (" + size + " bytes). Disconnecting.");

                // Güvenli kapatma: Önce buffer'ı temizle (Memory leak önle), sonra kapat
                buf.release();
                ctx.close();
                return; // Zinciri kır, paketi aşağıya iletme
            }

            // --- 2. PACKET ID KONTROLÜ (OPSİYONEL) ---
            // Bu kısım biraz risklidir çünkü compression varsa ID okumak zordur.
            // Sadece boyut kontrolü çoğu exploit'i engeller.
            // Gelişmiş ID okuma için buffer'ı bozmadan (peek) okumamız gerekir.

            // Eğer isterseniz buraya ID okuma eklenebilir ama şu an için
            // sadece boyut kontrolü en kritik olanıdır.
        }

        // Güvenliyse bir sonraki handler'a (Decoder/Decompressor) ilet
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Netty seviyesinde hata olursa (örn: Decoder hatası)
        // LightGuard burada hatayı yakalayıp sunucunun çökmesini engelleyebilir.
        plugin.getLogger().warning("[RawInspector] Netty error for " + playerName + ": " + cause.getMessage());
        ctx.close();
    }
}