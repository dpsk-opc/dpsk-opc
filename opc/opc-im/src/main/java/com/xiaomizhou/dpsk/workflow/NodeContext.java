package com.xiaomizhou.dpsk.workflow;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.List;

@Data
@Builder
public class NodeContext {

    private String nodeId;

    private String nodeType;

    private String nodeLabel;

    private String prompt;

    private String agentCode;

    private List<String> mcpCodes;

    private List<String> skillPaths;

}
