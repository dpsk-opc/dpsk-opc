package com.xiaomizhou.dpsk.tool.workspace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明工具方法中"哪些参数是文件系统路径"，以及访问方向与路径类型。
 * <p>
 * 挂载位置：{@code @ToolMeta(pathParams = { @PathParamDecl(...) })}。
 * <p>
 * 设计要点：<b>不按工具名/类名枚举</b>，由工具自我声明，新增工具无需改动安全逻辑。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PathParamDecl {

    /**
     * 参数名（需与工具方法的参数名一致）。
     */
    String name();

    /**
     * 访问方向，默认读。
     */
    PathDirection direction() default PathDirection.READ;

    /**
     * 路径类型，默认文件。
     */
    PathKind kind() default PathKind.FILE;
}
