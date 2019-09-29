package cn.schoolwow.quickserver.io;

import cn.schoolwow.quickserver.handler.ControllerMeta;

import java.util.concurrent.ThreadPoolExecutor;

public abstract class AbstractIOServer implements IOServer{
    protected ControllerMeta controllerMeta;
    public static ThreadPoolExecutor threadPoolExecutor;

    public AbstractIOServer(ControllerMeta controllerMeta) {
        this.controllerMeta = controllerMeta;
    }
}
