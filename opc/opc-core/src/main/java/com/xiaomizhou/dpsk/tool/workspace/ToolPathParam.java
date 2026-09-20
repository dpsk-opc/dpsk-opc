package com.xiaomizhou.dpsk.tool.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路径参数声明的运行期表示（由 {@link PathParamDecl} 注解解析而来，或由元数据 JSON 反序列化而来）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPathParam {

    /** 参数名 */
    private String name;

    /** 访问方向 */
    @Builder.Default
    private PathDirection direction = PathDirection.READ;

    /** 路径类型 */
    @Builder.Default
    private PathKind kind = PathKind.FILE;

    /**
     * 判断该参数是否是写类操作。
     */
    public boolean isWrite() {
        return direction != null && direction.isWriteLike();
    }
}
