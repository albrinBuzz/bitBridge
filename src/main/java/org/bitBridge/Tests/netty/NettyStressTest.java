package org.bitBridge.Tests.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.*;
import java.util.concurrent.atomic.AtomicLong;

public class NettyStressTest {
    private static final int NUM_CLIENTES = 2000; // Miles de clientes simultáneos
    private static final int MSG_POR_CLIENTE = 100; // Cada uno envía 100 mensajes
    private static final AtomicLong totalAcks = new AtomicLong(0);

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<io.netty.channel.socket.SocketChannel>() {
                    @Override
                    public void initChannel(io.netty.channel.socket.SocketChannel ch) {
                        ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
                        ch.pipeline().addLast(new ObjectEncoder());
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ProtocoloChat>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ProtocoloChat msg) {
                                totalAcks.incrementAndGet();
                            }
                        });
                    }
                });

        long start = System.currentTimeMillis();
        System.out.println("Conectando " + NUM_CLIENTES + " clientes...");

        for (int i = 0; i < NUM_CLIENTES; i++) {
            b.connect("127.0.0.1", 8080).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    Channel ch = future.channel();
                    // Cada cliente envía sus mensajes al conectar
                    for (int j = 0; j < MSG_POR_CLIENTE; j++) {
                        ch.write(new ProtocoloChat("test", "data", false));
                    }
                    ch.flush();
                }
            });
        }

        // Monitor de progreso
        long expected = (long) NUM_CLIENTES * MSG_POR_CLIENTE;
        while (totalAcks.get() < expected) {
            System.out.println("Progreso: " + totalAcks.get() + " / " + expected + " ACKs recibidos");
            Thread.sleep(1000);
        }

        long end = System.currentTimeMillis();
        System.out.println("\n--- RESULTADO FINAL ---");
        System.out.println("Clientes totales: " + NUM_CLIENTES);
        System.out.println("Mensajes procesados: " + expected);
        System.out.println("Tiempo: " + (end - start) + "ms");
        System.out.println("Rendimiento: " + (expected * 1000L / (end - start)) + " mensajes/seg");

        group.shutdownGracefully();
    }
}