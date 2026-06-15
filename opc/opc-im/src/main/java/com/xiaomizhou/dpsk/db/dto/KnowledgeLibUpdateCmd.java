package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 更新知识库请求参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Data
public class KnowledgeLibUpdateCmd {

    /** 知识库编码 */
    private String code;

    /** 知识库名称（可选） */
    private String name;

    /** 知识库描述（可选） */
    private String description;
}
