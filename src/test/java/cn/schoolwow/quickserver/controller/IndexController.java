package cn.schoolwow.quickserver.controller;

import cn.schoolwow.quickbeans.annotation.Component;
import cn.schoolwow.quickserver.annotation.*;
import cn.schoolwow.quickserver.request.MultipartFile;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.service.IndexService;
import cn.schoolwow.quickserver.session.SessionMeta;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import cn.schoolwow.quickserver.vo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;

@Component
@ResponseBody
public class IndexController {
    private Logger logger = LoggerFactory.getLogger(IndexController.class);
    @Resource
    private IndexService indexService;

    @RequestMapping(value = "/register",method = RequestMethod.POST)
    public boolean register(
            @RequestParam(name = "username") String username,
            @RequestParam(name = "password") String password
    ){
        logger.info("[注册用户]用户名:{},密码:{}",username,password);
        return indexService.register(username,password);
    }

    @RequestMapping(value = "/login",method = RequestMethod.GET)
    public boolean login(
            @RequestParam(name = "username") String username,
            @RequestParam(name = "password") String password,
            SessionMeta sessionMeta
    ){
        logger.info("[登陆用户]用户名:{},密码:{}",username,password);
        return indexService.login(username,password,sessionMeta);
    }

    @RequestMapping(value = "/showUserInfo",method = RequestMethod.GET)
    public long showUserInfo(
            @SessionAttribute(name = "userId") long userId,
            @CookieValue(name = QuickServerConfig.SESSION) String sessionId,
            @RequestHeader(name = "Content-Type") String contentType,
            SessionMeta sessionMeta
    ){
        logger.info("[查看会话]会话id:{},属性:{}",sessionId,sessionMeta.attributes);
        logger.info("[查看用户名]userId:{}",userId);
        logger.info("[查看Header]Content-Type:{}",contentType);
        return userId;
    }

    @RequestMapping(value = "/upload",method = RequestMethod.POST)
    public boolean upload(
            @RequestPart(name = "file") MultipartFile multipartFile,
            @RequestParam(name = "username") String username,
            @RequestParam(name = "password") String password,
            SessionMeta sessionMeta
            ){
        logger.info("[读取普通字段]username:{},password:{}",username,password);
        logger.info("[上传文件]文件对象:{}",multipartFile);
        File file = new File(sessionMeta.getRealPath()+"/files/"+multipartFile.originalFilename);
        if(!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        multipartFile.transferTo(file);
        return file.exists();
    }

    @RequestMapping(value = "/redirect",method = RequestMethod.GET)
    public void redirect(
            ResponseMeta responseMeta
    ){
        logger.info("[重定向]目标:{},","/redirect.html");
        responseMeta.redirect("/redirect.html");
    }

    @RequestMapping(value = "/forward",method = RequestMethod.GET)
    public void forward(
            ResponseMeta responseMeta
    ){
        logger.info("[转发]目标:{},","/redirect.html");
        responseMeta.forward("/redirect.html");
    }

    @RequestMapping(value = "/crossOrigin",method = {RequestMethod.GET,RequestMethod.POST})
    @CrossOrigin
    public String crossOrigin(
            @RequestParam(name = "username") String username
    ){
        logger.info("[跨域请求]用户名:{}",username);
        return "true";
    }

    @RequestMapping(value = "/basicAuth",method = {RequestMethod.GET,RequestMethod.POST})
    @BasicAuth(username = "quickserver",password = "123456")
    public String basicAuth(
            @RequestHeader(name = "Authorization") String authorization
    ){
        logger.info("[basicAuth]Authorization:{}",authorization);
        return "true";
    }

    @RequestMapping(value = "/requestBody",method = {RequestMethod.POST})
    public String requestBody(
            @RequestBody String body
    ){
        logger.info("[requestBody]requestBody:{}",body);
        return "true";
    }

    @RequestMapping(value = "/parameterCast",method = {RequestMethod.POST})
    public String parameterCast(
            @RequestParam(name = "age") int age,
            @RequestParam(name = "size") long size,
            @RequestParam(name = "phone") String phone,
            @RequestParam(name = "time",pattern = "yyyy-MM-dd") Date date
    ){
        logger.info("[参数类型转换]age:{},size:{},phone:{},time:{}",age,size,phone,date);
        return "true";
    }

    @RequestMapping(value = "/pathVariable/{userId}",method = {RequestMethod.GET})
    public boolean pathVariable(
            @PathVariable(name = "userId") int userId
    ){
        logger.info("[路径变量]userId:{}",userId);
        return true;
    }

    @RequestMapping(value = "/showUserJSON",method = {RequestMethod.POST})
    public boolean showUserJSON(
            @RequestBody User user
    ){
        logger.info("[显示用户]user:{}",user);
        return true;
    }

    @RequestMapping(value = "/showUserForm",method = {RequestMethod.POST})
    public boolean showUserForm(
            User user
    ){
        logger.info("[显示用户]user:{}",user);
        return true;
    }
}
