package cn.schoolwow.quickserver.response;

import org.junit.Test;

import java.net.HttpCookie;

public class ResponseHandlerTest {
    @Test
    public void testHttpCookie() {
        HttpCookie httpCookie = new HttpCookie("name", "value");
        httpCookie.setMaxAge(-1);
        httpCookie.setSecure(false);
        httpCookie.setPath("/");
        httpCookie.setDomain("");
        httpCookie.setComment("hahaha");
        httpCookie.setCommentURL("http://www.baidu.com");
        httpCookie.setVersion(1);
        System.out.println(httpCookie.toString());
    }
}