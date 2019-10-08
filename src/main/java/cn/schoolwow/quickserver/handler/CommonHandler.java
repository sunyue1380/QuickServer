package cn.schoolwow.quickserver.handler;

import cn.schoolwow.quickserver.annotation.BasicAuth;
import cn.schoolwow.quickserver.annotation.CrossOrigin;
import cn.schoolwow.quickserver.annotation.RequestMethod;
import cn.schoolwow.quickserver.domain.Filter;
import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.session.SessionMeta;
import cn.schoolwow.quickserver.util.AntPatternUtil;
import cn.schoolwow.quickserver.util.MIMEUtil;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 公共处理类
 */
public class CommonHandler {
    private static Logger logger = LoggerFactory.getLogger(CommonHandler.class);

    /**
     * 处理请求
     */
    public static void handleRequest(RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta, ControllerMeta controllerMeta) throws Exception {
        //TODO 初始化过滤器
//        {
//            for(Filter filter:controllerMeta.filterList){
//                if(AntPatternUtil.matchFilter(requestMeta.requestURI,filter)){
//                    filter.handlerInterceptor = controllerMeta.getInterceptor(filter.handlerInterceptorClass);
//                }
//            }
//        }
        //TODO 按照匹配模式长度进行顺序执行
        for (Filter filter : controllerMeta.filterList) {
            if (AntPatternUtil.matchFilter(requestMeta.requestURI, filter)) {
                if (!filter.handlerInterceptor.preHandle(requestMeta, responseMeta, sessionMeta, requestMeta.invokeMethod)) {
                    return;
                }
            }
        }
        //处理静态资源
        {
            handleStaticFile(requestMeta, responseMeta, controllerMeta);
            if (null != responseMeta.staticURL) {
                logger.debug("[静态资源]资源地址:{}", responseMeta.staticURL);
                return;
            }
        }
        //初始化Method
        {
            InvokeMethodHandler.getInvokeMethod(requestMeta, controllerMeta);
            logger.debug("[获取调用方法]{}", requestMeta.invokeMethod);
        }
        //判断请求路径
        {
            if (requestMeta.invokeMethod == null) {
                responseMeta.response(ResponseMeta.HttpStatus.NOT_FOUND, requestMeta);
                logger.debug("[请求路径不存在]路径:{},无对应处理方法.", requestMeta.requestURI);
                return;
            }
        }
        //处理Basic Auth
        {
            if (!handleBasicAuth(requestMeta, responseMeta)) {
                logger.warn("[BasicAuth]基本认证失败!");
                return;
            }
        }
        //处理跨域请求
        {
            handleCrossOrigin(requestMeta, responseMeta, controllerMeta);
            //判断是否是跨域预检请求
            if (requestMeta.origin != null && RequestMethod.OPTIONS.name().equalsIgnoreCase(requestMeta.method)) {
                responseMeta.status = 200;
                responseMeta.statusMessage = "OK";
                return;
            }
        }
        //方法调用
        Object result = null;
        {
            logger.debug("[调用方法]请求路径:[{}],调用方法:{}", requestMeta.method + " " + requestMeta.requestURI, requestMeta.invokeMethod.toString());
            result = InvokeMethodHandler.handleInvokeMethod(requestMeta, responseMeta, sessionMeta, controllerMeta);
            if (responseMeta.status == 0) {
                responseMeta.response(ResponseMeta.HttpStatus.OK, requestMeta);
            }
            if (responseMeta.contentType == null) {
                responseMeta.contentType = "text/plain;";
            }
            if (result != null) {
                if (responseMeta.body == null) {
                    responseMeta.body = result.toString();
                } else {
                    responseMeta.body += result.toString();
                }
            }
        }
    }

