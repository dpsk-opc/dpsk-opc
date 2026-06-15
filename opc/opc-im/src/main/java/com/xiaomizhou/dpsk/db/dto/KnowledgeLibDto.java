package com.xiaomizhou.dpsk.db.dto;

import com.xiaomizhou.dpsk.db.model.KnowledgeLib;
import com.xiaomizhou.dpsk.db.model.KnowledgeNode;
import lombok.Data;

import java.util.Date;

/**
 * 知识库数据传输对象
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Data
public class KnowledgeLibDto {

    /** 知识库编码 */
    private String code;

    /** 知识库名称 */
    private String name;

    /** 知识库描述 */
    private String description;

    /** 归属实体编码 */
    private String ownerCode;

    /** 归属类型: 0-AGENT, 1-GROUP, 2-DISCUSSION */
    private Integer ownerType;

    /** 状态: 0-已上传, 1-分析中, 2-已学习, 3-失败 */
    private Integer status;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;


    public static KnowledgeLibDto toKnowledgeLibDto(KnowledgeLib lib) {
        KnowledgeLibDto dto = new KnowledgeLibDto();
        dto.setCode(lib.getCode());
        dto.setName(lib.getName());
        dto.setDescription(lib.getDescription());
        dto.setOwnerCode(lib.getOwnerCode());
        dto.setOwnerType(lib.getOwnerType());
        dto.setStatus(lib.getStatus());
        dto.setCreateTime(lib.getCreateTime());
        dto.setUpdateTime(lib.getUpdateTime());
        return dto;
    }
}
