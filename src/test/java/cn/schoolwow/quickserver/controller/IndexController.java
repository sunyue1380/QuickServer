package cn.schoolwow.quickserver.controller;

import cn.schoolwow.quickserver.annotation.RequestMapping;
import cn.schoolwow.quickserver.annotation.RequestParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequestMapping("/")
public class IndexController {
    private Logger logger = LoggerFactory.getLogger(IndexController.class);
    @RequestMapping(value = "")
    public String index(
            @RequestParam("name") String name
    ){
        logger.info("[读取参数]name:{}",name);
        return "hello world! your name:"+name;
    }
}