    /**
     * 处理响应
     */
    public static void handleResponse(RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta, ControllerMeta controllerMeta) throws Exception {
        //TODO 按照匹配模式长度进行顺序执行
        for (Filter filter : controllerMeta.filterList) {
            if (AntPatternUtil.matchFilter(requestMeta.requestURI, filter)) {
                filter.handlerInterceptor.postHandle(requestMeta, responseMeta, sessionMeta, requestMeta.invokeMethod, responseMeta.body);
            }
        }
        //处理重定向
        if (null != responseMeta.forward) {
            requestMeta.requestURI = responseMeta.forward;
            requestMeta.invokeMethod = null;
            handleRequest(requestMeta, responseMeta, sessionMeta, controllerMeta);
        }
        if (responseMeta.body != null) {
            responseMeta.contentLength = responseMeta.body.getBytes().length;
        }
        responseMeta.headers.put("Content-Type", responseMeta.contentType + "; charset=" + responseMeta.charset);
        responseMeta.headers.put("Content-Length", responseMeta.contentLength + "");
        responseMeta.headers.put("Connection", "Close");
        responseMeta.headers.put("Server", "QuickServer");
        responseMeta.headers.put("Date", new Date().toString());
        for (Filter filter : controllerMeta.filterList) {
            if (AntPatternUtil.matchFilter(requestMeta.requestURI, filter)) {
                filter.handlerInterceptor.afterCompletion(requestMeta, responseMeta, sessionMeta, requestMeta.invokeMethod);
            }
        }
    }

    /**
     * 处理BasicAuth
     */
    public static boolean handleBasicAuth(RequestMeta requestMeta, ResponseMeta responseMeta) {
        BasicAuth basicAuth = requestMeta.invokeMethod.getAnnotation(BasicAuth.class);
        if (null == basicAuth) {
            basicAuth = requestMeta.invokeMethod.getDeclaringClass().getAnnotation(BasicAuth.class);
        }
        if (null != basicAuth) {
            if (requestMeta.authorization == null) {
                responseMeta.response(ResponseMeta.HttpStatus.UNAUTHORIZED, requestMeta);
                responseMeta.headers.put("WWW-Authenticate", "Basic realm=\"" + basicAuth.realm() + "\"");
                return false;
            }
            String auth = requestMeta.authorization.substring(requestMeta.authorization.indexOf("Basic ") + 6);
            String targetAuth = new String(Base64.getEncoder().encode((basicAuth.username() + ":" + basicAuth.password()).getBytes()));
            if (!targetAuth.equals(auth)) {
                responseMeta.response(ResponseMeta.HttpStatus.UNAUTHORIZED, requestMeta);
                responseMeta.headers.put("WWW-Authenticate", "Basic realm=\"" + basicAuth.realm() + "\"");
                return false;
            }
        }
        return true;
    }

