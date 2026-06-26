package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 工作流模板分页查询参数。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Data
public class WorkflowTemplateQueryParam {

    /** 模板分类（可选） */
    private Integer category;

    /** 状态（可选） */
    private Integer status;

    /** 关键词搜索（可选，匹配名称） */
    private String keyword;
}
