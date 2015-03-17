import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousSocketChannel;

public class Utils {

    public static void closeAndLog(Closeable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static SocketAddress getRemoteAddress(AsynchronousSocketChannel socket) {
        try {
            return socket.getRemoteAddress();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

}
