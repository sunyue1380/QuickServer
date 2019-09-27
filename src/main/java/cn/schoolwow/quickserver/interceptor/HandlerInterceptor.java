package cn.schoolwow.quickserver.interceptor;

import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.session.SessionMeta;

import java.lang.reflect.Method;

/**
 * 拦截器接口
 */
public interface HandlerInterceptor {
    /**
     * 预处理
     */
    boolean preHandle(RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta, Method handler);

    /**
     * 后处理
     */
    void postHandle(RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta, Method handler, Object result);

    /**
     * 请求结束
     */
    void afterCompletion(RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta, Method handler);
}
