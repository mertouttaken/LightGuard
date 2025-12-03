package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketLogWriter {
    private final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    private final File logFile;
    private volatile boolean running = false;
    private Thread writerThread;

    public PacketLogWriter(LightGuard plugin) {
        File folder = new File(plugin.getDataFolder(), "logs");
        if (!folder.exists()) folder.mkdirs();
        this.logFile = new File(folder, "packet-logs.txt");
        start();
    }

    private void start() {
        running = true;
        writerThread = new Thread(() -> {
            while (running || !logQueue.isEmpty()) {
                try {
                    String log = logQueue.poll();
                    if (log != null) {
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                            writer.write(log);
                            writer.newLine();
                        }
                    } else {
                        Thread.sleep(50);
                    }
                } catch (IOException | InterruptedException e) {
                    if(e instanceof InterruptedException) Thread.currentThread().interrupt();
                }
            }
        });
        writerThread.setName("LightGuard-LogWriter");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public void log(String message) { logQueue.offer(message); }
    public void shutdown() { running = false; }
}