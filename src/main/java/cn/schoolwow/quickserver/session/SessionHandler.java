package cn.schoolwow.quickserver.session;

import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpCookie;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionHandler {
    private static Logger logger = LoggerFactory.getLogger(SessionHandler.class);
    /**
     * 会话存储
     */
    private static ConcurrentHashMap<String, SessionMeta> sessionMap = new ConcurrentHashMap();

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
