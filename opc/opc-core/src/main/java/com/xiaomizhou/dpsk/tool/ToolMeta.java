package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.tool.workspace.FileSystemAccess;
import com.xiaomizhou.dpsk.tool.workspace.PathParamDecl;

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
    String category() default ToolCategory.BUILD_IN;


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

    /**
     * 文件系统访问能力位。
     * <p>
     * 默认 {@link FileSystemAccess#NONE}（不碰文件系统，跳过路径校验）。
     * 涉及文件读写的工具必须声明，否则不会被边界校验覆盖。
     *
     * @return 能力位
     */
    FileSystemAccess filesystemAccess() default FileSystemAccess.NONE;

    /**
     * 路径参数声明：标注哪些参数是文件系统路径、方向与类型。
     * <p>
     * 支持多路径（如 copy 的 from/to，方向各不相同）。
     *
     * @return 路径参数声明列表
     */
    PathParamDecl[] pathParams() default {};

}
