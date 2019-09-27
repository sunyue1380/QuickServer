package cn.schoolwow.quickserver.handler;

import cn.schoolwow.quickbeans.QuickBeans;
import cn.schoolwow.quickserver.annotation.ResponseBodyAdvice;
import cn.schoolwow.quickserver.domain.Filter;
import cn.schoolwow.quickserver.domain.Request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ControllerMeta {
    /**
     * 控制器映射
     */
    public Map<String, Request> requestMappingHandler = new HashMap<>();
    /**
     * 拦截器映射
     */
    public List<Filter> filterList = new ArrayList<>();
    /**
     * IOC容器
     */
    public QuickBeans component = new QuickBeans();
    /**
     * ResponseBodyAdvice
     */
    public ResponseBodyAdvice responseBodyAdvice;
    /**
     * 缓存跨域头
     */
    public Map<String, Map<String, String>> crossOriginMap = new ConcurrentHashMap<>();
}
