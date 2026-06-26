package com.xiaomizhou.dpsk.workflow.xyflow;

import lombok.Data;

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

    private int x;

    private int y;

}
