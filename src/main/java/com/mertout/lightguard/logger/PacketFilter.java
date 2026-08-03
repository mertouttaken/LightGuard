package com.mertout.lightguard.logger;

public class PacketFilter {

    public static boolean isAllowed(Class<?> packetClass, PacketLoggerConfig config) {
        boolean contains = config.getPacketList().contains(packetClass);
        return config.getPacketMode() == PacketLoggerConfig.FilterMode.WHITELIST ? contains : !contains;
    }

    public static boolean isPlayerTracked(String playerName, PacketLoggerConfig config) {
        boolean contains = config.getPlayerList().contains(playerName);
        return config.getPlayerMode() == PacketLoggerConfig.FilterMode.WHITELIST ? contains : !contains;
    }
}