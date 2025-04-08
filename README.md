# Virtual Threads without Pinning

I am trying to show the performance benefits of the new feature in JDK 24

[JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)

I understand there is some logic to benchmarking, but I can't seem to figure this one out. I have tried
everything from increasing the number of threads to limiting the number of platform threads `-Djdk.virtualThreadScheduler.parallelism=1`
and no matter what I do I can show the performance benefits of JDK 24 vs 21. 

The limited number of carrier threads (1) should pin the virtual thread to the carrier thread and make it 
unavailable to do any other work making this application run much slower on JDK 21. 

```java
// This work can run concurrently if carrier threads are available.
doCpuWork();
synchronized (lock) {
    // Short sleep *inside* the lock
    // JDK 21: Pins carrier, making it unavailable for others' doCpuWork()
    // JDK 24: Unmounts carrier, allowing it to run others' doCpuWork()
    Thread.sleep(Duration.ofMillis(BLOCKING_TIME_MS));
}
```

These are the results I am getting

```
Running with Java version: 21.0.6
Launching 5,000 virtual threads...
Each thread does CPU work (10,000 iterations), then acquires lock and blocks for 5 ms.
All 5,000 tasks completed.
Total execution time: 32.334 seconds
----------------------------------------
```

```
Running with Java version: 24
Launching 5,000 virtual threads...
Each thread does CPU work (10,000 iterations), then acquires lock and blocks for 5 ms.
All 5,000 tasks completed.
Total execution time: 31.592 seconds
----------------------------------------
```

## Update

Thank you to Alexander (alexander-shustanov) who provided a PR to fix this on GitHub


https://github.com/danvega/pinning/pull/2

Hi! Thank you for your highlight. But it sounds like I found a mistake in your code.

Take another look at your synchronized block. It captures the monitor of the lock object. synchronized guarantees that the code inside the block will be executed by only one thread at a time, from start to finish.

Therefore, regardless of pinning, each Thread.sleep() is executed exclusively.

However, if you capture a different monitor each time (i.e., use a different lock object per task), you'll notice a significant difference — the performance can improve by an order of magnitude.

Please, look at my fixes there:
https://github.com/spring-aio/java24-pinning/blob/master/src/main/java/dev/danvega/Application.java#L43

I create new lock every time. So, for java 21, it works the same as in your code, due to carrier thread pinning (34.688 seconds for me). But for java 24 there is no pinning and the code is executed immediately (0.633 seconds for me).