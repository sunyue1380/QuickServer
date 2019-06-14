package cn.schoolwow.quickserver.annotation;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {
    /**参数值*/
    String value();
    /**是否必须*/
    boolean required() default true;
    /**参数默认值*/
    String defaultValue() default "";
}
