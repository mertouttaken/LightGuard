package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;

public class SettingsCheck extends Check {

    private final int maxLength;

    public SettingsCheck(LightGuard plugin) {
        super(plugin);
        this.maxLength = plugin.getConfig().getInt("checks.client-settings.max-length", 128);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundClientInformationPacket)) return true;

        try {
            ServerboundClientInformationPacket settingsPacket = (ServerboundClientInformationPacket) packet;
            ClientInformation info = settingsPacket.information();

            if (info != null) {
                String locale = info.language();
                if (locale != null && locale.length() > maxLength) {
                    return false;
                }

                int viewDistance = info.viewDistance();
                if (viewDistance < 0 || viewDistance > 64) {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}