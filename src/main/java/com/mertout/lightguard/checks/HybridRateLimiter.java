package com.mertout.lightguard.checks;

public class HybridRateLimiter {

    // --- TEHDİT SKORU VE SOĞUMA ---
    // Volatile: Netty thread'i yazar, ana thread (örn: /lg stats komutu) kilitlenmeden anında okur.
    private volatile double threatScore = 0.0;
    private volatile long lastDecayTime;

    // --- DAİRESEL TAMPON (CIRCULAR BUFFER) İLE BURST TESPİTİ ---
    // Sadece 20 uzunluğunda bir ilkel (primitive) dizi.
    // Sürekli başa sararak çalışır, ASLA RAM'de yeni obje üretmez!
    private final long[] packetTimestamps = new long[20];
    private int index = 0;

    // --- LİMİTLER (İstersen Config'den çekilecek şekilde güncelleyebilirsin) ---
    private static final double MAX_THRESHOLD = 500.0;     // Maksimum tolere edilen puan
    private static final double DECAY_PER_SECOND = 150.0;  // Saniyede silinecek puan

    public HybridRateLimiter() {
        this.lastDecayTime = System.currentTimeMillis();
    }

    /**
     * LGPacketInjector tarafından her paket geldiğinde O(1) hızında çağrılır.
     * @param baseWeight Paketin ağırlığı (Örn: Hareket 1.0, Menü Tıklama 50.0)
     * @return true ise HIZ SINIRI AŞILDI (KICK), false ise TEMİZ.
     */
    public boolean addAndCheck(double baseWeight) {
        long now = System.currentTimeMillis();

        // --------------------------------------------------------
        // 1. SOĞUMA (DECAY) MANTIĞI
        // --------------------------------------------------------
        long timePassed = now - lastDecayTime;
        if (timePassed > 1000) {
            // Ne kadar saniye geçtiyse o kadar puan sil
            double decayAmount = (timePassed / 1000.0) * DECAY_PER_SECOND;
            this.threatScore = Math.max(0.0, this.threatScore - decayAmount);
            this.lastDecayTime = now;
        }

        // --------------------------------------------------------
        // 2. BURST (ANİ PATLAMA) TESPİTİ
        // --------------------------------------------------------
        double finalWeight = baseWeight;

        // Tampondaki en eski paketin zamanını al (Dairesel indeks mantığı)
        long oldestTimestamp = packetTimestamps[(index + 1) % 20];

        // Eğer 20 paket 50 milisaniyeden daha kısa sürede geldiyse (Spam Atağı)
        if (oldestTimestamp > 0 && (now - oldestTimestamp) < 50) {
            finalWeight *= 4.0; // Cezayı 4'e katla! (Örn: 50.0 ağırlık anında 200.0 olur)
        }

        // Yeni zamanı kaydet ve indeksi 1 ilerlet (20'ye ulaşınca 0'a döner)
        packetTimestamps[index] = now;
        index = (index + 1) % 20;

        // --------------------------------------------------------
        // 3. SKOR GÜNCELLEME VE İHLAL KONTROLÜ
        // --------------------------------------------------------
        // Not: Netty'de her oyuncunun ChannelRead'i tek bir EventLoop thread'i tarafından
        // sırayla işlendiği için thread-safe (Atomic) değişkenlere gerek yoktur! Saf hız!
        this.threatScore += finalWeight;

        // Skor 500'ü geçti mi? Geçtiyse fişini çek!
        return this.threatScore > MAX_THRESHOLD;
    }

    /**
     * Adminler "/lg profile <oyuncu>" yazdığında anlık skoru görmek için.
     */
    public double getThreatScore() {
        return threatScore;
    }
}