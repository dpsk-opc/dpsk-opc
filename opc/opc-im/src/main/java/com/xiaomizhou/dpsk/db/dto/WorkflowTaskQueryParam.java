package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 工作流任务分页查询参数。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Data
public class WorkflowTaskQueryParam {

    /** 模板编码（可选） */
    private String templateCode;

    /** 状态（可选） */
    private Integer status;

    /** 关键词搜索（可选，匹配名称） */
    private String keyword;
}
