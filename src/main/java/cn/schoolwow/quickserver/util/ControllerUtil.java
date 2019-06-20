package cn.schoolwow.quickserver.util;

import cn.schoolwow.quickserver.annotation.RequestMapping;
import cn.schoolwow.quickserver.annotation.RequestMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ControllerUtil {
    private static Logger logger = LoggerFactory.getLogger(ControllerUtil.class);
    private static Map<String, Method> requestMappingHandler = new HashMap<>();

    public static Method getMethod(String requestUrl){
        return requestMappingHandler.get(requestUrl);
    }

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
        }catch (Exception e){
            e.printStackTrace();
            throw new IllegalArgumentException(e.getMessage());
        }
        for (Class c : classList) {
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
                requestMappingHandler.put(mappingUrl,method);
                RequestMethod[] requestMethods = methodRequestMapping.method();
                logger.info("[注册Controller][[{}],{}] onto {}",mappingUrl,requestMethods.length==0?"":"method="+requestMethods,method.toString());
            }
        }
    }
}
