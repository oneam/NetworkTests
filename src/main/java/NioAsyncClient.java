import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class NioAsyncClient {

    public NioAsyncClient(InetSocketAddress remote, ClientMode mode, Metrics metrics) {
        this.metrics = metrics;
        this.mode = mode;
        this.remote = remote;
    }

    private AsynchronousSocketChannel socket;
    private final CountDownLatch quitLatch = new CountDownLatch(1);
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
    private final ByteBuffer readBuffer = ByteBuffer.allocate(65536);
    private final Metrics metrics;
    private final ClientMode mode;
    private final InetSocketAddress remote;

    public void start() throws IOException {
        socket = AsynchronousSocketChannel.open();
        socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
        beginConnect();
    }

    public void close() {
        Utils.closeAndLog(socket);
        metrics.recordDisconnect();
        quitLatch.countDown();
    }

    public void waitForClose() throws InterruptedException {
        quitLatch.await();
    }

    private void beginConnect() {
        socket.connect(remote, null, endConnect);
    }

    private void beginWrite() {
        long writeTime = System.nanoTime();
        writeBuffer.putLong(writeTime);
        writeBuffer.flip();
        socket.write(writeBuffer, null, endWrite);
    }

    private void beginRead() {
        socket.read(readBuffer, null, endRead);
    }

    private final CompletionHandler<Void, Void> endConnect = new CompletionHandler<Void, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            close();
        }

        @Override
        public void completed(Void result, Void attachment) {
            metrics.recordConnect();
            beginWrite();
            switch (mode) {
            case FULL_DUPLEX:
                beginRead();
                break;
            case HALF_DUPLEX:
                break;
            }
        }
    };

    private final CompletionHandler<Integer, Void> endWrite = new CompletionHandler<Integer, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            close();
        }

        @Override
        public void completed(Integer result, Void attachment) {
            writeBuffer.compact();
            if (result <= 0) {
                return;
            }

            switch (mode) {
            case FULL_DUPLEX:
                beginWrite();
                break;
            case HALF_DUPLEX:
                beginRead();
                break;
            }
        }
    };

    private final CompletionHandler<Integer, Void> endRead = new CompletionHandler<Integer, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            close();
        }

        @Override
        public void completed(Integer result, Void attachment) {
            metrics.recordRead(result);
            if (result <= 0) {
                return;
            }

            readBuffer.flip();
            while (readBuffer.remaining() >= 8) {
                long writeTime = readBuffer.getLong();
                long readTime = System.nanoTime();
                long latencyInNanos = readTime - writeTime;
                metrics.recordLatency(latencyInNanos);
            }
            readBuffer.compact();

            switch (mode) {
            case FULL_DUPLEX:
                beginRead();
                break;
            case HALF_DUPLEX:
                beginWrite();
                break;
            }
        }
    };

    static public void main(String[] args) throws Exception {
        String serverHostname = System.getProperty("server", "localhost");
        int numClients = Integer.parseInt(System.getProperty("numClients", "4"));
        String clientModeString = System.getProperty("clientMode", "full");
        ClientMode clientMode = clientModeString.equals("full") ? ClientMode.FULL_DUPLEX : ClientMode.HALF_DUPLEX;

        InetSocketAddress remote = new InetSocketAddress(serverHostname, 4726);
        Metrics metrics = new Metrics();
        metrics.start();

        System.out.format("Connecting to %s with %d clients using %s\n", remote, numClients, clientMode);

        List<NioAsyncClient> clients = Stream
                .generate(() -> new NioAsyncClient(remote, clientMode, metrics))
                .limit(numClients)
                .collect(Collectors.toList());

        for (NioAsyncClient client : clients) {
            client.start();
            Thread.sleep(100);
        }

        for (NioAsyncClient client : clients) {
            client.waitForClose();
        }
    }
}
