package com.xiaomizhou.dpsk.workflow.xyflow;

import lombok.Data;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;

@Data
public class XyFlow {

    private List<NodeStep> steps;

    private List<NodeEdge> edges;


    public boolean isCommonNode(String nodeId) {
        return (!isSwitchNode(nodeId) && !isStartNode(nodeId) && !isEndNode(nodeId));
    }

    public boolean isSwitchNode(String nodeId) {
        return CollectionUtils.isNotEmpty(edges) && edges.stream().filter(e -> e.getSource().equals(nodeId)).count() > 1;
    }

    public boolean isStartNode(String nodeId) {
        return CollectionUtils.isNotEmpty(steps) && steps.stream().filter(s -> s.getId().equals(nodeId)).anyMatch(s -> "start".equals(s.getType()));
    }

    public boolean isEndNode(String nodeId) {
        return CollectionUtils.isNotEmpty(steps) && steps.stream().filter(s -> s.getId().equals(nodeId)).anyMatch(s -> "end".equals(s.getType()));
    }

}
