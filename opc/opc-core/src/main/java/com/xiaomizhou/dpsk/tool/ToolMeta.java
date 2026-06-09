package com.xiaomizhou.dpsk.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1 15:07
 * @description
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolMeta {

    String value() default "description";

    /**
     * 风险
     *
     * @return
     */
    String level() default "normal";

    /**
     * 工具分类
     *
     * @return
     */
    String category() default "";


    boolean cacheable() default false;

    /**
     * tags of the tool.
     *
     * @return
     */
    String[] tags() default "";

    /**
     * executing time 60s
     * default
     *
     * @return
     */
    long timeout() default 60;

}
