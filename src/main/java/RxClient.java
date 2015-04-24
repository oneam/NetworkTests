import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import rx.Observable;
import rx.subjects.PublishSubject;

public class RxClient {

    private AsynchronousSocketChannel socket;
    private final CountDownLatch closeLatch = new CountDownLatch(1);
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
    private final ByteBuffer readBuffer = ByteBuffer.allocate(65536);
    private final InetSocketAddress remote;
    private final ClientMode mode;
    private final Metrics metrics;

    public RxClient(InetSocketAddress remote, ClientMode mode, Metrics metrics) {
        this.remote = remote;
        this.mode = mode;
        this.metrics = metrics;
    }

    public void start() throws IOException {
        socket = AsynchronousSocketChannel.open();
        switch (mode) {
        case FULL_DUPLEX:
            startFullDuplex();
            break;
        case HALF_DUPLEX:
            startHalfDuplex();
            break;
        }
    }

    public void close() {
        Utils.closeAndLog(socket);
        metrics.recordDisconnect();
        closeLatch.countDown();
    }

    public void waitForClose() throws InterruptedException {
        closeLatch.await();
    }

    private Observable<Void> connect() {
        return NioRx.<SocketAddress, Void> wrap(socket::connect, remote);
    }

    private Observable<Integer> read() {
        return NioRx.<ByteBuffer, Integer> wrap(socket::read, readBuffer)
                .doOnNext(this::onNextRead)
                .takeWhile(this::greaterThanZero);
    }

    private void onNextRead(int bytesRead) {
        metrics.recordRead(bytesRead);

        readBuffer.flip();
        while (readBuffer.remaining() >= 8) {
            long writeTime = readBuffer.getLong();
            long readTime = System.nanoTime();
            long latencyInNanos = readTime - writeTime;
            metrics.recordLatency(latencyInNanos);
        }
        readBuffer.compact();
    }

    private Observable<Integer> write() {
        long writeTime = System.nanoTime();
        writeBuffer.putLong(writeTime);
        writeBuffer.flip();
        return NioRx.<ByteBuffer, Integer> wrap(socket::write, writeBuffer)
                .doOnNext(this::onNextWrite)
                .takeWhile(this::greaterThanZero);
    }

    private void onNextWrite(int bytesWritten) {
        writeBuffer.compact();
    }

    private void startFullDuplex() {
        PublishSubject<Integer> writeLoop = PublishSubject.create();
        writeLoop
                .flatMap(_i -> write())
                .subscribe(writeLoop::onNext, this::onError);

        PublishSubject<Integer> readLoop = PublishSubject.create();
        readLoop
                .flatMap(_i -> read())
                .subscribe(readLoop::onNext, this::onError);

        connect()
                .subscribe(_v -> {
                    metrics.recordConnect();
                    writeLoop.onNext(0);
                    readLoop.onNext(0);
                }, this::onError);
    }

    private void startHalfDuplex() {
        PublishSubject<Integer> loop = PublishSubject.create();
        loop
                .flatMap(_i -> write())
                .flatMap(_i -> read())
                .subscribe(loop::onNext, this::onError);

        connect()
                .subscribe(_v -> {
                    metrics.recordConnect();
                    loop.onNext(0);
                }, this::onError);
    }

    private boolean greaterThanZero(int value) {
        return value > 0;
    }

    private void onError(Throwable t) {
        t.printStackTrace();
        close();
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress remote = new InetSocketAddress("localhost", 4726);
        int numClients = 4;
        Metrics metrics = new Metrics();
        metrics.start();

        List<RxClient> clients = Stream
                .generate(() -> new RxClient(remote, ClientMode.FULL_DUPLEX, metrics))
                .limit(numClients)
                .collect(Collectors.toList());

        for (RxClient client : clients) {
            client.start();
            Thread.sleep(100);
        }

        for (RxClient client : clients) {
            client.waitForClose();
        }
    }

}
