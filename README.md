# Virtual Threads without Pinning Demo

This project demonstrates the performance improvements introduced in JDK 24 with [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491).

## Project Overview

The demo shows how JDK 24 can dramatically improve performance in applications that use virtual threads with synchronized blocks that contain blocking operations. The key improvement in JDK 24 is that virtual threads no longer get "pinned" to carrier threads when they enter a synchronized block and perform a blocking operation.

The application:
- Launches 5,000 virtual threads
- Each thread performs CPU-intensive work (10,000 math iterations)
- Each thread then acquires a unique lock and sleeps for 5ms inside the synchronized block
- The JDK's virtual thread scheduler is deliberately limited to 1 platform thread with `-Djdk.virtualThreadScheduler.parallelism=1`

In JDK 21 and earlier, when a virtual thread enters a synchronized block and then blocks (e.g., with `Thread.sleep()`), it stays pinned to its carrier thread, preventing that carrier thread from executing other virtual threads. In JDK 24, the virtual thread unmounts from the carrier thread, allowing the carrier to run other virtual threads.

The key insight (thanks to alexander-shustanov) is that each task must use a *different* lock object to demonstrate the improvement:

```java
// Creating a new lock for each task demonstrates the difference between JDK 21 and 24
final Object lock = new Object(); 
synchronized (lock) {
    // Short sleep *inside* the lock
    // JDK 21: Pins carrier, making it unavailable for others' doCpuWork()
    // JDK 24: Unmounts carrier, allowing it to run others' doCpuWork()
    Thread.sleep(Duration.ofMillis(BLOCKING_TIME_MS));
}
```

## Benchmark Results

```
Running with Java version: 21.0.6
Launching 5,000 virtual threads...
Each thread does CPU work (10,000 iterations), then acquires lock and blocks for 5 ms.
All 5,000 tasks completed.
Total execution time: 31.791 seconds
----------------------------------------
```

```
Running with Java version: 24
Launching 5,000 virtual threads...
Each thread does CPU work (10,000 iterations), then acquires lock and blocks for 5 ms.
All 5,000 tasks completed.
Total execution time: 0.454 seconds
----------------------------------------
```

## Acknowledgments

Thank you to Alexander (alexander-shustanov) who provided a PR to fix this on GitHub: https://github.com/danvega/pinning/pull/2

Alexander identified that each task needed to use a different lock object to demonstrate the performance improvement. With a shared lock, the synchronized block would be executed by only one thread at a time regardless of pinning. By creating a new lock for each task, we can see the dramatic difference JDK 24's virtual thread improvements make.