package com.mertout.lightguard.utils; // Kendi paket isminize göre düzenleyin

import net.minecraft.server.v1_16_R3.NBTBase;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import net.minecraft.server.v1_16_R3.NBTTagList;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * NBT verilerindeki güvenlik açıklarını tarayan yardımcı sınıf.
 * Özellikle Cyclic Reference ve Excessive Depth exploitlerine karşı korur.
 */
public class NBTChecker {

    // Config'den alınacak değerler için varsayılanlar
    private static final int DEFAULT_MAX_DEPTH = 15;

    /**
     * Verilen NBTTagCompound içinde döngüsel referans veya aşırı derinlik olup olmadığını kontrol eder.
     *
     * @param rootTag Kontrol edilecek ana NBT etiketi.
     * @param maxDepth İzin verilen maksimum derinlik.
     * @return true ise NBT ZARARLIDIR (Exploit tespit edildi), false ise güvenlidir.
     */
    public static boolean isNBTDangerous(NBTTagCompound rootTag, int maxDepth) {
        if (rootTag == null) return false;

        // IdentityHashMap kullanarak Set oluşturuyoruz.
        // Bu, objelerin içeriğine değil, bellekteki adreslerine (referanslarına) bakar.
        // Performans: O(1) lookup.
        Set<NBTBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        try {
            return checkRecursively(rootTag, visited, 0, maxDepth);
        } catch (Exception e) {
            // NBT okurken herhangi bir hata (ClassCastException vs.) oluşursa
            // güvenli tarafta kalarak paketi zararlı kabul ediyoruz.
            return true;
        }
    }

    /**
     * Rekürsif tarama metodu.
     */
    private static boolean checkRecursively(NBTBase current, Set<NBTBase> visited, int depth, int maxDepth) {
        // 1. Derinlik Limiti Kontrolü
        // StackOverflowError oluşmadan önce biz durduruyoruz.
        if (depth > maxDepth) {
            return true; // Zararlı: Aşırı derinlik
        }

        // 2. Cyclic Reference Kontrolü
        // Eğer bu nesne şu anki yolda (path) zaten varsa, bir döngü var demektir.
        if (visited.contains(current)) {
            return true; // Zararlı: Döngü tespit edildi
        }

        // Şu anki nesneyi ziyaret edildi olarak işaretle
        visited.add(current);

        try {
            // 3. Tip Kontrolü ve Alt Elemanlara İniş

            // Durum A: NBTTagCompound (Map benzeri yapı)
            if (current instanceof NBTTagCompound) {
                NBTTagCompound compound = (NBTTagCompound) current;

                // Compound içindeki tüm anahtarları geziyoruz
                for (String key : compound.getKeys()) {
                    NBTBase child = compound.get(key);
                    // Alt eleman için rekürsif çağrı
                    if (checkRecursively(child, visited, depth + 1, maxDepth)) {
                        return true; // Alt daldan zararlı sinyali geldiyse yukarı taşı
                    }
                }
            }
            // Durum B: NBTTagList (Liste yapısı)
            else if (current instanceof NBTTagList) {
                NBTTagList list = (NBTTagList) current;

                // Liste elemanlarını geziyoruz
                for (int i = 0; i < list.size(); i++) {
                    NBTBase child = list.get(i);
                    // Alt eleman için rekürsif çağrı
                    if (checkRecursively(child, visited, depth + 1, maxDepth)) {
                        return true;
                    }
                }
            }
            // Diğer tipler (Int, String, Byte vs.) primitif sarmalayıcı olduğu için
            // içlerinde başka NBT barındırmazlar, dolayısıyla recursion burada biter.

        } finally {
            // 4. Backtracking (Geri İzleme)
            // Bu düğümden çıkıyoruz, artık "bu yolda" değiliz.
            // Bu sayede A->B ve A->C gibi meşru yapıları engellemeyiz.
            visited.remove(current);
        }

        return false; // Temiz
    }
}