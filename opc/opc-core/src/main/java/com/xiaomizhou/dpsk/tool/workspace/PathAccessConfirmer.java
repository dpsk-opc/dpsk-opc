package com.xiaomizhou.dpsk.tool.workspace;

import java.util.List;

/**
 * 越界路径访问确认器（阻塞式）。
 * <p>
 * opc-core 仅定义接口，由 opc-im 提供基于 WS + HTTP 的实现（复用 ToolAskManager 的阻塞/超时/取消模式）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
public interface PathAccessConfirmer {

    /**
     * 向用户发起确认并阻塞等待答复。
     *
     * @param title      标题/发起方描述
     * @param accesses   待确认的路径访问列表
     * @param reason     模型自述的原因（可为空）
     * @param context    执行上下文信息
     * @return 确认结果；超时按拒绝
     */
    ConfirmResult confirm(String title, List<PathAccess> accesses, String reason, ConfirmContext context);

    /**
     * 确认结果。
     */
    class ConfirmResult {

        public enum Decision {
            /** 仅本次允许 */
            ONCE,
            /** 本次会话允许该目录 */
            SESSION_DIR,
            /** 拒绝 */
            DENY
        }

        private final Decision decision;
        private final String message;

        public ConfirmResult(Decision decision, String message) {
            this.decision = decision;
            this.message = message;
        }

        public static ConfirmResult deny(String message) {
            return new ConfirmResult(Decision.DENY, message);
        }

        public boolean isAllowed() {
            return decision == Decision.ONCE || decision == Decision.SESSION_DIR;
        }

        public Decision getDecision() {
            return decision;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 确认请求的上下文。
     */
    class ConfirmContext {
        private String conversationCode;
        private String agentCode;
        private String agentName;
        private String userCode;
        private long timeoutSeconds = 60;

        public String getConversationCode() {
            return conversationCode;
        }

        public void setConversationCode(String conversationCode) {
            this.conversationCode = conversationCode;
        }

        public String getAgentCode() {
            return agentCode;
        }

        public void setAgentCode(String agentCode) {
            this.agentCode = agentCode;
        }

        public String getAgentName() {
            return agentName;
        }

        public void setAgentName(String agentName) {
            this.agentName = agentName;
        }

        public String getUserCode() {
            return userCode;
        }

        public void setUserCode(String userCode) {
            this.userCode = userCode;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
