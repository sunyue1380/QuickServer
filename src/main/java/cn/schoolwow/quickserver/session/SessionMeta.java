package cn.schoolwow.quickserver.session;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SessionMeta {
    /**sessionId*/
    public String id;
    /**会话属性*/
    public Map<String,String> attributes = new HashMap<>();
    /**创建时间*/
    public Date createdTime;
    /**上次访问时间*/
    public Date lastAccessedTime;
    /**注销会话*/
    public void invalidate(){
        SessionHandler.invalidate(id);
    }
}
