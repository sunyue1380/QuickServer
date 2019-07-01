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
                .webapps("C:/Users/admin/IdeaProjects/QuickServer/src/test/java/webapps")
                .start();
    }

    @Test
    public void testAntPattern(){
        Map<String,String[]> map = new HashMap<>();
        map.put("com/t?st.jsp",new String[]{"com/test.jsp","com/tast.jsp","com/txst.jsp"});
        map.put("com/*.jsp",new String[]{"com/test.jsp"});
        map.put("com/**/test.jsp",new String[]{"com/test.jsp","com/example/test.jsp"});
        map.put("org/springframework/**/*.jsp",new String[]{"org/springframework/example/t.jsp","org/springframework/t.jsp"});
        map.put("org/**/servlet/bla.jsp",new String[]{"org/springframework/servlet/bla.jsp","org/springframework/testing/servlet/bla.jsp","org/servlet/bla.jsp"});
        Set<String> patternSet = map.keySet();
        for(String pattern:patternSet) {
            String[] urls = map.get(pattern);
            for (String url : urls) {
                logger.info("[判断路径匹配]请求路径:{},拦截器路径:{}", url, pattern);
                Assert.assertEquals(true, AntPatternUtil.doMatch(url, pattern));
            }
        }
    }
}