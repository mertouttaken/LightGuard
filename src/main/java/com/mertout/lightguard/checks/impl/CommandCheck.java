package com.mertout.lightguard.checks.impl;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInChat;
import java.util.List;

public class CommandCheck extends Check {
    public CommandCheck(PlayerData data) { super(data, "Command"); }
    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.command.enabled")) return true;
        if (packet instanceof PacketPlayInChat) {
            String msg = getMessage((PacketPlayInChat) packet);
            if (msg != null && msg.startsWith("/")) {
                List<String> bl = plugin.getConfig().getStringList("checks.command.blacklist");
                String cmd = msg.split(" ")[0].toLowerCase();
                for(String b : bl) if(cmd.equals(b) || cmd.endsWith(":" + b.replace("/",""))) { flag("Blacklist"); return false; }
                if(plugin.getConfig().getBoolean("checks.command.block-syntax") && (msg.contains("::") || msg.startsWith("//"))) {
                    flag("Syntax"); return false;
                }
            }
        }
        return true;
    }
    private String getMessage(PacketPlayInChat p) {
        try { java.lang.reflect.Field f = p.getClass().getDeclaredField("a"); f.setAccessible(true); return (String) f.get(p); } catch (Exception e) { return null; }
    }
}