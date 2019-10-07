package cn.schoolwow.quickserver.controller;

import cn.schoolwow.quickbeans.annotation.Component;
import cn.schoolwow.quickserver.annotation.ResponseBodyAdvice;
import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.session.SessionMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

@Component
public class ResponseAdvice implements ResponseBodyAdvice{
    private Logger logger = LoggerFactory.getLogger(ResponseBodyAdvice.class);
    @Override
    public boolean support(Method method) {
        logger.info("[ResponseAdvice]调用了support方法");
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object result, Method method, RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta) {
        logger.info("[ResponseAdvice]调用了beforeBodyWrite方法,返回true");
        return true;
    }
}
