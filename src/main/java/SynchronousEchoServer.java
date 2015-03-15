import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class SynchronousEchoServer {

    public static final int PORT = 4726;
    public static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) throws Exception {
        startSyncServer();
    }

    static void startSyncServer() throws IOException {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            InetSocketAddress local = new InetSocketAddress(PORT);
            server.bind(local);
            System.out.printf("Sync server Listening on %s\n", local);
            while (true) {
                SocketChannel client = server.accept();
                Thread clientThread = new Thread(() -> handleSyncConnection(client));
                clientThread.start();
            }
        }
    }

    static void handleSyncConnection(SocketChannel socket) {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        try {
            System.out.printf("Client connected from %s\n", socket.getRemoteAddress());
            while (true) {
                buffer.clear();
                socket.read(buffer);
                buffer.flip();
                socket.write(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Utils.closeAndLog(socket);
        }
    }
}
