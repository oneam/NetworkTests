import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.CountDownLatch;

import rx.Subscriber;

public class AsynchronousEchoServer {

    public static final int PORT = 4726;
    public static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) throws Exception {
        startAsyncServer();
    }

    static void startAsyncServer() throws IOException, InterruptedException {
        CountDownLatch quit = new CountDownLatch(1);
        AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
        InetSocketAddress local = new InetSocketAddress(PORT);
        server.bind(local);
        System.out.printf("Async serv listening on %s\n", local);

        NioRx.accepter(server)
                .subscribe(
                        AsynchronousEchoServer::handleAsyncConnection,
                        e -> {
                            e.printStackTrace();
                            quit.countDown();
                        },
                        () -> quit.countDown());

        quit.await();
    }

    static void handleAsyncConnection(AsynchronousSocketChannel socket) {
        SocketAddress remote = Utils.getRemoteAddress(socket);

        System.out.printf("Client connected from %s\n", remote);
        NioRx.reader(socket)
                .subscribe(new Subscriber<byte[]>() {

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
                    public void onNext(byte[] t) {
                        ByteBuffer buffer = ByteBuffer.wrap(t);
                        NioRx.<ByteBuffer, Integer> wrap(socket::write, buffer)
                                .doOnError(e -> e.printStackTrace())
                                .toBlocking()
                                .forEach(i -> request(1));
                    }
                });
    }
}
