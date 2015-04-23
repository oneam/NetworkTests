import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import rx.Observable;
import rx.Subscription;

public class Metrics {

    AtomicLong readBytesSummer = new AtomicLong();
    AtomicInteger readCounter = new AtomicInteger();
    AtomicInteger connectionCounter = new AtomicInteger();

    Object latencySync = new Object();
    int latencyStorageCapacity = 10000;
    long[] latencyStorage = new long[10000];
    int latencyCounter = 0;

    Subscription displaySubscription;
    AtomicLong lastUpdateTimer = new AtomicLong();

    public void start() {
        long now = System.nanoTime();
        lastUpdateTimer.set(now);
        displaySubscription = Observable.timer(0, 1, TimeUnit.SECONDS).subscribe(i -> displayUpdate());
    }

    public void stop() {
        displaySubscription.unsubscribe();
    }

    public void recordConnect() {
        connectionCounter.getAndIncrement();
    }

    public void recordDisconnect() {
        connectionCounter.getAndDecrement();
    }

    public void recordRead(long bytes) {
        readCounter.getAndIncrement();
        readBytesSummer.getAndAdd(bytes);
    }

    public void recordLatency(long latencyInNanos) {
        synchronized (latencySync) {
            if (latencyCounter >= latencyStorageCapacity) {
                latencyStorageCapacity *= 2;
                latencyStorage = Arrays.copyOf(latencyStorage, latencyStorageCapacity);
            }
            latencyStorage[latencyCounter] = latencyInNanos;
            latencyCounter++;
        }
    }

    private void displayUpdate() {
        long now = System.nanoTime();
        long lastUpdateTime = lastUpdateTimer.getAndSet(now);
        long readBytesSum = readBytesSummer.getAndSet(0);
        long readCount = readCounter.getAndSet(0);
        long connections = connectionCounter.get();

        long[] latencies;
        int latencyCount;
        synchronized (latencySync) {
            latencyCount = latencyCounter;
            latencyCounter = 0;

            latencies = latencyStorage;
            latencyStorage = new long[latencyStorageCapacity];
        }
        Arrays.sort(latencies, 0, latencyCount);

        double timeInSeconds = (double) (now - lastUpdateTime) * 1e-9;

        double latencyP50 = latencyCount == 0 ? 0 : (double) latencies[latencyCount / 2] * 1e-6;
        double latencyP90 = latencyCount == 0 ? 0 : (double) latencies[latencyCount * 9 / 10] * 1e-6;
        double readByteRate = timeInSeconds == 0 ? 0 : (double) readBytesSum / timeInSeconds;
        double messageRate = timeInSeconds == 0 ? 0 : (double) latencyCount / timeInSeconds;
        System.out
                .printf(
                        "Connections: %d, Read count: %d, Byte rate: %.2f/s, Message rate: %.0f/s, Latency: P50 %.3fms P90 %.3fms\n",
                        connections,
                        readCount,
                        readByteRate,
                        messageRate,
                        latencyP50,
                        latencyP90);
    }
}
