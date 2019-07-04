package cn.schoolwow.quickserver.controller;

import cn.schoolwow.quickbeans.annotation.Component;
import cn.schoolwow.quickserver.annotation.RequestMapping;
import cn.schoolwow.quickserver.annotation.RequestMethod;
import cn.schoolwow.quickserver.response.ResponseMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ForwardController {
    private Logger logger = LoggerFactory.getLogger(ForwardController.class);

    @RequestMapping(value = "/forward",method = RequestMethod.GET)
    public String forward(
            ResponseMeta responseMeta
    ){
        logger.info("[转发]目标:{},","/redirect.html");
        return "/redirect.html";
    }
}
