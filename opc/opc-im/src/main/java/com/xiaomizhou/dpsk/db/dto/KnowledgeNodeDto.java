package com.xiaomizhou.dpsk.db.dto;

import com.xiaomizhou.dpsk.db.model.KnowledgeLib;
import com.xiaomizhou.dpsk.db.model.KnowledgeNode;
import lombok.Data;

import java.util.Date;

/**
 * 知识库节点数据传输对象
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Data
public class KnowledgeNodeDto {

    /** 节点编码 */
    private String code;

    /** 所属知识库编码 */
    private String libCode;

    /** 父节点编码 */
    private String parentCode;

    /** 节点名称 */
    private String name;

    /** 节点类型: 0-目录, 1-文件 */
    private Integer nodeType;

    /** 层级深度 */
    private Integer level;

    /** 关联文件编码（文件节点时） */
    private String fileCode;

    /** 排序号 */
    private Integer sortOrder;

    /** 是否有子节点（目录节点时，用于前端展开图标） */
    private Boolean hasChildren;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * 文件地址
     */
    private String accessUrl;


    /**
     * 状态: 0-已上传(UPLOADED), 1-分析中(ANALYZING), 2-已学习(LEARNED), 3-失败(FAILED)
     */
    private Integer status;



    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;



    public static KnowledgeNodeDto toKnowledgeNodeDto(KnowledgeNode node) {
        KnowledgeNodeDto dto = new KnowledgeNodeDto();
        dto.setCode(node.getCode());
        dto.setLibCode(node.getLibCode());
        dto.setParentCode(node.getParentCode());
        dto.setName(node.getName());
        dto.setNodeType(node.getNodeType());
        dto.setLevel(node.getLevel());
        dto.setFileCode(node.getFileCode());
        dto.setSortOrder(node.getSortOrder());
        dto.setCreateTime(node.getCreateTime());
        dto.setUpdateTime(node.getUpdateTime());
        dto.setStatus(node.getStatus());
        return dto;
    }


}
