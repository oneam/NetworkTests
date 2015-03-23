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
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class EchoClients {

    static final int PORT = 4726;
    static final int BUFFER_SIZE = 4096;
    static final String MESSAGE = "message";
    static CountDownLatch quit = new CountDownLatch(1);
    static ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        Observable.interval(1, TimeUnit.SECONDS).subscribe(EchoClients::displayCounters);
        Observable.just(null).observeOn(Schedulers.newThread()).subscribe(v -> rawNioSync());
        rawNioAsync();
        rxNioAsync();
        netty();
        quit.await();
    }

    public static void displayCounters(Long i) {
        System.out.println();
        for (Entry<String, AtomicLong> counter : counters.entrySet()) {
            String desc = counter.getKey();
            Long count = counter.getValue().getAndSet(0);
            System.out.printf("%s: %d\n", desc, count);
        }
    }

    public static void rawNioSync() {
        AtomicLong counter = new AtomicLong();
        counters.put("rawNioSync", counter);
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        InetSocketAddress remote = new InetSocketAddress("localhost", PORT);

        try (SocketChannel socket = SocketChannel.open(remote)) {
            byte[] messageBytes = MESSAGE.getBytes(StandardCharsets.UTF_8);
            while (quit.getCount() > 0) {
                buffer.clear();
                buffer.put(messageBytes);
                buffer.flip();
                socket.write(buffer);
                buffer.clear();
                socket.read(buffer);
                counter.incrementAndGet();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void rawNioAsync() throws IOException {
        AtomicLong counter = new AtomicLong();
        counters.put("rawNioAsync", counter);

        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        byte[] messageBytes = MESSAGE.getBytes(StandardCharsets.UTF_8);
        AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
        InetSocketAddress remote = new InetSocketAddress("localhost", PORT);

        RawNioAsyncRunner state = new RawNioAsyncRunner(socket, buffer, messageBytes, counter, remote);
        state.start();
    }

    public static void rxNioAsync() throws IOException {
        AtomicLong counter = new AtomicLong();
        counters.put("rxNioAsync", counter);

        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        byte[] messageBytes = MESSAGE.getBytes(StandardCharsets.UTF_8);
        AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
        InetSocketAddress remote = new InetSocketAddress("localhost", PORT);

        PublishSubject<Void> loop = PublishSubject.create();

        loop.flatMap(_void -> {
            buffer.clear();
            buffer.put(messageBytes);
            buffer.flip();
            return NioRx.<ByteBuffer, Integer> wrap(socket::write, buffer);
        }).flatMap(_bytesWritten -> {
            buffer.clear();
            return NioRx.<ByteBuffer, Integer> wrap(socket::read, buffer);
        }).subscribe(_bytesRead -> {
            counter.incrementAndGet();
            if (quit.getCount() > 0) loop.onNext(null);
        }, e -> {
            e.printStackTrace();
        });

        NioRx.<SocketAddress, Void> wrap(socket::connect, remote).subscribe(loop::onNext, loop::onError);
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
