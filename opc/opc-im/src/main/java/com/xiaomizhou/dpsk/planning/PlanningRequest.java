package com.xiaomizhou.dpsk.planning;

import java.util.List;

/**
 * 规划请求：用户诉求 + 候选 Agent 池 + 历史节点结果（replan 时使用）。
 */
public class PlanningRequest {

    /** 用户原始诉求 */
    private final String userRequest;

    /** 候选 Agent 池（群成员，排除发言用户） */
    private final List<String> agentCodes;

    /** replan 时：失败节点上游结果 + 失败原因；首次为空 */
    private final List<NodeResultRef> history;

    public PlanningRequest(String userRequest, List<String> agentCodes, List<NodeResultRef> history) {
        this.userRequest = userRequest;
        this.agentCodes = agentCodes;
        this.history = history;
    }

    public String getUserRequest() {
        return userRequest;
    }

    public List<String> getAgentCodes() {
        return agentCodes;
    }

    public List<NodeResultRef> getHistory() {
        return history;
    }

    /**
     * replan 输入中的节点结果引用。
     */
    public static class NodeResultRef {
        private final String nodeId;
        private final String output;

        public NodeResultRef(String nodeId, String output) {
            this.nodeId = nodeId;
            this.output = output;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getOutput() {
            return output;
        }
    }
}
