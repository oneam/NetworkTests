import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.CountDownLatch;

import rx.subjects.PublishSubject;

public class RxEchoServer {

    public static final int PORT = 4726;
    public static final int BUFFER_SIZE = 65536;

    public static void main(String[] args) throws Exception {
        startServer();
    }

    static void startServer() throws IOException, InterruptedException {
        CountDownLatch quit = new CountDownLatch(1);
        AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
        InetSocketAddress local = new InetSocketAddress(PORT);
        server.bind(local);
        System.out.printf("Rx server listening on %s\n", local);

        PublishSubject<Void> acceptLoop = PublishSubject.create();
        acceptLoop
                .flatMap(_v -> NioRx.<AsynchronousSocketChannel> wrap(server::accept))
                .map(RxEchoServer::onAccept)
                .subscribe(
                        acceptLoop::onNext,
                        e -> {
                            e.printStackTrace();
                            quit.countDown();
                        },
                        () -> quit.countDown());

        acceptLoop.onNext(null);
        quit.await();
    }

    static Void onAccept(AsynchronousSocketChannel socket) {
        SocketAddress remote = Utils.getRemoteAddress(socket);
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        System.out.printf("Client connected from %s\n", remote);
        PublishSubject<Integer> clientLoop = PublishSubject.create();
        clientLoop
                .flatMap(_i -> NioRx.<ByteBuffer, Integer> wrap(socket::read, buffer))
                .takeWhile(RxEchoServer::greaterThanZero)
                .doOnNext(_i -> buffer.flip())
                .flatMap(_i -> NioRx.<ByteBuffer, Integer> wrap(socket::write, buffer))
                .takeWhile(RxEchoServer::greaterThanZero)
                .doOnNext(_i -> buffer.compact())
                .subscribe(clientLoop::onNext, Throwable::printStackTrace);

        clientLoop.onNext(0);
        return null;
    }

    static boolean greaterThanZero(int value) {
        return value > 0;
    }
}
