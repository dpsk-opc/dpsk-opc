package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 移动知识库节点请求参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Data
public class KnowledgeNodeMoveCmd {

    /** 要移动的节点编码 */
    private String code;

    /** 目标父节点编码，空字符串表示根目录 */
    private String parentCode;
}
