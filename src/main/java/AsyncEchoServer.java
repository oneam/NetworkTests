import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;

public class AsyncEchoServer {
    public static final int PORT = 4726;
    public static final int BUFFER_SIZE = 65536;

    public static void main(String[] args) throws Exception {
        startAsyncServer();
    }

    static void startAsyncServer() throws IOException, InterruptedException {
        CountDownLatch quit = new CountDownLatch(1);
        AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
        InetSocketAddress local = new InetSocketAddress(PORT);
        server.bind(local);
        System.out.printf("Async serv listening on %s\n", local);

        beginAccept(server);
        quit.await();
    }

    static void beginAccept(AsynchronousServerSocketChannel server) {
        server.accept(server, endAccept);
    }

    static final CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> endAccept =
            new CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel>() {

                @Override
                public void failed(Throwable exc, AsynchronousServerSocketChannel server) {
                    exc.printStackTrace();
                    Utils.closeAndLog(server);
                }

                @Override
                public void completed(
                        AsynchronousSocketChannel clientSocket,
                        AsynchronousServerSocketChannel serverSocket) {
                    System.out.printf("Connected to %s\n", Utils.getRemoteAddress(clientSocket));
                    beginAccept(serverSocket);
                    try {
                        clientSocket.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                    Client client = new Client(clientSocket, buffer);
                    beginRead(client);
                }
            };

    static class Client {
        public Client(AsynchronousSocketChannel socket, ByteBuffer buffer) {
            this.socket = socket;
            this.buffer = buffer;
        }

        public AsynchronousSocketChannel socket;
        public ByteBuffer buffer;
    }

    static void beginRead(Client client) {
        client.socket.read(client.buffer, client, endRead);
    }

    static final CompletionHandler<Integer, Client> endRead = new CompletionHandler<Integer, Client>() {

        @Override
        public void completed(Integer result, Client client) {
            if (result == 0) {
                Utils.closeAndLog(client.socket);
                return;
            }
            client.buffer.flip();
            beginWrite(client);
        }

        @Override
        public void failed(Throwable exc, Client client) {
            exc.printStackTrace();
            Utils.closeAndLog(client.socket);
        }
    };

    static void beginWrite(Client client) {
        client.socket.write(client.buffer, client, endWrite);
    }

    static final CompletionHandler<Integer, Client> endWrite = new CompletionHandler<Integer, Client>() {

        @Override
        public void completed(Integer result, Client client) {
            if (result == 0) {
                Utils.closeAndLog(client.socket);
                return;
            }
            client.buffer.compact();
            beginRead(client);
        }

        @Override
        public void failed(Throwable exc, Client client) {
            exc.printStackTrace();
            Utils.closeAndLog(client.socket);
        }
    };
}
