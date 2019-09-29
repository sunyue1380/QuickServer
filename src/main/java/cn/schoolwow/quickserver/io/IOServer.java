package cn.schoolwow.quickserver.io;

import java.io.IOException;

/**服务器IO操作*/
public interface IOServer {
    void startServer(int port) throws IOException;
}
