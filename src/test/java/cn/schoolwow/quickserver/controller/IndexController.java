package cn.schoolwow.quickserver.controller;

import cn.schoolwow.quickserver.annotation.RequestMapping;
import cn.schoolwow.quickserver.annotation.RequestMethod;
import cn.schoolwow.quickserver.annotation.RequestParam;
import cn.schoolwow.quickserver.annotation.RequestPart;
import cn.schoolwow.quickserver.request.MultipartFile;
import cn.schoolwow.quickserver.session.SessionMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class IndexController {
    private Logger logger = LoggerFactory.getLogger(IndexController.class);
    @RequestMapping(value = "/register",method = RequestMethod.POST)
    public boolean register(
            @RequestParam("username") String username,
            @RequestParam("password") String password
    ){
        logger.info("[注册用户]用户名:{},密码:{}",username,password);
        if("quickserver".equalsIgnoreCase(username)&&
        "123456".equalsIgnoreCase(password)){
            return true;
        }else{
            return false;
        }
    }

    @RequestMapping(value = "/login",method = RequestMethod.GET)
    public boolean login(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            SessionMeta sessionMeta
    ){
        logger.info("[登陆用户]用户名:{},密码:{}",username,password);
        if("quickserver".equalsIgnoreCase(username)&&
                "123456".equalsIgnoreCase(password)){
            sessionMeta.attributes.put("username",username);
            logger.info("[会话添加属性]会话id:{},属性:{}", sessionMeta.id,sessionMeta.attributes);
            return true;
        }else{
            return false;
        }
    }

    @RequestMapping(value = "/showUserInfo",method = RequestMethod.GET)
    public String showUserInfo(
            SessionMeta sessionMeta
    ){
        logger.info("[查看会话属性]会话id:{},属性:{}",sessionMeta.id,sessionMeta.attributes);
        return sessionMeta.attributes.get("username");
    }

    @RequestMapping(value = "/upload",method = RequestMethod.POST)
    public boolean upload(
            @RequestPart(name = "file") MultipartFile multipartFile,
            @RequestParam("username") String username,
            @RequestParam("password") String password
            ){
        logger.info("[读取普通字段]username:{},password:{}",username,password);
        logger.info("[上传文件]文件对象:{}",multipartFile);
        File file = new File(new File(".").getAbsolutePath()+"/files/"+multipartFile.originalFilename);
        multipartFile.transferTo(file);
        return file.exists();
    }
}
