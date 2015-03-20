import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public class NettyClientHandler extends ChannelInboundHandlerAdapter {

    private final AtomicLong counter;
    private final byte[] messageBytes;

    public NettyClientHandler(String message, AtomicLong counter) {
        this.messageBytes = message.getBytes(StandardCharsets.UTF_8);
        this.counter = counter;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ByteBuf messageBuffer = Unpooled.wrappedBuffer(messageBytes);
        ctx.writeAndFlush(messageBuffer);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        counter.incrementAndGet();
        ((ByteBuf) msg).release();
        ByteBuf messageBuffer = Unpooled.wrappedBuffer(messageBytes);
        ctx.writeAndFlush(messageBuffer);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
