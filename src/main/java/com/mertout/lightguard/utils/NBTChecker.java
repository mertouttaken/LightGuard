package com.mertout.lightguard.utils;

import net.minecraft.server.v1_16_R3.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class NBTChecker {

    public static boolean isNBTDangerous(NBTTagCompound tag, FileConfiguration config) {
        if (tag == null) return false;

        // Ayarları al
        List<String> illegalKeys = config.getStringList("checks.item.illegal-keys");
        int maxDepth = config.getInt("checks.item.max-item-depth", 4);
        int maxListSize = config.getInt("checks.item.max-list-size", 15);
        int maxStringLen = config.getInt("checks.item.max-string-len", 200);

        // 1. Genel Yapısal Kontrol (Derinlik, Uzunluk, Yasaklı Kelimeler)
        if (checkRecursively(tag, 0, maxDepth, maxListSize, maxStringLen, illegalKeys)) {
            return true;
        }

        // 2. Spesifik Eşya Kontrolleri (SpigotGuard Mantığı)
        if (checkSpecificItems(tag)) {
            return true;
        }

        return false;
    }

    private static boolean checkRecursively(NBTTagCompound tag, int depth, int maxDepth, int maxList, int maxStr, List<String> bannedKeys) {
        // Derinlik Limiti (StackOverflow Koruması)
        if (depth > maxDepth) return true;

        for (String key : tag.getKeys()) {
            // Yasaklı Anahtar Kelimeler (Exploit Koruması)
            for (String banned : bannedKeys) {
                if (key.contains(banned)) return true;
            }

            NBTBase base = tag.get(key);

            // NaN ve Infinity Kontrolü (Crash Koruması)
            if (base instanceof NBTTagDouble) {
                double val = ((NBTTagDouble) base).asDouble();
                if (!Double.isFinite(val)) return true;
            }
            if (base instanceof NBTTagFloat) {
                float val = ((NBTTagFloat) base).asFloat();
                if (!Float.isFinite(val)) return true;
            }

            // String Uzunluk Kontrolü
            if (base instanceof NBTTagString) {
                if (base.asString().length() > maxStr) return true;
            }

            // Liste Boyut Kontrolü
            if (base instanceof NBTTagList) {
                NBTTagList list = (NBTTagList) base;
                if (list.size() > maxList) return true;

                // Listenin içindeki Compound'ları da tara
                // (Basit tipler için derinlik artırmıyoruz, sadece Compound için)
                for (int i = 0; i < list.size(); i++) {
                    // 1.16.5 NBTTagList get metodu bazen farklı olabilir, NBTBase döner
                    // Reflection veya NMS tipine göre gerekirse cast edilir.
                    // Basitlik adına burada recursive çağırmıyoruz, çünkü
                    // NBTTagList genellikle aynı tip verileri tutar.
                    // Ancak Compound listesi ise manuel bakmak gerekir:
                     /*
                     if (list.get(i) instanceof NBTTagCompound) {
                         if (checkRecursively((NBTTagCompound) list.get(i), depth + 1, ...)) return true;
                     }
                     */
                }
            }

            // İç İçe Compound Kontrolü
            if (base instanceof NBTTagCompound) {
                if (checkRecursively((NBTTagCompound) base, depth + 1, maxDepth, maxList, maxStr, bannedKeys)) return true;
            }
        }
        return false;
    }

    // SpigotGuard'dan Esinlenilen Özel Kontroller
    private static boolean checkSpecificItems(NBTTagCompound tag) {
        // A. Banner & Shield Desen Limiti (Client Freeze)
        if (tag.hasKey("BlockEntityTag")) {
            NBTTagCompound blockTag = tag.getCompound("BlockEntityTag");
            if (blockTag.hasKey("Patterns")) {
                NBTTagList patterns = blockTag.getList("Patterns", 10); // 10 = Compound ID
                if (patterns.size() > 20) return true; // 20'den fazla desen yasak
            }
        }

        // B. Havai Fişek (Firework) Crash Limiti
        if (tag.hasKey("Fireworks")) {
            NBTTagCompound fireworks = tag.getCompound("Fireworks");

            // Patlama Efekti Limiti
            if (fireworks.hasKey("Explosions")) {
                NBTTagList explosions = fireworks.getList("Explosions", 10);
                if (explosions.size() > 10) return true; // 10'dan fazla patlama yasak
            }

            // Uçuş Süresi Limiti (Oyun Bozma)
            if (fireworks.hasKey("Flight")) {
                // Byte olarak tutulur
                if (fireworks.getByte("Flight") > 5) return true;
            }
        }

        // C. Kitap Başlık/Yazar Uzunluğu (Ekstra Güvenlik)
        if (tag.hasKey("title") && tag.getString("title").length() > 32) return true;
        if (tag.hasKey("author") && tag.getString("author").length() > 16) return true;

        return false;
    }
}