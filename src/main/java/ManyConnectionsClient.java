import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import rx.Observable;

public class ManyConnectionsClient {

    final CountDownLatch quitLatch = new CountDownLatch(1);
    final Random rand = new Random();
    final ByteBuffer readBuffer = ByteBuffer.allocate(65536);
    final ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
    final Metrics metrics;
    final ClientMode mode;
    final InetSocketAddress remote;

    AsynchronousSocketChannel socket;

    public ManyConnectionsClient(InetSocketAddress remote, ClientMode mode, Metrics metrics) {
        this.remote = remote;
        this.metrics = metrics;
        this.mode = mode;
    }

    public void start() throws IOException {
        socket = AsynchronousSocketChannel.open();
        beginConnect();
    }

    public void waitForClose() throws InterruptedException {
        quitLatch.await();
    }

    private void close() {
        Utils.closeAndLog(socket);
        quitLatch.countDown();
        metrics.recordDisconnect();
    }

    private void beginConnect() {
        socket.connect(remote, null, endConnect);
    }

    private void beginWrite() {
        long now = System.nanoTime();
        writeBuffer.clear();
        writeBuffer.putLong(now);
        writeBuffer.flip();
        socket.write(writeBuffer, null, endWrite);
    }

    private void beginRead() {
        socket.read(readBuffer, null, endRead);
    }

    private CompletionHandler<Void, Void> endConnect = new CompletionHandler<Void, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            close();
        }

        @Override
        public void completed(Void result, Void attachment) {
            metrics.recordConnect();

            int delay = rand.nextInt(5000);
            Observable.timer(delay, TimeUnit.MILLISECONDS).subscribe(i -> beginWrite());

            switch (mode) {
            case FULL_DUPLEX:
                beginRead();
                break;
            case HALF_DUPLEX:
                break;
            }
        }
    };

    private CompletionHandler<Integer, Void> endWrite = new CompletionHandler<Integer, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            close();
        }

        @Override
        public void completed(Integer result, Void attachment) {
            switch (mode) {
            case FULL_DUPLEX:
                int delay = rand.nextInt(1000);
                Observable.timer(delay, TimeUnit.MILLISECONDS).subscribe(i -> beginWrite());
                break;
            case HALF_DUPLEX:
                beginRead();
                break;
            }
        }
    };

    private CompletionHandler<Integer, Void> endRead = new CompletionHandler<Integer, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            close();
        }

        @Override
        public void completed(Integer result, Void attachment) {
            long now = System.nanoTime();
            readBuffer.flip();
            long writeTime = readBuffer.getLong();
            readBuffer.compact();

            long latency = now - writeTime;
            metrics.recordRead(result);
            metrics.recordLatency(latency);

            switch (mode) {
            case FULL_DUPLEX:
                beginRead();
                break;
            case HALF_DUPLEX:
                int delay = rand.nextInt(1000);
                Observable.timer(delay, TimeUnit.MILLISECONDS).subscribe(i -> beginWrite());
                break;
            }
        }
    };

    public static void main(String[] args) throws Exception {
        int numClients = 4000;
        InetSocketAddress remote = new InetSocketAddress("localhost", 4726);

        Metrics metrics = new Metrics();
        metrics.start();

        List<ManyConnectionsClient> clients = IntStream
                .range(0, numClients)
                .mapToObj(i -> new ManyConnectionsClient(remote, ClientMode.HALF_DUPLEX, metrics))
                .collect(Collectors.toList());

        for (ManyConnectionsClient client : clients) {
            client.start();
            Thread.sleep(10);
        }

        for (ManyConnectionsClient client : clients) {
            client.waitForClose();
        }
    }

}
