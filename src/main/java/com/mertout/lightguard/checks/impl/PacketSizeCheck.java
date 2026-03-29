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
    private static final VarHandle PAYLOAD_DATA;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            CHAT_MSG = MethodHandles.privateLookupIn(PacketPlayInChat.class, lookup).findVarHandle(PacketPlayInChat.class, "a", String.class);
            TAB_TEXT = MethodHandles.privateLookupIn(PacketPlayInTabComplete.class, lookup).findVarHandle(PacketPlayInTabComplete.class, "b", String.class);
            PAYLOAD_DATA = MethodHandles.privateLookupIn(PacketPlayInCustomPayload.class, lookup).findVarHandle(PacketPlayInCustomPayload.class, "b", PacketDataSerializer.class);
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
        if (excludedPackets.contains(packetName)) return true;

        if (packet instanceof PacketPlayInChat) {
            String msg = (String) CHAT_MSG.get(packet);
            if (msg != null && msg.length() > maxStringLength) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Chat Message");
                return false;
            }
            return true;
        }

        if (packet instanceof PacketPlayInTabComplete) {
            String text = (String) TAB_TEXT.get(packet);
            if (text != null && text.length() > 256) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Tab Complete Request");
                return false;
            }
            return true;
        }

        if (packet instanceof PacketPlayInCustomPayload) {
            PacketDataSerializer data = (PacketDataSerializer) PAYLOAD_DATA.get(packet);
            if (data != null && data.readableBytes() > globalLimit) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Custom Payload (" + data.readableBytes() + " bytes)");
                return false;
            }
        }

        if (packet instanceof PacketPlayInUpdateSign) {
            PacketPlayInUpdateSign signPacket = (PacketPlayInUpdateSign) packet;
            int totalSignChars = 0;
            for (String line : signPacket.c()) {
                if (line != null) totalSignChars += line.length();
            }
            if (totalSignChars > 300) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Sign Data");
                return false;
            }
        }

        return true;
    }
}