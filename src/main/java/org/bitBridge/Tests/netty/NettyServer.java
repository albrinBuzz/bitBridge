package org.bitBridge.Tests.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.*;

public class NettyServer {
    public static void main(String[] args) throws Exception {
        // Boss maneja las conexiones nuevas, Worker maneja el tráfico de miles de sockets
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // OPTIMIZACIÓN: Cola de espera para conexiones entrantes masivas
                    .option(ChannelOption.SO_BACKLOG, 10000)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
                            ch.pipeline().addLast(new ObjectEncoder());
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<ProtocoloChat>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, ProtocoloChat msg) {
                                    // El servidor simplemente devuelve un ACK
                                    ctx.writeAndFlush(new ProtocoloChat(msg.id, "ACK", true));
                                }
                            });
                        }
                    });

            System.out.println("Servidor listo para miles de conexiones...");
            b.bind(8080).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}