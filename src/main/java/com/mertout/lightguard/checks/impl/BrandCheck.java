package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class BrandCheck extends Check {

    private final int maxBrandLength;
    private final boolean blockInvalidChars;

    public BrandCheck(LightGuard plugin) {
        super(plugin);
        this.maxBrandLength = plugin.getConfig().getInt("checks.client-brand.max-length", 30);
        this.blockInvalidChars = plugin.getConfig().getBoolean("checks.client-brand.block-invalid-chars", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundCustomPayloadPacket)) return true;

        try {
            ServerboundCustomPayloadPacket payloadPacket = (ServerboundCustomPayloadPacket) packet;
            CustomPacketPayload payload = payloadPacket.payload();

            if (payload instanceof BrandPayload) {
                if (player.isBrandSent()) {
                    return false;
                }

                String brand = ((BrandPayload) payload).brand();

                if (brand == null || brand.isEmpty()) {
                    return false;
                }

                if (brand.length() > maxBrandLength) {
                    return false;
                }

                if (blockInvalidChars) {
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
}