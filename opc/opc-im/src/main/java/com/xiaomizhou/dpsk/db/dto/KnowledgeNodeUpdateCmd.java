package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 更新知识库节点请求参数（重命名）
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Data
public class KnowledgeNodeUpdateCmd {

    /** 节点编码 */
    private String code;

    /** 新名称 */
    private String name;
}
