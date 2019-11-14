package cn.schoolwow.quickserver.session;

import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpCookie;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class SessionHandler {
    private static Logger logger = LoggerFactory.getLogger(SessionHandler.class);
    /**
     * 默认会话过期时间(秒)
     * */
    private volatile static int maxInactiveInterval = 3600;
    /**
     * 会话存储
     */
    private static ConcurrentHashMap<String, SessionMeta> sessionMap = new ConcurrentHashMap();
    /**
     * 定时任务线程池
     * */
    private static ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    static{
        //每隔10分钟检查一次
        scheduledExecutorService.scheduleWithFixedDelay(()->{
            Iterator<Map.Entry<String,SessionMeta>> iterable = sessionMap.entrySet().iterator();
            while(iterable.hasNext()){
                Map.Entry<String,SessionMeta> entry = iterable.next();
                if((System.currentTimeMillis()-entry.getValue().lastAccessedTime.getTime())/1000>=maxInactiveInterval){
                    iterable.remove();
                }
            }
        },60,600, TimeUnit.SECONDS);
    }
    /**
     * 设置默认会话过期时间
     * @param seconds 最大会话秒数
     * */
    public static void setMaxInactiveInterval(int seconds){
        maxInactiveInterval = seconds;
    }

    /**
     * 根据RequestMeta获取SessionMeta对象
     */
    public static SessionMeta handleRequest(RequestMeta requestMeta, ResponseMeta responseMeta) {
        String sessionId = requestMeta.cookies.get(QuickServerConfig.SESSION);
        if (sessionId == null || !sessionMap.containsKey(sessionId)) {
            //创建会话
            SessionMeta sessionMeta = new SessionMeta();
            sessionMeta.createdTime = new Date();
            sessionMeta.id = UUID.randomUUID().toString();
            sessionId = sessionMeta.id;
            sessionMap.put(sessionMeta.id, sessionMeta);
            responseMeta.cookies.add(new HttpCookie(QuickServerConfig.SESSION, sessionMeta.id));
            logger.trace("[创建会话]会话id:{}", sessionId);
        }
        SessionMeta sessionMeta = sessionMap.get(sessionId);
        sessionMeta.lastAccessedTime = new Date();
        return sessionMeta;
    }

    /**
     * 注销会话
     */
    public static void invalidate(String sessionId) {
        logger.trace("[销毁会话]会话id:{}", sessionId);
        sessionMap.remove(sessionId);
    }
}
