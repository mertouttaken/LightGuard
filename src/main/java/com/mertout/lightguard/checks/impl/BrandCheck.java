package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import net.minecraft.server.v1_16_R3.PacketDataSerializer;
import net.minecraft.server.v1_16_R3.MinecraftKey;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class BrandCheck extends Check {

    private static final MethodHandle CHANNEL_GETTER;
    private static final MethodHandle DATA_GETTER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Field fChannel;
            try {
                fChannel = PacketPlayInCustomPayload.class.getDeclaredField("tag");
            } catch (NoSuchFieldException e) {
                fChannel = PacketPlayInCustomPayload.class.getDeclaredField("a");
            }
            fChannel.setAccessible(true);
            CHANNEL_GETTER = lookup.unreflectGetter(fChannel);

            Field fData;
            try {
                fData = PacketPlayInCustomPayload.class.getDeclaredField("data");
            } catch (NoSuchFieldException e) {
                fData = PacketPlayInCustomPayload.class.getDeclaredField("b");
            }
            fData.setAccessible(true);
            DATA_GETTER = lookup.unreflectGetter(fData);

        } catch (Exception e) {
            throw new ExceptionInInitializerError("Failed to initialize BrandCheck: " + e.getMessage());
        }
    }

    private final int maxBrandLength;
    private final boolean blockInvalidChars;

    public BrandCheck(PlayerData data) {
        super(data, "BrandSecurity", "client-brand");

        this.maxBrandLength = plugin.getConfig().getInt("checks.client-brand.max-length", 30);
        this.blockInvalidChars = plugin.getConfig().getBoolean("checks.client-brand.block-invalid-chars", true);
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInCustomPayload) {
            try {
                MinecraftKey key = (MinecraftKey) CHANNEL_GETTER.invoke(packet);

                if (key != null && (key.getKey().equals("brand") || key.toString().endsWith("brand"))) {

                    if (data.isBrandSent()) {
                        flag("Invalid Brand Sequence (Resent Brand)", "BrandCheck");
                        return false;
                    }

                    PacketDataSerializer serializer = (PacketDataSerializer) DATA_GETTER.invoke(packet);
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
                        flag("Malformed Brand Packet Structure", "BrandCheck");
                        return false;
                    } finally {
                        serializer.resetReaderIndex();
                    }

                    if (brand == null || brand.trim().isEmpty()) {
                        flag("Empty or Null Client Brand", "BrandCheck");
                        return false;
                    }

                    if (brand.length() > maxBrandLength) {
                        flag("Oversized Client Brand (" + brand.length() + " > " + maxBrandLength + ")", "BrandCheck");
                        return false;
                    }

                    if (blockInvalidChars && !brand.matches("^[\\x20-\\x7E]+$")) {
                        flag("Illegal Characters in Brand", "BrandCheck");
                        return false;
                    }

                    data.setBrandSent(true);
                }
            } catch (Throwable t) {
                return false;
            }
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