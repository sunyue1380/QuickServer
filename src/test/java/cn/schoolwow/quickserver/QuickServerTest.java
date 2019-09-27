package cn.schoolwow.quickserver;

import cn.schoolwow.quickhttp.QuickHttp;
import cn.schoolwow.quickhttp.connection.Connection;
import cn.schoolwow.quickhttp.response.Response;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import cn.schoolwow.quickserver.vo.User;
import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class QuickServerTest {
    private static Logger logger = LoggerFactory.getLogger(QuickServerTest.class);
    private static String host = "http://127.0.0.1:10000";

    @BeforeClass
    public static void beforeClass() throws IOException {
        QuickHttp.proxy("127.0.0.1",8888);
        QuickServer.newInstance()
                .scan("cn.schoolwow.quickserver.controller")
                .scan("cn.schoolwow.quickserver.service")
                .asyncStart();
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
            Assert.assertEquals("true",response.body());
        }
    }

    @Test
    public void testUpload() throws IOException {
        Response response= QuickHttp.connect(host+"/upload")
                .method(Connection.Method.POST)
                .data("file",new File("C:\\Windows\\System32\\drivers\\etc\\hosts"))
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
                .followRedirects(false)
                .execute();
        Assert.assertEquals("/redirect.html",response.header("Location"));
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

    @Test
    public void showUserJSON() throws IOException {
        User user = new User();
        user.setUsername("sunyue");
        user.setPassword("123456");
        Response response = QuickHttp.connect(host+"/showUserJSON")
                .method(Connection.Method.POST)
                .requestBody(JSON.toJSONString(user))
                .execute();
        Assert.assertEquals("true",response.body());
    }

    @Test
    public void showUserForm() throws IOException {
        Response response = QuickHttp.connect(host+"/showUserForm")
                .method(Connection.Method.POST)
                .data("username","sunyue")
                .data("password","123456")
                .execute();
        Assert.assertEquals("true",response.body());
    }
}