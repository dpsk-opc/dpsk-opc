package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 执行日志分页查询参数。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class TaskExecutionLogQueryParam {

    private String taskCode;

    private String consumerKey;

    private String status;

    private Integer pageNo = 1;

    private Integer pageSize = 10;
}
