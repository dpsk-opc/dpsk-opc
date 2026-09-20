package com.xiaomizhou.dpsk.tool.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次文件中转的访问事实：某个参数（或某次抽取结果）对应的路径与方向。
 * <p>
 * 这是"事实"，不是"结论"：判定权在 {@link PathGuard}。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathAccess {

    /** 参数名（可为空，模型抽取结果可能无法确定参数名） */
    private String paramName;

    /** 原始路径文本（用户/模型给出的原样值） */
    private String rawPath;

    /** 访问方向 */
    @Builder.Default
    private PathDirection direction = PathDirection.READ;

    /** 路径类型 */
    @Builder.Default
    private PathKind kind = PathKind.FILE;

    /** 来源：DECLARED（工具声明）/ EXTRACTED（模型抽取）/ INFERRED（兜底推断） */
    private String source;

    public static final String SOURCE_DECLARED = "DECLARED";
    public static final String SOURCE_EXTRACTED = "EXTRACTED";
    public static final String SOURCE_INFERRED = "INFERRED";
}
