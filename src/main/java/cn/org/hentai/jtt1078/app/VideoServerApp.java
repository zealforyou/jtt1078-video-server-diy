package cn.org.hentai.jtt1078.app;

import cn.org.hentai.jtt1078.http.GeneralResponseWriter;
import cn.org.hentai.jtt1078.http.NettyHttpServerHandler;
import cn.org.hentai.jtt1078.publisher.PublishManager;
import cn.org.hentai.jtt1078.server.Jtt1078Handler;
import cn.org.hentai.jtt1078.server.Jtt1078MessageDecoder;
import cn.org.hentai.jtt1078.server.SessionManager;
import cn.org.hentai.jtt1078.util.Configs;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * Created by matrixy on 2019/4/9.
 */
public class VideoServerApp
{
    private static Logger logger = LoggerFactory.getLogger(VideoServerApp.class);

    public static void main(String[] args) throws Exception
    {
        Configs.init("/app.properties");
        PublishManager.init();
        SessionManager.init();

        VideoServer videoServer = new VideoServer();
        HttpServer httpServer = new HttpServer();

        Signal.handle(new Signal("TERM"), new SignalHandler()
        {
            @Override
            public void handle(Signal signal)
            {
                videoServer.shutdown();
                httpServer.shutdown();
            }
        });

        videoServer.start();
        httpServer.start();
    }

    static class VideoServer
    {
        private static ServerBootstrap serverBootstrap;

        private static EventLoopGroup bossGroup;
        private static EventLoopGroup workerGroup;

        private static void start() throws Exception
        {
            serverBootstrap = new ServerBootstrap();
            serverBootstrap.option(ChannelOption.SO_BACKLOG, Configs.getInt("server.backlog", 102400));
            bossGroup = new NioEventLoopGroup(Configs.getInt("server.worker-count", Runtime.getRuntime().availableProcessors()));
            workerGroup = new NioEventLoopGroup();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(final SocketChannel channel) throws Exception {
                            ChannelPipeline p = channel.pipeline();
                            // p.addLast(new IdleStateHandler(10,0,0, TimeUnit.SECONDS));
                            p.addLast(new Jtt1078MessageDecoder());
                            // p.addLast(new Jtt808MessageEncoder());
                            // p.addLast(new JTT808Handler());
                            p.addLast(new Jtt1078Handler());
                        }
                    });

            int port = Configs.getInt("server.port", 1078);
            Channel ch = serverBootstrap.bind(InetAddress.getByName("0.0.0.0"), port).sync().channel();
            logger.info("Video Server started at: {}", port);
            ch.closeFuture();
        }

        private static void shutdown()
        {
            try
            {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    static class HttpServer
    {
        private static ServerBootstrap serverBootstrap;

        private static EventLoopGroup bossGroup;
        private static EventLoopGroup workerGroup;

        private static void start() throws Exception
        {
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>()
                    {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception
                        {
                            ch.pipeline().addLast(
                                    new GeneralResponseWriter(),
                                    new HttpResponseEncoder(),
                                    new HttpRequestDecoder(),
                                    new HttpObjectAggregator(1024 * 64),
                                    new NettyHttpServerHandler()
                            );
                        }
                    }).option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            try
            {
                int port = Configs.getInt("server.http.port", 3333);
                ChannelFuture f = bootstrap.bind(InetAddress.getByName("0.0.0.0"), port).sync();
                logger.info("HTTP Server started at: {}", port);
                f.channel().closeFuture().sync();
            }
            catch (InterruptedException e)
            {
                logger.error("http server error", e);
            }
            finally
            {
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            }
        }

        private static void shutdown()
        {
            try
            {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
