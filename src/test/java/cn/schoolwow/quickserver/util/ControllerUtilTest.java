package cn.schoolwow.quickserver.util;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class ControllerUtilTest {

    @Test
    public void register() throws IOException, ClassNotFoundException {
        ControllerUtil.register("cn.schoolwow.quickserver.controller");
    }
}