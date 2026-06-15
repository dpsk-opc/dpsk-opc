package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 删除知识库节点请求参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Data
public class KnowledgeNodeDeleteCmd {

    /** 节点编码 */
    private String code;
}
