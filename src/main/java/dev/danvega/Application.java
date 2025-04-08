package dev.danvega;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Application {

    private static final int NUM_THREADS = 5000; // Increased threads
    private static final long BLOCKING_TIME_MS = 5; // Shorter sleep inside lock
    private static final int CPU_WORK_ITERATIONS = 10000; // Work *outside* lock

    private static final AtomicInteger completedTasks = new AtomicInteger(0);
    private static volatile double busyWorkSink = 0; // To prevent dead code elimination

    // Simple CPU-bound work
    private static void doCpuWork() {
        double result = 0;
        for (int i = 0; i < CPU_WORK_ITERATIONS; i++) {
            result += Math.sin(i) * Math.cos(i);
        }
        // Use the result to prevent optimization
        busyWorkSink += result;
    }

    public static void main(String[] args) throws InterruptedException {
        System.setProperty("jdk.virtualThreadScheduler.parallelism", "1");

        System.out.println("Running with Java version: " + System.getProperty("java.version"));
        System.out.printf("Launching %,d virtual threads...\n", NUM_THREADS);
        System.out.printf("Each thread does CPU work (%,d iterations), then acquires lock and blocks for %d ms.\n",
                CPU_WORK_ITERATIONS, BLOCKING_TIME_MS);

        ThreadFactory factory = Thread.ofVirtual().factory();
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {

            Instant start = Instant.now();

            for (int i = 0; i < NUM_THREADS; i++) {
                final Object lock = new Object(); // the key to this performance test - thank you alexander-shustanov
                executor.submit(() -> {
                    try {
                        // This work can run concurrently if carrier threads are available.
                        doCpuWork();
                        synchronized (lock) {
                            // Short sleep *inside* the lock
                            // JDK 21: Pins carrier, making it unavailable for others' doCpuWork()
                            // JDK 24: Unmounts carrier, allowing it to run others' doCpuWork()
                            Thread.sleep(Duration.ofMillis(BLOCKING_TIME_MS));
                        }
                        completedTasks.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Thread interrupted: " + e.getMessage());
                    } catch (Exception e) {
                        System.err.println("Error in task: " + e.getMessage());
                        e.printStackTrace(); // Print stack trace for unexpected errors
                    }
                });
            }

            executor.shutdown();
            boolean finished = executor.awaitTermination(5, TimeUnit.MINUTES);

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            if (finished) {
                System.out.printf("All %,d tasks completed.\n", completedTasks.get());
                System.out.printf("Total execution time: %.3f seconds\n", duration.toMillis() / 1000.0);
            } else {
                System.err.printf("Executor timed out after 5 minutes. Tasks completed: %,d\n", completedTasks.get());
            }
        }
        System.out.println("----------------------------------------");
        // Print sink value to ensure work wasn't optimized away
        // System.out.println("Sink value (ignore): " + busyWorkSink);
    }
}
