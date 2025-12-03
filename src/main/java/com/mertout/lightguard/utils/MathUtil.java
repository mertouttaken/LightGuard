package com.mertout.lightguard.utils;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MathUtil {

    /**
     * Shannon Entropisi Hesabı.
     * Düşük değer (0.0 - 2.0) = Tekrarlayan, Robotik işlem.
     * Yüksek değer (> 3.0) = Rastgele, İnsani işlem.
     */
    public static double calculateEntropy(List<String> sequence) {
        if (sequence == null || sequence.isEmpty()) return 0.0;

        Map<String, Integer> frequency = new HashMap<>();
        for (String s : sequence) {
            frequency.put(s, frequency.getOrDefault(s, 0) + 1);
        }

        double entropy = 0.0;
        int size = sequence.size();

        for (int count : frequency.values()) {
            double prob = (double) count / size;
            entropy -= prob * (Math.log(prob) / Math.log(2));
        }

        return entropy;
    }
}