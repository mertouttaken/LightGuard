package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInChat;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.regex.Pattern;

public class ChatSecurityCheck extends Check {

    private static final VarHandle MSG_FIELD;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInChat.class, MethodHandles.lookup());
            MSG_FIELD = lookup.findVarHandle(PacketPlayInChat.class, "a", String.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static final Pattern ZALGO_PATTERN = Pattern.compile("[\\u0300-\\u036F\\u0483-\\u0489\\u1DC0-\\u1DFF\\u20D0-\\u20FF\\uFE20-\\uFE2F]{3,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    private final boolean blockZalgo;

    public ChatSecurityCheck(PlayerData data) {
        super(data, "ChatSecurity", "chat-unicode");
        this.blockZalgo = plugin.getConfig().getBoolean("checks.chat-unicode.block-zalgo", true);
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;
        if (packet instanceof PacketPlayInChat) {
            String message = (String) MSG_FIELD.get(packet);
            if (message == null || message.isEmpty()) return true;

            if (CONTROL_CHARS.matcher(message).find()) {
                flag("Illegal Control Characters", "PacketPlayInChat");
                return false;
            }
            if (blockZalgo && ZALGO_PATTERN.matcher(message).find()) {
                flag("Zalgo Text", "PacketPlayInChat");
                return false;
            }
            if (message.length() > 256) {
                flag("Message too long", "PacketPlayInChat");
                return false;
            }
        }
        return true;
    }
}