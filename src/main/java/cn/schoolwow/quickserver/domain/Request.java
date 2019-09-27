package cn.schoolwow.quickserver.domain;

import cn.schoolwow.quickserver.annotation.RequestMethod;

import java.lang.reflect.Method;

/**
 * 请求类
 */
public class Request {
    /**
     * 映射路径
     */
    public String mappingUrl;
    /**
     * 匹配方法
     */
    public RequestMethod[] requestMethods;
    /**
     * Ant模式路径
     */
    public String antPatternUrl;
    /**
     * 关联方法
     */
    public Method method;
    /**
     * 控制器实例
     */
    public Object instance;
}
