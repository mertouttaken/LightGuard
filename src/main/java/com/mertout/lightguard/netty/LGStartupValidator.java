package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.server.v1_16_R3.ServerConnection;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;

import java.lang.reflect.Field;
import java.util.List;

public class LGStartupValidator {

    public static boolean validateAndInject(LightGuard plugin) {
        try {
            ServerConnection serverConnection = ((CraftServer) Bukkit.getServer()).getServer().getServerConnection();
            if (serverConnection == null) {
                throw new IllegalStateException("ServerConnection is not initialized yet!");
            }

            Field channelsField = null;
            for (Field f : ServerConnection.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object val = f.get(serverConnection);
                    if (val instanceof List && !((List<?>) val).isEmpty()) {
                        Object first = ((List<?>) val).get(0);
                        if (first instanceof ChannelFuture) {
                            channelsField = f;
                            break;
                        }
                    }
                }
            }

            if (channelsField == null) {
                throw new NoSuchFieldException("Could not detect Netty listening channels!");
            }

            @SuppressWarnings("unchecked")
            List<ChannelFuture> futures = (List<ChannelFuture>) channelsField.get(serverConnection);

            for (ChannelFuture future : futures) {
                future.channel().pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof Channel) {
                            Channel channel = (Channel) msg;
                            channel.pipeline().addFirst("lg_prelogin_raw", new PreLoginRawFilter(plugin));
                        }
                        super.channelRead(ctx, msg);
                    }
                });
            }

            plugin.getLogger().info("§a[LightGuard] Network Hooks successfully injected into Netty Pipeline.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("§c[LightGuard] CRITICAL STARTUP FAILURE: " + e.getMessage());
            return false;
        }
    }
}