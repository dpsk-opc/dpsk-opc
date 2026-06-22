package com.xiaomizhou.dpsk.db.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 工具绑定关系 VO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolRefVO {

    /**
     * Agent 编码
     */
    private String agentCode;

    /**
     * 工具编码
     */
    private String toolCode;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具描述
     */
    private String toolDescription;

    /**
     * 工具分类
     */
    private String toolCategory;
}
