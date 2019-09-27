package cn.schoolwow.quickserver.handler;

import cn.schoolwow.quickserver.annotation.Interceptor;
import cn.schoolwow.quickserver.annotation.RequestMapping;
import cn.schoolwow.quickserver.annotation.RequestMethod;
import cn.schoolwow.quickserver.annotation.ResponseBodyAdvice;
import cn.schoolwow.quickserver.domain.Filter;
import cn.schoolwow.quickserver.domain.Request;
import cn.schoolwow.quickserver.interceptor.HandlerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 映射处理类
 * 处理映射,过滤器等等功能
 */
public class ControllerHandler {
    private static Logger logger = LoggerFactory.getLogger(ControllerHandler.class);

    /**
     * 扫描拦截器和控制器类
     */
    public static void handle(ControllerMeta controllerMeta) {
        controllerMeta.component.refresh();
        for (Class c : controllerMeta.component.getBeanClassList()) {
            //注册拦截器
            {
                Interceptor interceptor = (Interceptor) c.getDeclaredAnnotation(Interceptor.class);
                if (interceptor != null && !c.getName().equals("cn.schoolwow.quickserver.interceptor.HandlerInterceptor")) {
                    Filter filter = new Filter();
                    filter.patterns = interceptor.patterns();
                    filter.excludePatterns = interceptor.excludePatterns();
                    filter.handlerInterceptorClass = c;
                    filter.handlerInterceptor = (HandlerInterceptor) controllerMeta.component.getBean(c.getName());
                    controllerMeta.filterList.add(filter);
                    logger.info("[注册Filter]拦截器名:{},匹配路径:{},排除路径:{}", c.getSimpleName(), filter.patterns, filter.excludePatterns);
                }
            }
            //注册控制器
            {
                String bathUrl = "";
                RequestMapping classRequestMapping = (RequestMapping) c.getDeclaredAnnotation(RequestMapping.class);
                if (classRequestMapping != null) {
                    bathUrl = classRequestMapping.value();
                }
                Method[] methods = c.getMethods();
                for (Method method : methods) {
                    RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);
                    if (methodRequestMapping == null) {
                        continue;
                    }
                    String mappingUrl = bathUrl + methodRequestMapping.value();
                    RequestMethod[] requestMethods = methodRequestMapping.method();
                    if (requestMethods.length == 0) {
                        logger.info("[映射路径][{}] onto {}", mappingUrl, method.toString());
                    } else {
                        logger.info("[映射路径][{},method={}] onto {}", mappingUrl, requestMethods, method.toString());
                    }
                    Request request = new Request();
                    request.instance = controllerMeta.component.getBean(c.getName());
                    request.mappingUrl = mappingUrl;
                    request.requestMethods = methodRequestMapping.method();
                    request.method = method;
                    request.antPatternUrl = mappingUrl.replaceAll("\\{\\w+\\}", "\\*");
                    //将请求方法也作为key值
                    if (requestMethods.length > 0) {
                        for (RequestMethod requestMethod : request.requestMethods) {
                            mappingUrl += requestMethod.name() + "_";
                        }
                    }
                    controllerMeta.requestMappingHandler.put(mappingUrl, request);
                }
            }
        }
        //注册ResponseBodyAdvice
        {
            controllerMeta.responseBodyAdvice = controllerMeta.component.getBean(ResponseBodyAdvice.class);
        }
    }
}
