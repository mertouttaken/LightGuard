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

    private static final String SERVER_HANDLER_NAME = "lg_server_validator";

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
                if (future.channel().pipeline().get(SERVER_HANDLER_NAME) != null) {
                    future.channel().pipeline().remove(SERVER_HANDLER_NAME);
                }

                future.channel().pipeline().addFirst(SERVER_HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof Channel) {
                            Channel channel = (Channel) msg;
                            if (channel.pipeline().get("lg_prelogin_raw") == null) {
                                channel.pipeline().addFirst("lg_prelogin_raw", new PreLoginRawFilter(plugin));
                            }
                        }
                        super.channelRead(ctx, msg);
                    }
                });
            }

            plugin.getLogger().info("Network Hooks successfully injected into Netty Pipeline.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("CRITICAL STARTUP FAILURE: " + e.getMessage());
            return false;
        }
    }

    public static void uninject() {
        try {
            ServerConnection serverConnection = ((CraftServer) Bukkit.getServer()).getServer().getServerConnection();
            if (serverConnection == null) return;

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

            if (channelsField != null) {
                @SuppressWarnings("unchecked")
                List<ChannelFuture> futures = (List<ChannelFuture>) channelsField.get(serverConnection);

                for (ChannelFuture future : futures) {
                    if (future.channel().pipeline().get(SERVER_HANDLER_NAME) != null) {
                        future.channel().pipeline().remove(SERVER_HANDLER_NAME);
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}