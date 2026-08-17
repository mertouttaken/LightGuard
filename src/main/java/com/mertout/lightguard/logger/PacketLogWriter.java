package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PacketLogWriter {

    private final File logFile;
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>(10000);
    private volatile boolean running = true;
    private volatile boolean started = false;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public PacketLogWriter(LightGuard plugin) {
        this.logFile = new File(plugin.getDataFolder(), "packet_logs.txt");
    }

    /** Started on first use so a disabled logger neither spawns a thread nor creates the file. */
    private synchronized void startWriterThread() {
        if (started) return;
        started = true;

        Thread writerThread = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                while (running || !logQueue.isEmpty()) {
                    try {
                        String log = logQueue.poll(2, TimeUnit.SECONDS);
                        if (log != null) {
                            String time = LocalTime.now().format(TIME_FORMAT);
                            writer.write("[" + time + "] " + log);
                            writer.newLine();
                            if (logQueue.size() < 10) writer.flush();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        writerThread.setName("LightGuard-Log-Writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public void log(String message) {
        if (!running) return;
        if (!started) startWriterThread();
        logQueue.offer(message);
    }

    public void shutdown() {
        running = false;
    }
}