package com.xiaomizhou.dpsk.db.dto;


import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class FileRecordDto {

    private String code;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件在磁盘上的相对路径（如 2026/06/12/xxx.png）
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件扩展名（含点，如 .png）
     */
    private String fileExtension;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME 类型（如 image/png, application/pdf）
     */
    private String contentType;

    /**
     * 下载链接
     */
    private String accessUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
