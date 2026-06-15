package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 创建知识库目录节点请求参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Data
public class KnowledgeNodeCreateFolderCmd {

    /** 知识库编码 */
    private String libCode;

    /** 父节点编码，空字符串表示根节点 */
    private String parentCode;

    /** 目录名称 */
    private String name;
}
