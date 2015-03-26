import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import rx.Observable;
import rx.subjects.PublishSubject;

public class EchoClients {

    static final int PORT = 4726;
    static final int BUFFER_SIZE = 4096;
    static final String MESSAGE = "message\n";
    static CountDownLatch quit = new CountDownLatch(1);
    static ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        Observable.interval(1, TimeUnit.SECONDS).subscribe(EchoClients::displayCounters);
        rawNioSync();
        rawNioAsync();
        rxNioAsync();
        netty();
        quit.await();
    }

    public static void displayCounters(Long i) {
        System.out.println();
        for (Entry<String, AtomicLong> counter : counters.entrySet()) {
            String desc = counter.getKey();
            Long count = counter.getValue().getAndSet(0) / MESSAGE.length();
            System.out.printf("%s: %d\n", desc, count);
        }
    }

    public static void rawNioSync() {
        try {
            InetSocketAddress remote = new InetSocketAddress("localhost", PORT);
            SocketChannel socket = SocketChannel.open(remote);

            new Thread(() -> {
                try {
                    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                    byte[] messageBytes = MESSAGE.getBytes(StandardCharsets.UTF_8);
                    while (quit.getCount() > 0) {
                        buffer.clear();
                        buffer.put(messageBytes);
                        buffer.flip();
                        socket.write(buffer);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "rawNioSyncWrite").start();

            new Thread(() -> {
                try {
                    AtomicLong counter = new AtomicLong();
                    counters.put("rawNioSync", counter);
                    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                    while (quit.getCount() > 0) {
                        buffer.clear();
                        int bytesRead = socket.read(buffer);
                        counter.addAndGet(bytesRead);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "rawNioSyncRead").start();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    public static void rawNioAsync() throws IOException {
        AtomicLong counter = new AtomicLong();
        counters.put("rawNioAsync", counter);

        RawNioAsyncRunner
                .builder()
                .withReadBuffer(ByteBuffer.allocate(BUFFER_SIZE))
                .withWriteBuffer(ByteBuffer.allocate(BUFFER_SIZE))
                .withMessageBytes(MESSAGE.getBytes(StandardCharsets.UTF_8))
                .withSocket(AsynchronousSocketChannel.open())
                .withRemote(new InetSocketAddress("localhost", PORT))
                .withCounter(counter)
                .build()
                .start();
    }

    public static void rxNioAsync() throws IOException {
        AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
        InetSocketAddress remote = new InetSocketAddress("localhost", PORT);

        ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        byte[] messageBytes = MESSAGE.getBytes(StandardCharsets.UTF_8);
        PublishSubject<Integer> writeLoop = PublishSubject.create();
        writeLoop
                .flatMap(_bytesWritten -> {
                    writeBuffer.clear();
                    writeBuffer.put(messageBytes);
                    writeBuffer.flip();
                    return NioRx.<ByteBuffer, Integer> wrap(socket::write, writeBuffer);
                })
                .subscribe(writeLoop::onNext, Throwable::printStackTrace);

        AtomicLong counter = new AtomicLong();
        counters.put("rxNioAsync", counter);
        ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        PublishSubject<Integer> readLoop = PublishSubject.create();
        readLoop
                .flatMap(bytesRead -> {
                    counter.addAndGet(bytesRead);
                    readBuffer.clear();
                    return NioRx.<ByteBuffer, Integer> wrap(socket::read, readBuffer);
                })
                .subscribe(readLoop::onNext, Throwable::printStackTrace);

        NioRx.<SocketAddress, Void> wrap(socket::connect, remote)
                .subscribe(_void -> {
                    writeLoop.onNext(0);
                    readLoop.onNext(0);
                }, Throwable::printStackTrace);
    }

    public static void netty() throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        counters.put("netty", counter);

        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<io.netty.channel.socket.SocketChannel>() {
                    @Override
                    public void initChannel(io.netty.channel.socket.SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new NettyClientHandler(MESSAGE, counter));
                    }
                });

        ChannelFuture connectFuture = bootstrap.connect("localhost", PORT).sync();
        connectFuture.channel().closeFuture().addListener(_future -> {
            group.shutdownGracefully();
        });
    }
}
