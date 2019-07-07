package cn.schoolwow.quickserver;

import cn.schoolwow.quickserver.util.AntPatternUtil;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class QuickServerTest {
    private static Logger logger = LoggerFactory.getLogger(QuickServerTest.class);

    @Test
    public void test() throws IOException {
        QuickServer.newInstance()
                .scan("cn.schoolwow.quickserver.controller")
                .scan("cn.schoolwow.quickserver.service")
                .start();
    }

    @Test
    public void testPathVariable(){
        String requestURI = "/restful/script/2";
        String requestMappingHander = "/restful/{entity}/{id}";
        String antRequestUrl = requestMappingHander;

        int urlPos=0,mappingPos=0,lastUrlPos=0,lastMappingPos=0;
        while(urlPos<requestURI.length()&&mappingPos<requestMappingHander.length()){
            if(requestMappingHander.charAt(mappingPos)=='{'){
                lastUrlPos=urlPos;
                lastMappingPos=mappingPos+1;

                while(mappingPos<requestMappingHander.length()&&requestMappingHander.charAt(mappingPos)!='}'){
                    mappingPos++;
                }
                if(mappingPos<requestMappingHander.length()){
                    //提取变量名
                    String name = requestMappingHander.substring(lastMappingPos,mappingPos);
                    System.out.println("提取变量名:"+name);
                    antRequestUrl = antRequestUrl.replace("{"+name+"}","*");
                    //提取变量值
                    if(mappingPos+1<requestMappingHander.length()){
                        while(urlPos<requestURI.length()&&requestURI.charAt(urlPos)!=requestMappingHander.charAt(mappingPos+1)){
                            urlPos++;
                        }
                        if(urlPos<requestURI.length()){
                            System.out.println("变量值:"+requestURI.substring(lastUrlPos,urlPos));
                        }
                    }else{
                        //末尾
                        System.out.println("变量值:"+requestURI.substring(lastUrlPos));
                    }
                }
            }else if(requestURI.charAt(urlPos)==requestMappingHander.charAt(mappingPos)){
                urlPos++;
                mappingPos++;
            }else{
                mappingPos++;
            }
        }
        System.out.println(antRequestUrl);
    }

    @Test
    public void testAntPattern() {
        Map<String, String[]> map = new HashMap<>();
        map.put("com/t?st.jsp", new String[]{"com/test.jsp", "com/tast.jsp", "com/txst.jsp"});
        map.put("com/*.jsp", new String[]{"com/test.jsp"});
        map.put("com/**/test.jsp", new String[]{"com/test.jsp", "com/example/test.jsp"});
        map.put("org/springframework/**/*.jsp", new String[]{"org/springframework/example/t.jsp", "org/springframework/t.jsp"});
        map.put("org/**/servlet/bla.jsp", new String[]{"org/springframework/servlet/bla.jsp", "org/springframework/testing/servlet/bla.jsp", "org/servlet/bla.jsp"});
        Set<String> patternSet = map.keySet();
        for (String pattern : patternSet) {
            String[] urls = map.get(pattern);
            for (String url : urls) {
                logger.info("[判断路径匹配]请求路径:{},拦截器路径:{}", url, pattern);
                Assert.assertEquals(true, AntPatternUtil.doMatch(url, pattern));
            }
        }
    }
}