package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 保存文件到知识库节点请求参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Data
public class KnowledgeNodeSaveFileCmd {

    /** 知识库编码 */
    private String libCode;

    /** 父节点编码，空字符串表示根节点 */
    private String parentCode;

    /** 已上传的文件编码 */
    private String fileCode;

    /** 文件在知识库中的显示名称（可选，不传则用原始文件名） */
    private String name;
}
