package cn.schoolwow.quickserver;

import cn.schoolwow.quickserver.handler.ControllerHandler;
import cn.schoolwow.quickserver.handler.ControllerMeta;
import cn.schoolwow.quickserver.io.AbstractIOServer;
import cn.schoolwow.quickserver.io.BIOServer;
import cn.schoolwow.quickserver.io.IOServerType;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class QuickServer {
    Logger logger = LoggerFactory.getLogger(QuickServer.class);
    private int port = 10000;
    private String indexPage = "/index.html";
    private ControllerMeta controllerMeta = new ControllerMeta();
    private ThreadPoolExecutor threadPoolExecutor;
    private IOServerType ioServerType = IOServerType.BIO;

    public static QuickServer newInstance() {
        return new QuickServer();
    }

    public QuickServer port(int port) {
        this.port = port;
        return this;
    }

    public QuickServer indexPage(String indexPage) {
        this.indexPage = indexPage;
        return this;
    }

    public QuickServer scan(String packageName) {
        controllerMeta.component.scan(packageName);
        return this;
    }

    public QuickServer register(Class _class) {
        controllerMeta.component.register(_class);
        return this;
    }

    public QuickServer threadPool(ThreadPoolExecutor threadPoolExecutor) {
        this.threadPoolExecutor = threadPoolExecutor;
        return this;
    }

    public QuickServer io(IOServerType ioServerType) {
        this.ioServerType = ioServerType;
        return this;
    }

    /**
     * 配置压缩策略
     */
    public QuickServer compress(boolean compress) {
        if (compress) {
            QuickServerConfig.compressSupports = new String[]{"gzip"};
        } else {
            QuickServerConfig.compressSupports = null;
        }
        return this;
    }

    public void asyncStart() throws IOException {
        assureInitial();
        threadPoolExecutor.execute(() -> {
            try {
                start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void start() throws IOException {
        assureInitial();

        AbstractIOServer ioServer = null;
        switch (ioServerType){
            case BIO:{
                ioServer = new BIOServer(controllerMeta);
            };break;
            case NIO:{
                //TODO 实现NIO模型
            };break;
            case AIO:{
                //TODO 实现AIO模型
            };break;
        }
        logger.debug("[开启服务器]http://127.0.0.1:{}{}",port,indexPage);
        ioServer.startServer(port);
    }

    private void assureInitial() throws IOException {
        if (null != QuickServerConfig.realPath) {
            return;
        }
        if (threadPoolExecutor == null) {
            threadPoolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 200, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
        }
        controllerMeta.component.refresh();
        ControllerHandler.handle(controllerMeta);
        //获取真实路径
        String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        if (System.getProperty("os.name").contains("dows")) {
            path = path.substring(1, path.length());
        }
        if (path.contains("jar")) {
            path = path.substring(0, path.lastIndexOf("."));
            QuickServerConfig.realPath = path.substring(0, path.lastIndexOf("/"));
        } else {
            QuickServerConfig.realPath = path.replace("target/classes/", "");
        }
    }
}
