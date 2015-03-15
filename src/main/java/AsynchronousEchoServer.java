import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.CountDownLatch;

import rx.subjects.PublishSubject;

public class AsynchronousEchoServer {

    public static final int PORT = 4726;
    public static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) throws Exception {
        startAsyncServer();
    }

    static void startAsyncServer() throws IOException, InterruptedException {
        CountDownLatch quit = new CountDownLatch(1);
        AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
        PublishSubject<Void> loop = PublishSubject.create();
        InetSocketAddress local = new InetSocketAddress(PORT);
        server.bind(local);
        System.out.printf("Async serv listening on %s\n", local);

        loop
                .flatMap(_void -> NioRx.<AsynchronousSocketChannel> wrap(server::accept))
                .subscribe(
                        client -> {
                            handleAsyncConnection(client);
                            loop.onNext(null);
                        },
                        e -> e.printStackTrace(),
                        () -> quit.countDown());

        loop.onNext(null);
        quit.await();
    }

    static void handleAsyncConnection(AsynchronousSocketChannel socket) {
        try {
            System.out.printf("Client connected from %s\n", socket.getRemoteAddress());
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        PublishSubject<Void> loop = PublishSubject.create();
        loop
                .flatMap(_void -> {
                    buffer.clear();
                    return NioRx.<ByteBuffer, Integer> wrap(socket::read, buffer);
                })
                .flatMap(bytesRead -> {
                    buffer.flip();
                    return NioRx.<ByteBuffer, Integer> wrap(socket::write, buffer);
                })
                .doOnTerminate(() -> Utils.closeAndLog(socket))
                .subscribe(_bytesWritten -> loop.onNext(null), e -> e.printStackTrace());

        loop.onNext(null);
    }
}
