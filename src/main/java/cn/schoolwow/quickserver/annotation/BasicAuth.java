package cn.schoolwow.quickserver.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BasicAuth {
    /**用户名*/
    String username();
    /**密码*/
    String password();
    /**描述*/
    String realm() default "quickServer Basic Auth";
}
