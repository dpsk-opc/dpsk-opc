package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import java.util.List;

/**
 * Agent 工具绑定命令。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1
 */
@Data
public class AgentToolBindCmd {

    /**
     * Agent 编码
     */
    private String agentCode;

    /**
     * 要绑定的工具编码列表（全量替换）
     */
    private List<String> toolCodes;
}
