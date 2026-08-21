package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Validates the channel a custom payload arrives on.
 *
 * <p>Payload <em>size</em> is deliberately not checked here. This handler is installed after the
 * pipeline's decoder, so the packet has already been decoded and the encoded length is gone; vanilla's
 * own codec caps a custom payload at 32767 bytes and kills the connection before this point. The
 * raw byte ceiling for pre-login traffic is enforced separately by
 * {@link com.mertout.lightguard.netty.PreLoginRawFilter}.
 */
public class PayloadCheck extends Check {

    private static final int MAX_CHANNEL_LENGTH = 128;

    private final Set<String> blockedChannels = new HashSet<>();

    public PayloadCheck(LightGuard plugin) {
        super(plugin);
        for (String channel : plugin.getConfig().getStringList("checks.payload.blocked-channels")) {
            blockedChannels.add(channel.toLowerCase(Locale.ROOT));
        }
        // A client claiming to speak BungeeCord's channel is trying to forge proxy messages.
        blockedChannels.add("bungeecord");
        blockedChannels.add("bungeecord:main");
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundCustomPayloadPacket)) return true;

        try {
            CustomPacketPayload payload = ((ServerboundCustomPayloadPacket) packet).payload();
            ResourceLocation id = payload.type().id();
            if (id == null) return false;

            String channel = id.toString().toLowerCase(Locale.ROOT);

            if (channel.isEmpty() || channel.length() > MAX_CHANNEL_LENGTH) return false;

            for (int i = 0; i < channel.length(); i++) {
                if (Character.isISOControl(channel.charAt(i))) return false;
            }

            // A: PayloadCheckItemInHand - book payloads require holding a book
            if ((channel.contains("bedit") || channel.contains("bsign")) && !player.isHoldingBook()) {
                return false;
            }

            // A: PayloadCheckCoordinates / privilege check for command payloads
            if ((channel.contains("adv_cmd") || channel.contains("auto_cmd") || channel.contains("advcdm") || channel.contains("autocmd"))
                    && (!player.isCreativeOrSpectator() || !player.isAdmin())) {
                return false;
            }

            return !blockedChannels.contains(channel);

        } catch (Throwable t) {
            return false;
        }
    }
}
