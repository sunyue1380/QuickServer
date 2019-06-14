package cn.schoolwow.quickserver;

import org.junit.Test;

import java.io.IOException;

public class QuickServerTest {

    @Test
    public void test() throws IOException {
        QuickServer.newInstance()
                .controller("cn.schoolwow.quickserver.controller")
                .webapps("C:/Users/admin/IdeaProjects/QuickServer/src/test/java/webapps")
                .start();
    }
}