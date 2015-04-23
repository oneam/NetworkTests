import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NioSyncClient {

    private SocketChannel socket;
    private final CountDownLatch closeLatch = new CountDownLatch(1);
    ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
    ByteBuffer readBuffer = ByteBuffer.allocate(65536);
    private final InetSocketAddress remote;
    private final ClientMode mode;
    private final Metrics metrics;

    public NioSyncClient(InetSocketAddress remote, ClientMode mode, Metrics metrics) {
        this.remote = remote;
        this.mode = mode;
        this.metrics = metrics;
    }

    public void start() throws IOException {
        socket = SocketChannel.open(remote);
        metrics.recordConnect();
        switch (mode) {
        case FULL_DUPLEX:
            new Thread(this::fullDuplexReadLoop).start();
            new Thread(this::fullDuplexWriteLoop).start();
            break;
        case HALF_DUPLEX:
            new Thread(this::halfDuplexLoop).start();
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

    private int read() throws IOException {
        int bytesRead = socket.read(readBuffer);
        metrics.recordRead(bytesRead);
        if (bytesRead <= 0) {
            return bytesRead;
        }

        readBuffer.flip();
        while (readBuffer.remaining() >= 8) {
            long writeTime = readBuffer.getLong();
            long readTime = System.nanoTime();
            long latencyInNanos = readTime - writeTime;
            metrics.recordLatency(latencyInNanos);
        }
        readBuffer.compact();
        return bytesRead;
    }

    private int write() throws IOException {
        long writeTime = System.nanoTime();
        writeBuffer.putLong(writeTime);
        writeBuffer.flip();
        int bytesWritten = socket.write(writeBuffer);
        writeBuffer.compact();
        return bytesWritten;
    }

    private void fullDuplexWriteLoop() {
        try {
            while (true) {
                int bytesWritten = write();
                if (bytesWritten <= 0) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void fullDuplexReadLoop() {
        try {
            while (true) {
                int bytesRead = read();
                if (bytesRead <= 0) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void halfDuplexLoop() {
        try {
            while (true) {
                int bytesWritten = write();
                if (bytesWritten <= 0) {
                    break;
                }

                int bytesRead = read();
                if (bytesRead <= 0) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress remote = new InetSocketAddress("localhost", 4726);
        int numClients = 4;
        Metrics metrics = new Metrics();
        metrics.start();

        List<NioSyncClient> clients = Stream
                .generate(() -> new NioSyncClient(remote, ClientMode.FULL_DUPLEX, metrics))
                .limit(numClients)
                .collect(Collectors.toList());

        for (NioSyncClient client : clients) {
            client.start();
            Thread.sleep(100);
        }

        for (NioSyncClient client : clients) {
            client.waitForClose();
        }
    }

}