import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicLong;

class RawNioAsyncRunner {

    public RawNioAsyncRunner(final AsynchronousSocketChannel socket, final ByteBuffer buffer,
            final byte[] messageBytes,
            final AtomicLong counter, final InetSocketAddress remote) {
        this.socket = socket;
        this.buffer = buffer;
        this.messageBytes = messageBytes;
        this.counter = counter;
        this.remote = remote;
    }

    private final AsynchronousSocketChannel socket;
    private final ByteBuffer buffer;
    private final byte[] messageBytes;
    private final AtomicLong counter;
    private final InetSocketAddress remote;

    public void start() {
        beginConnect();
    }

    private void beginConnect() {
        socket.connect(remote, null, endConnect);
    }

    private void beginWrite() {
        buffer.clear();
        buffer.put(messageBytes);
        buffer.flip();
        socket.write(buffer, null, endWrite);
    }

    private void beginRead() {
        buffer.clear();
        socket.read(buffer, null, endRead);
    }

    private final CompletionHandler<Integer, Void> endRead = new CompletionHandler<Integer, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            Utils.closeAndLog(socket);
        }

        @Override
        public void completed(Integer result, Void attachment) {
            counter.incrementAndGet();
            beginWrite();
        }
    };

    private final CompletionHandler<Integer, Void> endWrite = new CompletionHandler<Integer, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            Utils.closeAndLog(socket);
        }

        @Override
        public void completed(Integer result, Void attachment) {
            beginRead();
        }
    };

    private final CompletionHandler<Void, Void> endConnect = new CompletionHandler<Void, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            Utils.closeAndLog(socket);
        }

        @Override
        public void completed(Void result, Void attachment) {
            beginWrite();
        }
    };
}
