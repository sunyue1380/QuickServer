package cn.schoolwow.quickserver;

import cn.schoolwow.quickhttp.QuickHttp;
import cn.schoolwow.quickhttp.connection.Connection;
import cn.schoolwow.quickhttp.response.Response;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class QuickClientTest {
    private static Logger logger = LoggerFactory.getLogger(QuickClientTest.class);
    private static String host = "http://127.0.0.1:9000";
    static{
        QuickHttp.proxy("127.0.0.1",8888);
    }

    @Test
    public void testRegister() throws IOException {
        Response response= QuickHttp.connect(host+"/register")
                .method(Connection.Method.POST)
                .data("username","quickserver")
                .data("password","123456")
                .execute();
        System.out.println(response.body());
        Assert.assertEquals("true",response.body());
    }

    @Test
    public void testLogin() throws IOException {
        {
            Response response= QuickHttp.connect(host+"/login")
                    .data("username","quickserver")
                    .data("password","123456")
                    .execute();
            Assert.assertEquals(true,response.hasCookie(QuickServerConfig.SESSION));
        }
        {
            Response response= QuickHttp.connect(host+"/showUserInfo")
                    .execute();
            Assert.assertEquals("quickserver",response.body());
        }
    }

    @Test
    public void testUpload() throws IOException {
        Response response= QuickHttp.connect(host+"/upload")
                .method(Connection.Method.POST)
                .data("file",new File("C:\\Users\\admin\\Pictures\\棋牌社区APP_slices\\#1.png"))
                .data("username","aaaa")
                .data("password","bbbb")
                .execute();
        logger.debug("[body]{}",response.body());
        Assert.assertEquals("true",response.body());
    }

    @Test
    public void testRedirect() throws IOException {
        Response response= QuickHttp.connect(host+"/redirect")
                .method(Connection.Method.GET)
                .execute();
        Assert.assertEquals(host+"/redirect.html",response.url());
    }

    @Test
    public void testForward() throws IOException {
        Response response= QuickHttp.connect(host+"/forward")
                .method(Connection.Method.GET)
                .execute();
        Assert.assertEquals(host+"/forward",response.url());
    }

    @Test
    public void testCrossOrigin() throws IOException {
        Response response = QuickHttp.connect(host+"/crossOrigin")
                .method(Connection.Method.GET)
                .header("origin",host)
                .data("username","quickserver")
                .execute();
        Assert.assertEquals("true",response.body());
    }

    @Test
    public void testRequestBody() throws IOException {
        Response response = QuickHttp.connect(host+"/requestBody")
                .method(Connection.Method.POST)
                .requestBody("这是RequestBody中文字符串")
                .execute();
        Assert.assertEquals("true",response.body());
    }

    @Test
    public void testParameterCast() throws IOException {
        Response response = QuickHttp.connect(host+"/parameterCast")
                .method(Connection.Method.POST)
                .data("age","10")
                .data("size","10000")
                .data("phone","123456789")
                .data("time","2019-06-25")
                .execute();
        Assert.assertEquals("true",response.body());
    }

    @Test
    public void testPathVariable() throws IOException {
        Response response = QuickHttp.connect(host+"/pathVariable/1")
                .method(Connection.Method.GET)
                .execute();
        Assert.assertEquals("true",response.body());
    }
}