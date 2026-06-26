package com.xiaomizhou.dpsk.workflow.xyflow;

import lombok.Data;

@Data
public class NodeEdge {

    private String id;

    private String source;

    private String target;

    private String condition;

    private String sourceHandler;

    private String tagetHandler;

    private String label;

    private String maxIterations;
}
