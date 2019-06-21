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

public class QuickServerTest {
    private static Logger logger = LoggerFactory.getLogger(QuickServerTest.class);
    private static String host = "http://127.0.0.1:9000";
    static{
        QuickHttp.proxy("127.0.0.1",8888);
    }

    @Test
    public void test() throws IOException {
        QuickServer.newInstance()
                .controller("cn.schoolwow.quickserver.controller")
                .webapps("C:/Users/admin/IdeaProjects/QuickServer/src/test/java/webapps")
                .start();
    }

    @Test
    public void testRegister() throws IOException {
        Response response= QuickHttp.connect(host+"/register")
                .method(Connection.Method.POST)
                .data("username","quickserver")
                .data("password","123456")
                .execute();
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
}