    /**
     * 处理跨域请求
     */
    private static void handleCrossOrigin(RequestMeta requestMeta, ResponseMeta responseMeta, ControllerMeta controllerMeta) {
        CrossOrigin crossOrigin = requestMeta.invokeMethod.getDeclaredAnnotation(CrossOrigin.class);
        if (null == crossOrigin) {
            crossOrigin = requestMeta.invokeMethod.getDeclaringClass().getDeclaredAnnotation(CrossOrigin.class);
        }
        if(null==crossOrigin){
            return;
        }
        //检查origin
        boolean allowOrigin = false;
        if (crossOrigin.origins().length == 0) {
            allowOrigin = true;
        } else {
            String[] origins = crossOrigin.origins();
            for (String origin : origins) {
                if (requestMeta.origin.equalsIgnoreCase(origin) || "*".equalsIgnoreCase(origin)) {
                    break;
                }
            }
        }
        if (!allowOrigin) {
            return;
        }
        //检查请求方法
        if (crossOrigin.methods().length > 0 && null!=requestMeta.accessControlRequestMethod) {
            String requestMethod = requestMeta.accessControlRequestMethod;
            boolean allowMethod = false;
            for (String method : crossOrigin.methods()) {
                if (requestMethod.equalsIgnoreCase(method)) {
                    allowMethod = true;
                    break;
                }
            }
            if (!allowMethod) {
                return;
            }
        }
        //检查请求头部
        if (crossOrigin.headers().length > 0 && null!=requestMeta.accessControlRequestHeaders) {
            String[] requestHeaders = requestMeta.accessControlRequestHeaders.split(",");
            for (String allowHeader : crossOrigin.headers()) {
                boolean exist = false;
                for (String requestHeader : requestHeaders) {
                    if (allowHeader.equals("*")||requestHeader.equals(allowHeader)) {
                        exist = true;
                        break;
                    }
                }
                if (!exist) {
                    return;
                }
            }
        }
        //缓存跨域响应头部信息
        if (!controllerMeta.crossOriginMap.containsKey(requestMeta.requestURI)) {
            //设置跨域头部信息
            Map<String, String> crossOriginMap = new HashMap<>();
            crossOriginMap.put("Access-Control-Allow-Origin", (crossOrigin.origins().length == 0 ? "*" : requestMeta.origin));
            crossOriginMap.put("Access-Control-Max-Age", crossOrigin.maxAge() + "");
            if (crossOrigin.allowCredentials()) {
                crossOriginMap.put("Access-Control-Allow-Credentials", "true");
            }
            if (crossOrigin.methods().length > 0) {
                StringBuffer stringBuffer = new StringBuffer();
                for (String method : crossOrigin.methods()) {
                    stringBuffer.append(method + ",");
                }
                stringBuffer.deleteCharAt(stringBuffer.length() - 1);
                crossOriginMap.put("Access-Control-Allow-Methods", stringBuffer.toString());
            } else {
                crossOriginMap.put("Access-Control-Allow-Methods", requestMeta.accessControlRequestMethod);
            }
            if (crossOrigin.headers().length > 0) {
                StringBuffer stringBuffer = new StringBuffer();
                for (String header : crossOrigin.headers()) {
                    stringBuffer.append(header + ",");
                }
                stringBuffer.deleteCharAt(stringBuffer.length() - 1);
                crossOriginMap.put("Access-Control-Allow-Headers", stringBuffer.toString());
            } else {
                crossOriginMap.put("Access-Control-Allow-Headers", requestMeta.accessControlRequestHeaders);
            }
            if (crossOrigin.exposedHeaders().length > 0) {
                StringBuffer stringBuffer = new StringBuffer();
                for (String exposedHeader : crossOrigin.exposedHeaders()) {
                    stringBuffer.append(exposedHeader + ",");
                }
                stringBuffer.deleteCharAt(stringBuffer.length() - 1);
                crossOriginMap.put("Access-Control-Expose-Headers", stringBuffer.toString());
            }
            controllerMeta.crossOriginMap.put(requestMeta.requestURI, crossOriginMap);
        }
        responseMeta.headers.putAll(controllerMeta.crossOriginMap.get(requestMeta.requestURI));
    }

    /**
     * 处理静态资源请求
     */
    public static void handleStaticFile(RequestMeta requestMeta, ResponseMeta responseMeta, ControllerMeta controllerMeta) throws IOException, URISyntaxException {
        URL url = CommonHandler.class.getResource(requestMeta.requestURI);
        if (null != url) {
            switch (url.getProtocol()) {
                case "file": {
                    File file = new File(url.getFile());
                    if (file.isDirectory()) {
                        return;
                    }
                    responseMeta.inputStream = url.openStream();
                }
                break;
                case "jar": {
                    JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
                    JarFile jarFile = jarURLConnection.getJarFile();
                    JarEntry jarEntry = jarFile.getJarEntry(requestMeta.requestURI.substring(1));
                    if (jarEntry == null || jarEntry.isDirectory()) {
                        return;
                    }
                    responseMeta.inputStream = jarFile.getInputStream(jarEntry);
                }
                break;
            }
            responseMeta.staticURL = url;
        } else {
            File staticFile = new File(QuickServerConfig.realPath + requestMeta.requestURI);
            if (!staticFile.exists() || staticFile.isDirectory()) {
                return;
            }
            responseMeta.inputStream = new FileInputStream(staticFile);
            responseMeta.staticURL = staticFile.toURL();
        }
        responseMeta.response(ResponseMeta.HttpStatus.OK, requestMeta);
        responseMeta.contentType = MIMEUtil.getMIMEType(requestMeta.requestURI);
        responseMeta.contentLength = responseMeta.inputStream.available();
        logger.debug("[访问静态资源]请求路径:{},资源路径:{}", requestMeta.requestURI, responseMeta.staticURL.toString());
    }
}
