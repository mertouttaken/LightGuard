package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import net.minecraft.server.v1_16_R3.PacketDataSerializer;
import net.minecraft.server.v1_16_R3.MinecraftKey;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class BrandCheck extends Check {

    private static final VarHandle CHANNEL_FIELD;
    private static final VarHandle DATA_FIELD;

    static {
        VarHandle channel = null;
        VarHandle data = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInCustomPayload.class, MethodHandles.lookup());
            for (Field field : PacketPlayInCustomPayload.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == MinecraftKey.class && channel == null) {
                    channel = lookup.unreflectVarHandle(field);
                } else if (field.getType() == PacketDataSerializer.class && data == null) {
                    data = lookup.unreflectVarHandle(field);
                }
            }
        } catch (Exception ignored) {}
        CHANNEL_FIELD = channel;
        DATA_FIELD = data;
    }

    private final int maxBrandLength;
    private final boolean blockInvalidChars;

    public BrandCheck(LightGuard plugin) {
        super(plugin);
        this.maxBrandLength = plugin.getConfig().getInt("checks.client-brand.max-length", 30);
        this.blockInvalidChars = plugin.getConfig().getBoolean("checks.client-brand.block-invalid-chars", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInCustomPayload)) return true;

        try {
            if (CHANNEL_FIELD == null || DATA_FIELD == null) return true;

            MinecraftKey key = (MinecraftKey) CHANNEL_FIELD.get(packet);

            if (key != null && (key.getKey().equals("brand") || key.toString().endsWith("brand"))) {

                if (player.isBrandSent()) {
                    return false;
                }

                PacketDataSerializer serializer = (PacketDataSerializer) DATA_FIELD.get(packet);
                if (serializer == null || serializer.readableBytes() < 1) return true;

                String brand = "";
                serializer.markReaderIndex();
                try {
                    int len = readVarInt(serializer);
                    if (len > 0 && len < 32767 && serializer.readableBytes() >= len) {
                        byte[] bytes = new byte[len];
                        serializer.readBytes(bytes);
                        brand = new String(bytes, StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    return false;
                } finally {
                    serializer.resetReaderIndex();
                }

                if (brand.isEmpty()) {
                    return false;
                }

                if (brand.length() > maxBrandLength) {
                    return false;
                }

                if (blockInvalidChars) {
                    // Regex kullanmak yerine sıfır çöp (Zero-Allocation) karakter kontrolü
                    int brandLen = brand.length();
                    for (int i = 0; i < brandLen; i++) {
                        char c = brand.charAt(i);
                        if (c < 0x20 || c > 0x7E) {
                            return false;
                        }
                    }
                }

                player.setBrandSent(true);
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    private int readVarInt(PacketDataSerializer buf) {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = buf.readByte();
            int value = (read & 127);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new RuntimeException("VarInt too big");
        } while ((read & 128) != 0);
        return result;
    }
}