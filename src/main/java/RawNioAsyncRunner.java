import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicLong;

class RawNioAsyncRunner {

    public RawNioAsyncRunner(
            final AsynchronousSocketChannel socket,
            final ByteBuffer writeBuffer,
            final ByteBuffer readBuffer,
            final byte[] messageBytes,
            final AtomicLong counter, final InetSocketAddress remote) {
        this.socket = socket;
        this.writeBuffer = writeBuffer;
        this.readBuffer = readBuffer;
        this.messageBytes = messageBytes;
        this.counter = counter;
        this.remote = remote;
    }

    private final AsynchronousSocketChannel socket;
    private final ByteBuffer writeBuffer;
    private final ByteBuffer readBuffer;
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
        writeBuffer.clear();
        writeBuffer.put(messageBytes);
        writeBuffer.flip();
        socket.write(writeBuffer, null, endWrite);
    }

    private void beginRead() {
        readBuffer.clear();
        socket.read(readBuffer, null, endRead);
    }

    private final CompletionHandler<Integer, Void> endRead = new CompletionHandler<Integer, Void>() {

        @Override
        public void failed(Throwable exc, Void attachment) {
            exc.printStackTrace();
            Utils.closeAndLog(socket);
        }

        @Override
        public void completed(Integer result, Void attachment) {
            counter.addAndGet(result);
            beginRead();
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
            beginWrite();
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
            beginRead();
        }
    };

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AsynchronousSocketChannel socket;
        private ByteBuffer writeBuffer;
        private ByteBuffer readBuffer;
        private byte[] messageBytes;
        private AtomicLong counter;
        private InetSocketAddress remote;

        public Builder withSocket(AsynchronousSocketChannel socket) {
            this.socket = socket;
            return this;
        }

        public Builder withWriteBuffer(ByteBuffer writeBuffer) {
            this.writeBuffer = writeBuffer;
            return this;
        }

        public Builder withReadBuffer(ByteBuffer readBuffer) {
            this.readBuffer = readBuffer;
            return this;
        }

        public Builder withMessageBytes(byte[] messageBytes) {
            this.messageBytes = messageBytes;
            return this;
        }

        public Builder withCounter(AtomicLong counter) {
            this.counter = counter;
            return this;
        }

        public Builder withRemote(InetSocketAddress remote) {
            this.remote = remote;
            return this;
        }

        public RawNioAsyncRunner build() {
            return new RawNioAsyncRunner(socket, writeBuffer, readBuffer, messageBytes, counter, remote);
        }
    }
}
