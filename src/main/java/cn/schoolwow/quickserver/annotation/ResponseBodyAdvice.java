package cn.schoolwow.quickserver.annotation;

import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.session.SessionMeta;

import java.lang.reflect.Method;

public interface ResponseBodyAdvice {
    /**
     * 是否支持
     */
    boolean support(Method method);

    /**
     * 定制化返回结果
     */
    Object beforeBodyWrite(Object result, Method method, RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta);
}
