package cn.schoolwow.quickserver.util;

import cn.schoolwow.quickbeans.QuickBeans;
import cn.schoolwow.quickbeans.util.PackageUtil;
import cn.schoolwow.quickserver.annotation.Interceptor;
import cn.schoolwow.quickserver.annotation.RequestMapping;
import cn.schoolwow.quickserver.annotation.RequestMethod;
import cn.schoolwow.quickserver.domain.Filter;
import cn.schoolwow.quickserver.domain.Request;
import cn.schoolwow.quickserver.interceptor.HandlerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ControllerUtil {
    private static Logger logger = LoggerFactory.getLogger(ControllerUtil.class);
    /**控制器映射*/
    public static Map<String, Request> requestMappingHandler = new HashMap<>();
    /**拦截器映射*/
    public static List<Filter> filterList = new ArrayList<>();
    /**缓存跨域头*/
    public static Map<String, Map<String,String>> crossOriginMap = new ConcurrentHashMap<>();
    /**IOC容器*/
    private static QuickBeans quickBeans = new QuickBeans();
    /**扫描类数组*/
    private static List<Class> classList = new ArrayList<>();

    /**获取拦截器实例*/
    public static HandlerInterceptor getInterceptor(Class _class){
        return (HandlerInterceptor) quickBeans.getBean(_class.getName());
    }

    /**注入依赖与扫描注解*/
    public static void refresh() {
        quickBeans.refresh();
        Set<String> beanNameSet = quickBeans.getBeanNameSet();
        for(String beanName:beanNameSet){
            doScan(quickBeans.getBean(beanName).getClass());
        }
    }

    /**注册Controller类*/
    public static void register(Class _class) {
        quickBeans.register(_class);
        classList.add(_class);
    }

    /**注册Controller类*/
    public static void scan(String packageName) {
        logger.debug("[扫描路径]{}",packageName);
        quickBeans.scan(packageName);
        classList.addAll(PackageUtil.scanPackage(packageName));
    }

    /**扫描相关注解*/
    private static void doScan(Class c){
        //注册拦截器
        Interceptor interceptor = (Interceptor) c.getDeclaredAnnotation(Interceptor.class);
        if(interceptor!=null){
            Filter filter = new Filter();
            filter.patterns = interceptor.patterns();
            filter.excludePatterns = interceptor.excludePatterns();
            filter.handlerInterceptorClass = c;
            filterList.add(filter);
            logger.info("[注册Filter]拦截器名:{},匹配路径:{},排除路径:{}",c.getSimpleName(),filter.patterns,filter.excludePatterns);
        }
        //注册控制器
        String bathUrl = "";
        RequestMapping classRequestMapping = (RequestMapping) c.getDeclaredAnnotation(RequestMapping.class);
        if(classRequestMapping!=null){
            bathUrl = classRequestMapping.value();
        }

        Method[] methods = c.getMethods();
        for(Method method:methods){
            RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);
            if(methodRequestMapping==null){
                continue;
            }
            String mappingUrl = bathUrl+methodRequestMapping.value();
            RequestMethod[] requestMethods = methodRequestMapping.method();
            if(requestMethods.length==0){
                logger.info("[映射路径][{}] onto {}",mappingUrl,method.toString());
            }else{
                logger.info("[映射路径][{},method={}] onto {}",mappingUrl,requestMethods,method.toString());
            }
            Request request = new Request();
            request.instance = quickBeans.getBean(c.getName());
            request.mappingUrl = mappingUrl;
            request.method = method;
            requestMappingHandler.put(mappingUrl,request);
        }
    }
}
