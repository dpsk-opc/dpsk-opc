package com.xiaomizhou.dpsk.memory.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/6/2 11:48
 * @description
 */
@Data
public class ToolMsgDto {

    private String id;

    private String toolName;

    private List<Content> contents;

    private Boolean isError;

    private Map<String, Object> attributes;


    @Data
    public static class Content {

        private String type;


        private String text;


    }


}
