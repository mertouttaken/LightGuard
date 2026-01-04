package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

public class PacketLogWriter {

    private final LinkedBlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private final File logFile;
    private volatile boolean running = false;
    private Thread writerThread;

    private BufferedWriter writer;

    public PacketLogWriter(LightGuard plugin) {
        File folder = new File(plugin.getDataFolder(), "logs");
        if (!folder.exists()) folder.mkdirs();
        this.logFile = new File(folder, "packet-logs.txt");
        start();
    }

    private void start() {
        running = true;
        writerThread = new Thread(() -> {
            try {
                writer = new BufferedWriter(new FileWriter(logFile, true));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            while (running || !logQueue.isEmpty()) {
                try {
                    String log = running ? logQueue.take() : logQueue.poll();

                    if (log != null) {
                        writer.write(log);
                        writer.newLine();
                        writer.flush();
                    } else if (!running) {
                        break;
                    }
                } catch (InterruptedException e) {
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                if (writer != null) writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        writerThread.setName("LightGuard-LogWriter");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public void log(String message) {
        logQueue.offer(message);
    }

    public void shutdown() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
        }
    }
}