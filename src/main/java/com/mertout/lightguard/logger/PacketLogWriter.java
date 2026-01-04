package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PacketLogWriter {

    private final LightGuard plugin;
    private final File logFile;
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>(10000);
    private volatile boolean running = true;

    public PacketLogWriter(LightGuard plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "packet_logs.txt");
        startWriterThread();
    }

    private void startWriterThread() {
        Thread writerThread = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        String log = logQueue.poll(5, TimeUnit.SECONDS);
                        if (log != null) {
                            writer.write(log);
                            writer.newLine();
                            writer.flush();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        writerThread.setName("LightGuard-LogWriter");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public void log(String message) {
        if (!running) return;

        String timeStamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String formattedMessage = "[" + timeStamp + "] " + message;

        if (!logQueue.offer(formattedMessage)) {
            System.out.println("LightGuard Log Queue Full! Dropping log...");
        }
    }

    public void shutdown() {
        running = false;
    }
}