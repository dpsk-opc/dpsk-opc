package com.xiaomizhou.dpsk.tool.workspace;

/**
 * 路径抽取用的 LLM 调用接口。
 * <p>
 * opc-core 不直接依赖具体模型实现，由 opc-im 用 {@code createChatModel()} 提供实现（D17）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
public interface PathExtractionLlm {

    /**
     * 调用模型并返回文本结果。
     *
     * @param prompt 提示词（仅含工具元信息与参数，严禁包含外部不可信内容）
     * @return 模型返回文本；失败返回 null
     */
    String chat(String prompt);
}
