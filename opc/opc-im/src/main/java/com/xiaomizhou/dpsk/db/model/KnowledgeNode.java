package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库节点实体类
 * 管理知识库的目录树结构，目录节点可挂子节点，文件节点为叶子不可再挂子节点
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_knowledge_node")
@Data
public class KnowledgeNode extends BaseModel {

    /**
     * 节点编码，唯一标识
     */
    @TableField("code")
    private String code;

    /**
     * 所属知识库编码
     */
    @TableField("lib_code")
    private String libCode;

    /**
     * 父节点编码，空字符串表示根节点
     */
    @TableField("parent_code")
    private String parentCode;

    /**
     * 节点名称
     */
    @TableField("name")
    private String name;

    /**
     * 节点类型: 0-目录(FOLDER), 1-文件(FILE)
     */
    @TableField("node_type")
    private Integer nodeType;

    /**
     * 层级深度，根节点=0
     */
    @TableField("level")
    private Integer level;

    /**
     * 关联的文件编码（node_type=FILE 时，指向 t_file_record.code）
     */
    @TableField("file_code")
    private String fileCode;

    /**
     * 排序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 状态: 0-已上传(UPLOADED), 1-分析中(ANALYZING), 2-已学习(LEARNED), 3-失败(FAILED)
     */
    @TableField("status")
    private Integer status;

}
