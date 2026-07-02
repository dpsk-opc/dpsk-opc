package com.xiaomizhou.dpsk.workflow.xyflow;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.List;

@Data
public class NodeStep {

    private String id;

    private String type;

    private String label;

    private int order;

    private String agentCode;

    private List<String> mcpCodes;

    private List<String> skillPaths;

    private String systemPrompt;

    private int x;

    private int y;


    public static ImmutablePair<String, Integer> NODE_TYPE_START = ImmutablePair.of("start", 0);
    public static ImmutablePair<String, Integer> NODE_TYPE_END = ImmutablePair.of("end", 1);
    public static ImmutablePair<String, Integer> NODE_TYPE_COMMON_AGENT = ImmutablePair.of("process", 2);
    public static ImmutablePair<String, Integer> NODE_TYPE_SWITCH = ImmutablePair.of("switch", 4);
    public static ImmutablePair<String, Integer> NODE_TYPE_HUMAN_CONFIRM = ImmutablePair.of("confirm", 3);
    public static ImmutablePair<String, Integer> NODE_TYPE_AI_CONFIRM = ImmutablePair.of("ai_confirm", 7);
    public static ImmutablePair<String, Integer> NODE_TYPE_LOOP = ImmutablePair.of("loop", 5);


    public static Integer getNodeTypeByName(String name) {

        if (StringUtils.isBlank(name)) {
            return null;
        }

        if (NodeStep.NODE_TYPE_START.left.equals(name)) {
            return NodeStep.NODE_TYPE_START.right;
        }


        if (NodeStep.NODE_TYPE_END.left.equals(name)) {
            return NodeStep.NODE_TYPE_END.right;
        }
        if (NodeStep.NODE_TYPE_COMMON_AGENT.left.equals(name)) {
            return NodeStep.NODE_TYPE_COMMON_AGENT.right;
        }
        if (NodeStep.NODE_TYPE_SWITCH.left.equals(name)) {
            return NodeStep.NODE_TYPE_SWITCH.right;
        }
        if (NodeStep.NODE_TYPE_HUMAN_CONFIRM.left.equals(name)) {
            return NodeStep.NODE_TYPE_HUMAN_CONFIRM.right;
        }
        if (NodeStep.NODE_TYPE_AI_CONFIRM.left.equals(name)) {
            return NodeStep.NODE_TYPE_AI_CONFIRM.right;
        }

        if (NodeStep.NODE_TYPE_LOOP.left.equals(name)) {
            return NodeStep.NODE_TYPE_LOOP.right;
        }

        return null;
    }
}
