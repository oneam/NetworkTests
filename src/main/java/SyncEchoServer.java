import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class SyncEchoServer {

    public static final int PORT = 4726;
    public static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) throws Exception {
        startServer();
    }

    static void startServer() throws IOException {
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
                int bytesRead = socket.read(buffer);
                if (bytesRead <= 0) {
                    break;
                }

                buffer.flip();
                int bytesWritten = socket.write(buffer);
                if (bytesWritten <= 0) {
                    break;
                }
                buffer.compact();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Utils.closeAndLog(socket);
        }
    }
}
