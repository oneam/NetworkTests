import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.CountDownLatch;

import rx.Subscriber;

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
        System.out.printf("Rx serv listening on %s\n", local);

        NioRx.accepter(server)
                .subscribe(
                        RxEchoServer::handleAsyncConnection,
                        e -> {
                            e.printStackTrace();
                            quit.countDown();
                        },
                        () -> quit.countDown());

        quit.await();
    }

    static void handleAsyncConnection(AsynchronousSocketChannel socket) {
        SocketAddress remote = Utils.getRemoteAddress(socket);
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        System.out.printf("Client connected from %s\n", remote);
        NioRx.reader(socket, buffer)
                .subscribe(new Subscriber<ByteBuffer>() {

                    @Override
                    public void onStart() {
                        request(1);
                    }

                    @Override
                    public void onCompleted() {
                        System.out.printf("%s disconnected\n", remote);
                        Utils.closeAndLog(socket);
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(ByteBuffer t) {
                        t.flip();
                        NioRx.<ByteBuffer, Integer> wrap(socket::write, buffer)
                                .subscribe(i -> {
                                    t.clear();
                                    request(1);
                                }, Throwable::printStackTrace);
                    }
                });
    }
}
