package cn.schoolwow.quickserver.util;

import cn.schoolwow.quickserver.annotation.Interceptor;
import cn.schoolwow.quickserver.annotation.RequestMapping;
import cn.schoolwow.quickserver.annotation.RequestMethod;
import cn.schoolwow.quickserver.domain.Filter;
import cn.schoolwow.quickserver.domain.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ControllerUtil {
    private static Logger logger = LoggerFactory.getLogger(ControllerUtil.class);
    /**控制器映射*/
    public static Map<String, Request> requestMappingHandler = new HashMap<>();
    /**拦截器映射*/
    public static List<Filter> filterList = new ArrayList<>();
    /**缓存跨域头*/
    public static Map<String, Map<String,String>> crossOriginMap = new ConcurrentHashMap<>();

    /**注册Controller类*/
    public static void register(String packageName) {
        List<Class> classList = new ArrayList<>();
        String packageNamePath = packageName.replace(".", "/");
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL url = classLoader.getResource(packageNamePath);
        if(url==null){
            throw new IllegalArgumentException("无法识别的包路径:"+packageNamePath);
        }
        try {
            if("file".equals(url.getProtocol())){
                File file = new File(url.getFile());
                //TODO 对于有空格或者中文路径会无法识别
                logger.info("[扫描Controller路径]{}",file.getAbsolutePath());
                if(!file.isDirectory()){
                    throw new IllegalArgumentException("包名不是合法的文件夹!"+url.getFile());
                }
                Stack<File> stack = new Stack<>();
                stack.push(file);

                String indexOfString = packageName.replace(".","/");
                while(!stack.isEmpty()){
                    file = stack.pop();
                    for(File f:file.listFiles()){
                        if(f.isDirectory()){
                            stack.push(f);
                        }else if(f.isFile()&&f.getName().endsWith(".class")){
                            String path = f.getAbsolutePath().replace("\\","/");
                            int startIndex = path.indexOf(indexOfString);
                            String className = path.substring(startIndex,path.length()-6).replace("/",".");
                            classList.add(Class.forName(className));
                        }
                    }
                }
            }else if("jar".equals(url.getProtocol())){
                JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
                if (null != jarURLConnection) {
                    JarFile jarFile = jarURLConnection.getJarFile();
                    if (null != jarFile) {
                        Enumeration<JarEntry> jarEntries = jarFile.entries();
                        while (jarEntries.hasMoreElements()) {
                            JarEntry jarEntry = jarEntries.nextElement();
                            String jarEntryName = jarEntry.getName();
                            if (jarEntryName.contains(packageNamePath) && jarEntryName.endsWith(".class")) {
                                String className = jarEntryName.substring(0, jarEntryName.lastIndexOf(".")).replaceAll("/", ".");
                                classList.add(Class.forName(className));
                            }
                        }
                    }
                }
            }
            if(classList.isEmpty()){
                return;
            }
            for (Class c : classList) {
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
                        logger.info("[注册Controller][{}] onto {}",mappingUrl,method.toString());
                    }else{
                        logger.info("[注册Controller][{},method={}] onto {}",mappingUrl,requestMethods,method.toString());
                    }
                    Request request = new Request();
                    request.instance = c.newInstance();
                    request.mappingUrl = mappingUrl;
                    request.method = method;
                    requestMappingHandler.put(mappingUrl,request);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new IllegalArgumentException(e.getMessage());
        }

    }
}
