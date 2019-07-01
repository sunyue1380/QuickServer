package cn.schoolwow.quickserver.service;

import cn.schoolwow.quickbeans.annotation.Component;
import cn.schoolwow.quickserver.session.SessionMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class IndexService {
    private Logger logger = LoggerFactory.getLogger(IndexService.class);
    /**注册*/
    public boolean register(String username,String password){
        if("quickserver".equalsIgnoreCase(username)&&
                "123456".equalsIgnoreCase(password)){
            return true;
        }else{
            return false;
        }
    }
    /**登录*/
    public boolean login(String username, String password, SessionMeta sessionMeta){
        if("quickserver".equalsIgnoreCase(username)&&
                "123456".equalsIgnoreCase(password)){
            sessionMeta.attributes.put("username",username);
            logger.info("[会话添加属性]会话id:{},属性:{}", sessionMeta.id,sessionMeta.attributes);
            return true;
        }else{
            return false;
        }
    }
}
