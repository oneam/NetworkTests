import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.Semaphore;

public class NettyClient {
    static Semaphore limiter = new Semaphore(200);
    static Metrics metrics = new Metrics();

    public static void main(String[] args) throws Exception {
        String serverHostname = System.getProperty("server", "localhost");
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new ClientHandler());
                }
            });

            ChannelFuture f = b.connect(serverHostname, 4726).sync();
            Channel c = f.channel();
            metrics.start();

            while (c.isOpen()) {
                limiter.acquire();
                long start = System.nanoTime();
                ByteBuf startMsg = Unpooled.buffer();
                startMsg.writeLong(start);
                c.writeAndFlush(startMsg);
            }

            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    static class ClientHandler extends ChannelInboundHandlerAdapter {
        ByteBuf readBuffer = Unpooled.buffer();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            long end = System.nanoTime();
            ByteBuf m = (ByteBuf) msg;
            metrics.recordRead(m.readableBytes());
            readBuffer.writeBytes(m);
            m.release();

            while (readBuffer.readableBytes() > 0) {
                long start = readBuffer.readLong();
                metrics.recordLatency(end - start);
                limiter.release();
            }
            readBuffer.discardReadBytes();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }

}
