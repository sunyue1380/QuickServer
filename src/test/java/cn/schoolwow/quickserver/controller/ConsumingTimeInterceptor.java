package cn.schoolwow.quickserver.controller;


import cn.schoolwow.quickbeans.annotation.Component;
import cn.schoolwow.quickbeans.annotation.Scope;
import cn.schoolwow.quickbeans.annotation.ScopeType;
import cn.schoolwow.quickserver.annotation.Interceptor;
import cn.schoolwow.quickserver.interceptor.HandlerInterceptor;
import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.service.IndexService;
import cn.schoolwow.quickserver.session.SessionMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.lang.reflect.Method;

@Interceptor(patterns = {"/"})
@Component
@Scope(ScopeType.prototype)
public class ConsumingTimeInterceptor implements HandlerInterceptor {
    @Resource
    private IndexService indexService;

    private Logger logger = LoggerFactory.getLogger(ConsumingTimeInterceptor.class);
    private long startTime;
    private long endTime;
    @Override
    public boolean preHandle(RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta, Method handler) {
        startTime = System.currentTimeMillis();
        return true;
    }

    @Override
    public void postHandle(RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta, Method handler, Object result) {
    }

    @Override
    public void afterCompletion(RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta, Method handler) {
        if(handler!=null){
            endTime = System.currentTimeMillis();
            logger.info("[方法耗时]耗时:{}毫秒,方法名:{}",(endTime-startTime),handler.toString());
        }
    }
}
