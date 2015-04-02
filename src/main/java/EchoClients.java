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

    static final InetSocketAddress REMOTE = new InetSocketAddress("127.0.0.1", 4726);
    static final int BUFFER_SIZE = 4096;
    static final String MESSAGE = "message\n";
    static final byte[] MESSAGE_BYTES = MESSAGE.getBytes(StandardCharsets.UTF_8);
    static CountDownLatch quit = new CountDownLatch(1);
    static ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        Observable.interval(1, TimeUnit.SECONDS).subscribe(EchoClients::displayCounters);
        for (int i = 0; i < 10; ++i) {
            //            rawNioSync(i);
            rawNioAsync(i);
            //            rxNioAsync(i);
            //            netty(i);
        }
        quit.await();
    }

    public static void displayCounters(Long i) {
        System.out.println();
        long sum = 0;
        for (Entry<String, AtomicLong> counter : counters.entrySet()) {
            String desc = counter.getKey();
            long count = counter.getValue().getAndSet(0) / MESSAGE.length();
            System.out.printf("%s: %d\n", desc, count);
            sum += count;
        }
        System.out.printf("Sum: %d\n", sum);
    }

    public static void rawNioSync(int i) {
        try {
            SocketChannel socket = SocketChannel.open(REMOTE);

            new Thread(() -> {
                try {
                    ByteBuffer buffer = ByteBuffer.wrap(MESSAGE_BYTES);
                    while (quit.getCount() > 0) {
                        buffer.rewind();
                        socket.write(buffer);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "rawNioSyncWrite").start();

            new Thread(() -> {
                try {
                    AtomicLong counter = new AtomicLong();
                    counters.put(String.format("rawNioSync %d", i), counter);
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

    public static void rawNioAsync(int i) throws IOException {
        AtomicLong counter = new AtomicLong();
        counters.put(String.format("rawNioAsync %d", i), counter);

        RawNioAsyncRunner
                .builder()
                .withReadBuffer(ByteBuffer.allocate(BUFFER_SIZE))
                .withWriteBuffer(ByteBuffer.wrap(MESSAGE_BYTES))
                .withSocket(AsynchronousSocketChannel.open())
                .withRemote(REMOTE)
                .withCounter(counter)
                .build()
                .start();
    }

    public static void rxNioAsync(int i) throws IOException {
        AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();

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
        counters.put(String.format("rxNioAsync %d", i), counter);
        ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        PublishSubject<Integer> readLoop = PublishSubject.create();
        readLoop
                .flatMap(bytesRead -> {
                    counter.addAndGet(bytesRead);
                    readBuffer.clear();
                    return NioRx.<ByteBuffer, Integer> wrap(socket::read, readBuffer);
                })
                .subscribe(readLoop::onNext, Throwable::printStackTrace);

        NioRx.<SocketAddress, Void> wrap(socket::connect, REMOTE)
                .subscribe(_void -> {
                    writeLoop.onNext(0);
                    readLoop.onNext(0);
                }, Throwable::printStackTrace);
    }

    public static void netty(int i) throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        counters.put(String.format("netty %d", i), counter);

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

        ChannelFuture connectFuture = bootstrap.connect(REMOTE).sync();
        connectFuture.channel().closeFuture().addListener(_future -> {
            group.shutdownGracefully();
        });
    }
}
