package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;

public class PacketSizeCheck extends Check {

    private static final VarHandle CHAT_MSG;
    private static final VarHandle TAB_TEXT;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            CHAT_MSG = MethodHandles.privateLookupIn(PacketPlayInChat.class, lookup).findVarHandle(PacketPlayInChat.class, "a", String.class);
            TAB_TEXT = MethodHandles.privateLookupIn(PacketPlayInTabComplete.class, lookup).findVarHandle(PacketPlayInTabComplete.class, "b", String.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private final List<String> excludedPackets;
    private final int maxStringLength;
    private final int globalLimit;

    public PacketSizeCheck(LightGuard plugin) {
        super(plugin);
        this.excludedPackets = plugin.getConfig().getStringList("checks.packet-size.excluded-packets");
        this.maxStringLength = plugin.getConfig().getInt("checks.packet-size.max-string-length", 256);
        this.globalLimit = plugin.getConfig().getInt("checks.packet-size.per-packet-limit", 32000);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        String packetName = packet.getClass().getSimpleName();
        if (packet instanceof PacketPlayInCustomPayload || excludedPackets.contains(packetName)) return true;

        if (packet instanceof PacketPlayInChat) {
            String msg = (String) CHAT_MSG.get(packet);
            if (msg != null && msg.length() > maxStringLength) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Chat Message");
                return false;
            }
        }

        if (packet instanceof PacketPlayInTabComplete) {
            String text = (String) TAB_TEXT.get(packet);
            if (text != null && text.length() > 256) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Tab Complete Request");
                return false;
            }
        }

        if (estimateSize(packet) > globalLimit) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Packet Detected");
            return false;
        }
        return true;
    }

    private long estimateSize(Object packet) {
        if (packet instanceof PacketPlayInSetCreativeSlot) return 1500;
        if (packet instanceof PacketPlayInUpdateSign) return 500;
        return 20;
    }
}