package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库实体类
 * 知识库是文件的集合，文件通过 t_file_record.ref_code 关联到知识库 code
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_knowledge_lib")
@Data
public class KnowledgeLib extends BaseModel {

    /**
     * 知识库编码，唯一标识
     */
    @TableField("code")
    private String code;

    /**
     * 知识库名称
     */
    @TableField("name")
    private String name;

    /**
     * 知识库描述
     */
    @TableField("description")
    private String description;

    /**
     * 归属实体编码，如 agent.code
     */
    @TableField("owner_code")
    private String ownerCode;

    /**
     * 归属类型: 0-AGENT, 1-GROUP(预留), 2-DISCUSSION(预留)
     */
    @TableField("owner_type")
    private Integer ownerType;

    /**
     * 状态: 0-已上传(UPLOADED), 1-分析中(ANALYZING), 2-已学习(LEARNED), 3-失败(FAILED)
     */
    @TableField("status")
    private Integer status;

}
