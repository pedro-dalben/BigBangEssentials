package com.zerog.bigbangessentials.economy;

import com.zerog.bigbangessentials.util.DebugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous transaction logger for economy operations.
 * Batches log writes off the main thread to avoid disk stalls during frequent balance changes.
 */
public final class EconomyTransactionLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(EconomyTransactionLogger.class);
    private static final Path LOG_FILE = Paths.get("logs", "bigbangessentials", "transactions.log");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LinkedBlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);
    private static final Thread WORKER = new Thread(EconomyTransactionLogger::runWriter, "BBE-EconomyTxLogger");

    static {
        WORKER.setDaemon(true);
        WORKER.start();
    }

    private EconomyTransactionLogger() {
    }

    public static void log(String type, String sender, String receiver, String amount, String reason) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String entry = String.format("[%s] %s | %s -> %s | %s | %s%n", timestamp, type, sender, receiver, amount, reason);

        if (!RUNNING.get()) {
            writeEntries(List.of(entry));
            return;
        }

        QUEUE.offer(entry);
    }

    public static void shutdown() {
        if (!RUNNING.compareAndSet(true, false)) {
            return;
        }

        WORKER.interrupt();
        try {
            WORKER.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        flushRemaining();
    }

    private static void runWriter() {
        List<String> batch = new ArrayList<>(128);

        while (RUNNING.get() || !QUEUE.isEmpty()) {
            try {
                String first = QUEUE.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                batch.add(first);
                QUEUE.drainTo(batch, 127);
                writeEntries(batch);
                batch.clear();
            } catch (InterruptedException e) {
                if (!RUNNING.get()) {
                    break;
                }
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void flushRemaining() {
        List<String> batch = new ArrayList<>(128);
        QUEUE.drainTo(batch);
        if (!batch.isEmpty()) {
            writeEntries(batch);
        }
    }

    private static synchronized void writeEntries(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        try {
            Path parent = LOG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(
                LOG_FILE,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            )) {
                for (String entry : entries) {
                    writer.write(entry);
                }
            }
        } catch (IOException e) {
            DebugUtil.debugStackTrace(e);
            LOGGER.warn("Failed to write {} economy transaction log entr{}: {}",
                entries.size(),
                entries.size() == 1 ? "y" : "ies",
                e.getMessage());
        }
    }
}
