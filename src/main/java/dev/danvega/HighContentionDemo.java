package dev.danvega;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HighContentionDemo {

    // --- Configuration ---
    // Increase threads significantly to ensure high contention
    private static final int NUM_THREADS = 15_000;
    // Each thread will attempt to increment the counter this many times
    private static final int OPS_PER_THREAD = 1_000;
    // --- End Configuration ---

    private static final Object lock = new Object();
    // Use AtomicInteger for thread-safe increments, though lock makes it safe
    // Using AtomicInteger here just as a common pattern for shared counters
    private static final AtomicInteger sharedCounter = new AtomicInteger(0);
    private static final AtomicInteger completedThreads = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Running with Java version: " + System.getProperty("java.version"));
        System.out.printf("Launching %,d virtual threads...\n", NUM_THREADS);
        System.out.printf("Each thread performs %,d increments under high lock contention (minimal work inside lock).\n", OPS_PER_THREAD);

        ThreadFactory factory = Thread.ofVirtual().factory();
        // Consider limiting the underlying pool if you have many cores,
        // although default usually works ok. Example: ForkJoinPool commonPool = new ForkJoinPool(16);
        // try (var executor = Executors.newThreadPerTaskExecutor(factory, commonPool)) {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {

            Instant start = Instant.now();

            for (int i = 0; i < NUM_THREADS; i++) {
                executor.submit(() -> {
                    try {
                        for (int op = 0; op < OPS_PER_THREAD; op++) {
                            // --- High Contention Point ---
                            synchronized (lock) {
                                // VERY minimal work inside lock. Focus is acquisition/release.
                                sharedCounter.incrementAndGet();
                                // NO SLEEP - lock held extremely briefly.
                            }
                            // --- End Contention Point ---

                            // Optional: Yielding might encourage more scheduler activity
                            // and potentially expose differences more, but can also add overhead.
                            // if (op % 100 == 0) Thread.yield();
                        }
                        completedThreads.incrementAndGet();
                    } catch (Exception e) {
                        // Catch potential errors within the thread task
                        System.err.println("Error in task for thread " + Thread.currentThread().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }

            executor.shutdown();
            // Increase timeout slightly due to more threads/ops
            boolean finished = executor.awaitTermination(10, TimeUnit.MINUTES);

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            long expectedCount = (long)NUM_THREADS * OPS_PER_THREAD;
            long actualCount = sharedCounter.get();

            if (finished) {
                System.out.printf("All %,d threads completed.\n", completedThreads.get());
                System.out.printf("Expected counter value: %,d\n", expectedCount);
                System.out.printf("Actual counter value:   %,d\n", actualCount);
                if (expectedCount != actualCount) {
                    System.err.println("COUNTER VALUE MISMATCH! Potential issue.");
                }
                System.out.printf("Total execution time: %.3f seconds\n", duration.toMillis() / 1000.0);
            } else {
                System.err.printf("Executor timed out after 10 minutes. Threads completed: %,d / %,d\n", completedThreads.get(), NUM_THREADS);
                System.out.printf("Actual counter value: %,d / %,d\n", actualCount, expectedCount);
            }
        }
        System.out.println("----------------------------------------");
    }
}
