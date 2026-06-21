package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端 - 文件记录更新入参
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class FileRecordUpdateCmd {

    @NotBlank(message = "文件编码不能为空")
    private String code;

    private String originalName;

    private String sourceType;

    private String refCode;

    private Integer refType;
}
