package com.xiaomizhou.dpsk.utils;

/**
 * 认证上下文工具类
 * <p>
 * 基于 ThreadLocal 存储当前请求的认证信息，由 AuthFilter 在请求进入时设置，请求结束后清除。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/23
 */
public class AuthContext {

    /**
     * 请求头: Agent-Code
     */
    public static final String HEADER_AGENT_CODE = "Agent-Code";

    /**
     * 请求头: Access-Token
     */
    public static final String HEADER_ACCESS_TOKEN = "Access-Token";

    private static final ThreadLocal<String> AGENT_CODE_HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    /**
     * 设置当前请求上下文的 Agent Code
     */
    public static void setAgentCode(String agentCode) {
        AGENT_CODE_HOLDER.set(agentCode);
    }

    /**
     * 获取当前请求上下文的 Agent Code
     *
     * @return Agent Code，未设置返回 null
     */
    public static String getAgentCode() {
        return AGENT_CODE_HOLDER.get();
    }


    /**
     * 清除当前请求上下文（在 Filter 的 finally 中调用，防止内存泄漏）
     */
    public static void clear() {
        AGENT_CODE_HOLDER.remove();
    }
}
