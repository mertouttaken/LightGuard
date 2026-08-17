package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import io.netty.buffer.ByteBuf;
import net.minecraft.server.v1_16_R3.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class PacketSizeCheck extends Check {

    private static final VarHandle CHAT_MSG;
    private static final VarHandle TAB_TEXT;
    private static final VarHandle PAYLOAD_DATA;
    private static final VarHandle SIGN_LINES;

    static {
        VarHandle chat = null, tab = null, payload = null, sign = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInChat.class, MethodHandles.lookup());
            for (Field f : PacketPlayInChat.class.getDeclaredFields()) {
                if (f.getType() == String.class) { f.setAccessible(true); chat = lookup.unreflectVarHandle(f); break; }
            }

            MethodHandles.Lookup lookupTab = MethodHandles.privateLookupIn(PacketPlayInTabComplete.class, MethodHandles.lookup());
            for (Field f : PacketPlayInTabComplete.class.getDeclaredFields()) {
                if (f.getType() == String.class) { f.setAccessible(true); tab = lookupTab.unreflectVarHandle(f); break; }
            }

            MethodHandles.Lookup lookupPayload = MethodHandles.privateLookupIn(PacketPlayInCustomPayload.class, MethodHandles.lookup());
            for (Field f : PacketPlayInCustomPayload.class.getDeclaredFields()) {
                if (ByteBuf.class.isAssignableFrom(f.getType())) { f.setAccessible(true); payload = lookupPayload.unreflectVarHandle(f); break; }
            }

            MethodHandles.Lookup lookupSign = MethodHandles.privateLookupIn(PacketPlayInUpdateSign.class, MethodHandles.lookup());
            for (Field f : PacketPlayInUpdateSign.class.getDeclaredFields()) {
                if (f.getType() == String[].class) { f.setAccessible(true); sign = lookupSign.unreflectVarHandle(f); break; }
            }
        } catch (Exception ignored) {}

        CHAT_MSG = chat;
        TAB_TEXT = tab;
        PAYLOAD_DATA = payload;
        SIGN_LINES = sign;
    }

    private final Set<Class<?>> excludedClasses = new HashSet<>();
    private final int maxStringLength;
    private final int globalLimit;

    public PacketSizeCheck(LightGuard plugin) {
        super(plugin);
        this.maxStringLength = plugin.getConfig().getInt("checks.packet-size.max-string-length", 256);
        this.globalLimit = plugin.getConfig().getInt("checks.packet-size.per-packet-limit", 32000);

        for (String packetName : plugin.getConfig().getStringList("checks.packet-size.excluded-packets")) {
            try {
                excludedClasses.add(Class.forName("net.minecraft.server.v1_16_R3." + packetName));
            } catch (ClassNotFoundException ignored) {}
        }
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        Class<?> packetClass = packet.getClass();

        if (excludedClasses.contains(packetClass)) return true;

        try {
            if (packet instanceof PacketPlayInChat && CHAT_MSG != null) {
                String msg = (String) CHAT_MSG.get(packet);
                if (msg != null && msg.length() > maxStringLength) return false;
            }
            else if (packet instanceof PacketPlayInTabComplete && TAB_TEXT != null) {
                String text = (String) TAB_TEXT.get(packet);
                if (text != null && text.length() > 256) return false;
            }
            else if (packet instanceof PacketPlayInCustomPayload && PAYLOAD_DATA != null) {
                // Do not release here: LGPacketInjector owns the buffer lifetime and releases
                // any packet a check rejects. Releasing in both places over-decrements refCnt.
                ByteBuf data = (ByteBuf) PAYLOAD_DATA.get(packet);
                if (data != null && data.readableBytes() > globalLimit) {
                    return false;
                }
            }
            else if (packet instanceof PacketPlayInUpdateSign && SIGN_LINES != null) {
                String[] lines = (String[]) SIGN_LINES.get(packet);
                if (lines != null) {
                    int totalChars = 0;
                    for (String line : lines) {
                        if (line != null) totalChars += line.length();
                    }
                    if (totalChars > 1000) return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return true;
    }
}