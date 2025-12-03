package com.mertout.lightguard.utils;

import net.minecraft.server.v1_16_R3.*;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;

public class NBTChecker {

    public static boolean isNBTDangerous(NBTTagCompound tag, FileConfiguration config) {
        if (tag == null) return false;

        List<String> illegalKeys = config.getStringList("checks.item.illegal-keys");
        int maxDepth = config.getInt("checks.item.max-item-depth", 4);
        int maxListSize = config.getInt("checks.item.max-list-size", 15);
        int maxStringLen = config.getInt("checks.item.max-string-len", 200);

        // Derinlik ve Yapı Kontrolü (Recursive)
        return checkRecursively(tag, 0, maxDepth, maxListSize, maxStringLen, illegalKeys);
    }

    private static boolean checkRecursively(NBTTagCompound tag, int depth, int maxDepth, int maxList, int maxStr, List<String> bannedKeys) {
        // ➤ FIX: Derinlik Limiti (Test 21, 31 - Recursive NBT)
        if (depth > maxDepth) return true;

        for (String key : tag.getKeys()) {
            // Yasaklı Kelimeler
            for (String banned : bannedKeys) if (key.contains(banned)) return true;

            NBTBase base = tag.get(key);

            // ➤ FIX: NaN / Infinity Kontrolü (Test 17 - Attribute NaN)
            if (base instanceof NBTTagDouble) {
                double val = ((NBTTagDouble) base).asDouble();
                if (!Double.isFinite(val)) return true; // NaN veya Sonsuz ise yasak
            }
            if (base instanceof NBTTagFloat) {
                float val = ((NBTTagFloat) base).asFloat();
                if (!Float.isFinite(val)) return true;
            }

            // String ve Liste Kontrolleri
            if (base instanceof NBTTagString) {
                if (base.asString().length() > maxStr) return true;
            }
            if (base instanceof NBTTagList) {
                if (((NBTTagList) base).size() > maxList) return true;
            }

            // Recursive (İç içe) Kontrol
            if (base instanceof NBTTagCompound) {
                if (checkRecursively((NBTTagCompound) base, depth + 1, maxDepth, maxList, maxStr, bannedKeys)) return true;
            }
        }
        return false;
    }
}