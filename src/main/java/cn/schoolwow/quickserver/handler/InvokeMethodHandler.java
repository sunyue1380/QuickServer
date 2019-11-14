package cn.schoolwow.quickserver.handler;

import cn.schoolwow.quickserver.annotation.*;
import cn.schoolwow.quickserver.domain.Request;
import cn.schoolwow.quickserver.exception.BusinessException;
import cn.schoolwow.quickserver.request.MultipartFile;
import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.session.SessionMeta;
import cn.schoolwow.quickserver.util.AntPatternUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InvokeMethodHandler {
    private static Logger logger = LoggerFactory.getLogger(InvokeMethodHandler.class);

    private static SimpleDateFormat sdf = new SimpleDateFormat();

    /**
     * 基本数据类型数组类型
     */
    private static Class[] basicDataTypeClassList = new Class[]{boolean[].class, byte[].class, char[].class, short[].class, int[].class, long[].class, float[].class, double[].class};

    /**
     * 获取InvokeMethod
     */
    public static void getInvokeMethod(RequestMeta requestMeta, ControllerMeta controllerMeta) {
        Collection<Request> requestCollection = controllerMeta.requestMappingHandler.values();
        for (Request request : requestCollection) {
            int urlPos = 0, mappingPos = 0, lastUrlPos = 0, lastMappingPos = 0;
            String requestURI = requestMeta.requestURI;
            String mappingUrl = request.mappingUrl;
            String antRequestUrl = mappingUrl;
            while (urlPos < requestURI.length() && mappingPos < mappingUrl.length()) {
                if (mappingUrl.charAt(mappingPos) == '{') {
                    lastUrlPos = urlPos;
                    lastMappingPos = mappingPos + 1;

                    while (mappingPos < mappingUrl.length() && mappingUrl.charAt(mappingPos) != '}') {
                        mappingPos++;
                    }
                    if (mappingPos < mappingUrl.length()) {
                        //提取变量名
                        String name = mappingUrl.substring(lastMappingPos, mappingPos);
                        antRequestUrl = antRequestUrl.replace("{" + name + "}", "*");
                        String value = null;
                        //提取变量值
                        if (mappingPos + 1 < mappingUrl.length()) {
                            while (urlPos < requestURI.length() && requestURI.charAt(urlPos) != mappingUrl.charAt(mappingPos + 1)) {
                                urlPos++;
                            }
                            if (urlPos < requestURI.length()) {
                                value = requestURI.substring(lastUrlPos, urlPos);
                            }
                        } else {
                            value = requestURI.substring(lastUrlPos);
                        }
                        requestMeta.pathVariable.put(name, value);
                    }
                } else if (requestURI.charAt(urlPos) == mappingUrl.charAt(mappingPos)) {
                    urlPos++;
                    mappingPos++;
                } else {
                    mappingPos++;
                }
            }
            if ((mappingUrl.contains("{") && AntPatternUtil.doMatch(requestURI, antRequestUrl))
                    || requestURI.equals(mappingUrl)) {
                if (request.requestMethods.length == 0) {
                    requestMeta.invokeMethod = request.method;
                } else {
                    //判断请求方法是否匹配
                    for (RequestMethod requestMethod : request.requestMethods) {
                        if (requestMethod.name().equals(requestMeta.method)) {
                            requestMeta.invokeMethod = request.method;
                            break;
                        }
                    }
                }
            }
            if (requestMeta.invokeMethod == null) {
                continue;
            }
            requestMeta.request = request;
            break;
        }
    }

    /**
     * 判断是否支持该方法
     */
    public static boolean supportRequestMethod(RequestMeta requestMeta, ResponseMeta responseMeta) {
        RequestMethod[] requestMethods = requestMeta.request.requestMethods;
        boolean support = false;
        if (requestMethods.length == 0 || (requestMeta.origin != null && RequestMethod.OPTIONS.name().equalsIgnoreCase(requestMeta.method))) {
            support = true;
        } else {
            for (RequestMethod requestMethod : requestMethods) {
                if (requestMethod.name().equals(requestMeta.method)) {
                    support = true;
                    break;
                }
            }
        }
        if (!support) {
            responseMeta.response(ResponseMeta.HttpStatus.METHOD_NOT_ALLOWED, requestMeta);
        }
        return support;
    }

    /**
     * 处理方法调用
     */
    public static Object handleInvokeMethod(RequestMeta requestMeta, ResponseMeta responseMeta, SessionMeta sessionMeta, ControllerMeta controllerMeta) throws Exception {
        Object controller = requestMeta.request.instance;
        Parameter[] parameters = requestMeta.invokeMethod.getParameters();
        Object result = null;
        try {
            if (parameters.length == 0) {
                result = requestMeta.invokeMethod.invoke(controller);
            } else {
                List<Object> parameterList = new ArrayList<>();
                for (Parameter parameter : parameters) {
                    String parameterType = parameter.getType().getName();
                    switch (parameterType) {
                        case "cn.schoolwow.quickserver.request.RequestMeta": {
                            parameterList.add(requestMeta);
                        }
                        break;
                        case "cn.schoolwow.quickserver.response.ResponseMeta": {
                            parameterList.add(responseMeta);
                        }
                        break;
                        case "cn.schoolwow.quickserver.session.SessionMeta": {
                            parameterList.add(sessionMeta);
                        }
                        break;
                        default: {
                            if (handleRequestParam(parameter, parameterList, requestMeta)) {
                                continue;
                            }
                            if (handleRequestPart(parameter, parameterList, requestMeta)) {
                                continue;
                            }
                            if (handleRequestBody(parameter, parameterList, requestMeta)) {
                                continue;
                            }
                            if (handleSessionAttribute(parameter, parameterList, sessionMeta)) {
                                continue;
                            }
                            if (handleCookieValue(parameter, parameterList, requestMeta)) {
                                continue;
                            }
                            if (handleRequestHeader(parameter, parameterList, requestMeta)) {
                                continue;
                            }
                            if (handlePathVariable(parameter, parameterList, requestMeta)) {
                                continue;
                            }
                            handleCompositParameter(parameter, parameterList, requestMeta);
                        }
                    }
                }
                result = requestMeta.invokeMethod.invoke(controller, parameterList.toArray(new Object[]{parameterList.size()}));
            }
        } catch (BusinessException e) {
            responseMeta.response(e.httpStatus, requestMeta);
            responseMeta.body = e.body;
        } catch (Exception e) {
            e.printStackTrace();
            result = e;
        }
        if (null != controllerMeta.responseBodyAdvice && null == responseMeta.staticURL) {
            if (controllerMeta.responseBodyAdvice.support(requestMeta.invokeMethod)) {
                result = controllerMeta.responseBodyAdvice.beforeBodyWrite(result, requestMeta.invokeMethod, requestMeta, responseMeta, sessionMeta);
            }
        }
        if (result == null) {
            return null;
        }
        //处理ResponseBody注解
        ResponseBody responseBody = controller.getClass().getDeclaredAnnotation(ResponseBody.class);
        if (null==responseBody) {
            responseBody = requestMeta.invokeMethod.getAnnotation(ResponseBody.class);
        }
        if (null==responseBody) {
            if(result.toString().startsWith("redirect:")){
                responseMeta.redirect(result.toString().substring("redirect:".length()));
            }else{
                responseMeta.forward(result.toString());
            }
            result = null;
        } else {
            switch (responseBody.value()) {
                case String: {}break;
                case JSON: {
                    result = JSON.toJSONString(result, SerializerFeature.WriteMapNullValue);
                    responseMeta.contentType = "application/json";
                }
            }
        }
        return result;
    }

    private static boolean handleRequestParam(Parameter parameter, List<Object> parameterList, RequestMeta requestMeta) throws BusinessException {
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        if (null == requestParam) {
            return false;
        }
        String requestParameter = requestMeta.parameters.get(requestParam.name());
        if (requestParam.required() && requestParameter == null) {
            throw new BusinessException(ResponseMeta.HttpStatus.BAD_REQUEST, "请求参数[" + requestParam.name() + "]不能为空!");
        }
        if (requestParameter == null) {
            requestParameter = requestParam.defaultValue();
        }
        parameterList.add(castParameter(parameter, requestParameter, requestParam.pattern()));
        return true;
    }

    private static boolean handleRequestPart(Parameter parameter, List<Object> parameterList, RequestMeta requestMeta) throws BusinessException {
        RequestPart requestPart = parameter.getAnnotation(RequestPart.class);
        if (null == requestPart) {
            return false;
        }
        MultipartFile multipartFile = requestMeta.fileParameters.get(requestPart.name());
        if (requestPart.required() && multipartFile == null) {
            throw new BusinessException(ResponseMeta.HttpStatus.BAD_REQUEST, "请求参数[" + requestPart.name() + "]不能为空!");
        }
        parameterList.add(multipartFile);
        return true;
    }

    private static boolean handleRequestBody(Parameter parameter, List<Object> parameterList, RequestMeta requestMeta) throws ClassNotFoundException {
        RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
        if (null == requestBody) {
            return false;
        }
        if(null==requestMeta.body&&requestBody.required()){
            throw new IllegalArgumentException("RequestBody提取失败!参数:"+parameter.getName()+",调用方法:"+requestMeta.invokeMethod.toString());
        }
        String parameterType = parameter.getType().getName();
        if ("java.lang.String".equals(parameterType)) {
            parameterList.add(requestMeta.body);
        } else if ("com.alibaba.fastjson.JSONObject".equals(parameterType)) {
            parameterList.add(JSON.parseObject(requestMeta.body));
        } else if ("com.alibaba.fastjson.JSONArray".equals(parameterType)) {
            parameterList.add(JSON.parseArray(requestMeta.body));
        } else if ("java.io.InputStream".equals(parameterType)) {
            parameterList.add(requestMeta.inputStream);
        } else if ("java.util.List".equals(parameterType)) {
            Type type = parameter.getParameterizedType();
            ParameterizedType pt = (ParameterizedType) type;
            Type[] actualTypes = pt.getActualTypeArguments();
            parameterList.add(JSON.parseArray(requestMeta.body).toJavaList(Class.forName(actualTypes[0].getTypeName())));
        } else if (parameterType.startsWith("[")) {
            Class clazz = getArrayClass(parameterType);
            JSONArray bodyArray = JSON.parseArray(requestMeta.body);
            Object array = Array.newInstance(clazz.getComponentType(),bodyArray.size());
            for(int i=0;i<bodyArray.size();i++){
                Array.set(array,i,bodyArray.get(i));
            }
            parameterList.add(array);
        } else {
            parameterList.add(JSON.parseObject(requestMeta.body).toJavaObject(parameter.getType()));
        }
        return true;
    }

    private static boolean handleSessionAttribute(Parameter parameter, List<Object> parameterList, SessionMeta sessionMeta) throws BusinessException {
        SessionAttribute sessionAttribute = parameter.getAnnotation(SessionAttribute.class);
        if (null == sessionAttribute) {
            return false;
        }
        try {
            parameterList.add(castParameter(parameter, sessionMeta.attributes.get(sessionAttribute.name()), null));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean handleCookieValue(Parameter parameter, List<Object> parameterList, RequestMeta requestMeta) throws BusinessException {
        CookieValue cookieValue = parameter.getAnnotation(CookieValue.class);
        if (null == cookieValue) {
            return false;
        }
        parameterList.add(requestMeta.cookies.get(cookieValue.name()));
        return true;
    }

    private static boolean handleRequestHeader(Parameter parameter, List<Object> parameterList, RequestMeta requestMeta) throws BusinessException {
        RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
        if (null == requestHeader) {
            return false;
        }
        String value = requestMeta.headers.get(requestHeader.name());
        parameterList.add(castParameter(parameter, value, null));
        return true;
    }

    private static boolean handlePathVariable(Parameter parameter, List<Object> parameterList, RequestMeta requestMeta) throws BusinessException {
        PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
        if (null == pathVariable) {
            return false;
        }
        if (pathVariable.required() && !requestMeta.pathVariable.containsKey(pathVariable.name())) {
            throw new BusinessException(ResponseMeta.HttpStatus.BAD_REQUEST, "路径变量[" + pathVariable.name() + "]不能为空!");
        }
        parameterList.add(castParameter(parameter, requestMeta.pathVariable.get(pathVariable.name()), null));
        return true;
    }

    private static void handleCompositParameter(Parameter parameter, List<Object> parameterList, RequestMeta requestMeta) throws BusinessException {
        try {
            Object instance = parameter.getType().newInstance();
            Field[] fields = parameter.getType().getDeclaredFields();
            Field.setAccessible(fields, true);
            for (Field field : fields) {
                if (requestMeta.parameters.containsKey(field.getName())) {
                    field.set(instance, requestMeta.parameters.get(field.getName()));
                }
            }
            parameterList.add(instance);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 转换参数类型
     */
    private static Object castParameter(Parameter parameter, String requestParameter, String datePattern) {
        try {
            if (parameter.getType().isPrimitive()) {
                switch (parameter.getType().getName()) {
                    case "boolean": {
                        return Boolean.parseBoolean(requestParameter);
                    }
                    case "byte": {
                        return Byte.parseByte(requestParameter);
                    }
                    case "char": {
                        return requestParameter.charAt(0);
                    }
                    case "short": {
                        return Short.parseShort(requestParameter);
                    }
                    case "int": {
                        return Integer.parseInt(requestParameter);
                    }
                    case "long": {
                        return Long.parseLong(requestParameter);
                    }
                    case "float": {
                        return Float.parseFloat(requestParameter);
                    }
                    case "double": {
                        return Double.parseDouble(requestParameter);
                    }
                    default: {
                        return null;
                    }
                }
            } else if ("java.util.Date".equals(parameter.getType().getName())) {
                sdf.applyPattern(datePattern);
                return sdf.parse(requestParameter);
            } else {
                return parameter.getType().getConstructor(String.class).newInstance(requestParameter);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取数组类型
     */
    public static Class getArrayClass(String parameterType) throws ClassNotFoundException {
        if (parameterType.startsWith("[L")) {
            String className = parameterType.substring(2, parameterType.length() - 1);
            return Class.forName(className);
        }
        for (Class _class : basicDataTypeClassList) {
            if (parameterType.equals(_class.getName())) {
                return _class;
            }
        }
        throw new IllegalArgumentException("不支持的数组参数类型!类型:" + parameterType);
    }
}
