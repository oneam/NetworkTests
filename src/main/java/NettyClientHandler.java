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
        writeLoop(ctx);
    }

    private void writeLoop(ChannelHandlerContext ctx) {
        ByteBuf messageBuffer = Unpooled.wrappedBuffer(messageBytes);
        ctx.writeAndFlush(messageBuffer).addListener(f -> {
            writeLoop(ctx);
        });
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ByteBuf messageBuffer = Unpooled.wrappedBuffer(messageBytes);
        ctx.write(messageBuffer);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buffer = (ByteBuf) msg;
        counter.addAndGet(buffer.readableBytes());
        buffer.release();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
