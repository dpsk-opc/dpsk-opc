package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 管理端 - 文件记录分页查询入参
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class FileRecordPageCmd {

    private String sourceType;

    private String uploaderCode;

    private String originalName;

    private Integer pageNo = 1;

    private Integer pageSize = 10;
}
