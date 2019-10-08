package cn.schoolwow.quickserver;

import cn.schoolwow.quickserver.handler.CommonHandler;
import cn.schoolwow.quickserver.handler.ControllerHandler;
import cn.schoolwow.quickserver.handler.ControllerMeta;
import cn.schoolwow.quickserver.request.RequestHandler;
import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseHandler;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.session.SessionHandler;
import cn.schoolwow.quickserver.session.SessionMeta;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class QuickServer {
    Logger logger = LoggerFactory.getLogger(QuickServer.class);
    private int port = 10000;
    private String indexPage = "/index.html";
    private ControllerMeta controllerMeta = new ControllerMeta();
    private ThreadPoolExecutor threadPoolExecutor;

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
        ServerSocket serverSocket = new ServerSocket(port);
        logger.info("[开启服务器]http://127.0.0.1:{}{}",port,indexPage);
        while (true) {
            final Socket socket = serverSocket.accept();
            threadPoolExecutor.execute(() -> {
                final RequestMeta requestMeta = new RequestMeta();
                requestMeta.remoteAddress = socket.getInetAddress();
                final ResponseMeta responseMeta = new ResponseMeta();
                logger.trace("[接收请求]客户端地址:{}",requestMeta.remoteAddress);
                try {
                    requestMeta.inputStream = new BufferedInputStream(socket.getInputStream());
                    responseMeta.outputStream = new BufferedOutputStream(socket.getOutputStream());
                    RequestHandler.parseRequest(requestMeta);
                    if (null == requestMeta.method) {
                        return;
                    }

                    SessionMeta sessionMeta = SessionHandler.handleRequest(requestMeta, responseMeta);
                    if(requestMeta.requestURI.equals("/")){
                        requestMeta.requestURI = "/index.html";
                    }
                    CommonHandler.handleRequest(requestMeta, responseMeta, sessionMeta, controllerMeta);
                    CommonHandler.handleResponse(requestMeta, responseMeta, sessionMeta, controllerMeta);
                    ResponseHandler.handleResponse(requestMeta, responseMeta);
                    requestMeta.inputStream.close();
                    responseMeta.outputStream.close();
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        responseMeta.response(ResponseMeta.HttpStatus.INTERNAL_SERVER_ERROR, requestMeta);
                        ResponseHandler.handleResponse(requestMeta, responseMeta);
                        socket.close();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }
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
