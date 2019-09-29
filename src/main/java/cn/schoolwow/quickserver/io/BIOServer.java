package cn.schoolwow.quickserver.io;

import cn.schoolwow.quickserver.handler.CommonHandler;
import cn.schoolwow.quickserver.handler.ControllerMeta;
import cn.schoolwow.quickserver.request.RequestHandler;
import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseHandler;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.session.SessionHandler;
import cn.schoolwow.quickserver.session.SessionMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class BIOServer extends AbstractIOServer{
    private Logger logger = LoggerFactory.getLogger(BIOServer.class);

    public BIOServer(ControllerMeta controllerMeta) {
        super(controllerMeta);
    }

    @Override
    public void startServer(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        while (true) {
            final Socket socket = serverSocket.accept();
            threadPoolExecutor.execute(() -> {
                final RequestMeta requestMeta = new RequestMeta();
                requestMeta.remoteAddress = socket.getInetAddress();
                final ResponseMeta responseMeta = new ResponseMeta();
                try {
                    //处理输入流
                    requestMeta.inputStream = new BufferedInputStream(socket.getInputStream());
                    RequestHandler.parseRequest(requestMeta);
                    if (null == requestMeta.method) {
                        return;
                    }
                    //处理输出流
                    responseMeta.protocol = requestMeta.protocol;
                    responseMeta.outputStream = new BufferedOutputStream(socket.getOutputStream());
                    SessionMeta sessionMeta = SessionHandler.handleRequest(requestMeta, responseMeta);
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
}
