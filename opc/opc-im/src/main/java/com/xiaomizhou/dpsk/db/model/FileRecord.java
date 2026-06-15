package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件记录实体类
 * 存储上传文件的元数据信息，不存储 base64 内容
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/12
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_file_record")
@Data
public class FileRecord extends BaseModel {

    /**
     * 文件编码，唯一标识，用于外部访问
     */
    @TableField("code")
    private String code;

    /**
     * 原始文件名
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 存储文件名（时间戳_UUID_扩展名）
     */
    @TableField("stored_name")
    private String storedName;

    /**
     * 文件在磁盘上的相对路径（如 2026/06/12/xxx.png）
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件扩展名（含点，如 .png）
     */
    @TableField("file_extension")
    private String fileExtension;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * MIME 类型（如 image/png, application/pdf）
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 上传者 Agent Code
     */
    @TableField("uploader_code")
    private String uploaderCode;

    /**
     * 关联的业务编码（如 conversation_code、task_code 等，可选）
     */
    @TableField("ref_code")
    private String refCode;

    /**
     * 关联业务类系。0-默认，1-聊天，2-会话，3-知识库节点
     */
    @TableField("ref_type")
    private Integer refType;

    /**
     * 文件来源类型：CHAT(聊天), TASK(任务), AVATAR(头像), OTHER(其他)
     */
    @TableField("source_type")
    private String sourceType;

}
