package cn.schoolwow.quickserver.session;

import cn.schoolwow.quickserver.util.QuickServerConfig;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SessionMeta {
    /**
     * sessionId
     */
    public String id;
    /**
     * 会话属性
     */
    public Map<String, String> attributes = new HashMap<>();
    /**
     * 创建时间
     */
    public Date createdTime;
    /**
     * 上次访问时间
     */
    public Date lastAccessedTime;

    /**
     * 设置会话过期时间
     * @param seconds 会话过期时间(秒)
     */
    public void setMaxInactiveInterval(int seconds) {
        SessionHandler.setMaxInactiveInterval(seconds);
    }

    /**
     * 注销会话
     */
    public void invalidate() {
        SessionHandler.invalidate(id);
    }

    /**
     * 获取真实路径
     */
    public String getRealPath() {
        return QuickServerConfig.realPath;
    }
}
