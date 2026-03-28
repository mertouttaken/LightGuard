package com.mertout.lightguard.logger;

import java.util.Set;

public class PacketFilter {
    public static boolean isAllowed(String packetName, PacketLoggerConfig config) {
        Set<String> set = config.getPacketList();
        boolean contains = set.contains(packetName);
        return config.getPacketMode() == PacketLoggerConfig.FilterMode.WHITELIST ? contains : !contains;
    }

    public static boolean isPlayerTracked(String playerName, PacketLoggerConfig config) {
        Set<String> set = config.getPlayerList();
        boolean contains = set.contains(playerName);
        return config.getPlayerMode() == PacketLoggerConfig.FilterMode.WHITELIST ? contains : !contains;
    }

    public static String getPacketName(Object packet) {
        return packet.getClass().getSimpleName();
    }
}