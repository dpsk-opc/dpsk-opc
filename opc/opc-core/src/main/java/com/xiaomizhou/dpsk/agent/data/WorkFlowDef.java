package com.xiaomizhou.dpsk.agent.data;

import lombok.Data;

import java.util.List;

@Data
public class WorkFlowDef {

    private String code;

    private String name;

    private String description;

    private List<WorkFlowNode> nodes;

    @Data
    public static class WorkFlowNode {

        /**
         * 1-内置agent；2-自定义agent
         */
        private Integer type;

        private String code;

        private String name;

        private String llmConfig;

        private String prompt;

    }

}
