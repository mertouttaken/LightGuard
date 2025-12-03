package com.mertout.lightguard.logger;
import java.util.List;
public class PacketFilter {
    public static boolean isAllowed(String packetName, PacketLoggerConfig config) {
        List<String> list = config.getPacketList();
        boolean contains = list.stream().anyMatch(packetName::equalsIgnoreCase);
        return config.getPacketMode() == PacketLoggerConfig.FilterMode.WHITELIST ? contains : !contains;
    }
    public static String getPacketName(Object packet) {
        return packet.getClass().getSimpleName();
    }
}