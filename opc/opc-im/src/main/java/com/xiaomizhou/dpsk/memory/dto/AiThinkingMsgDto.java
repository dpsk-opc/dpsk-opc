package com.xiaomizhou.dpsk.memory.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/6/2 13:37
 * @description
 */
@Data
public class AiThinkingMsgDto {

    private String thinking;

    private ToolMsgDto.Content content;

    private Map<String, Object> attributes;

    private List<ToolExecutionRequest> requests;

    @Data
    public static class ToolExecutionRequest{

        private String id;

        private String name;

        private String arguments;
    }

}
