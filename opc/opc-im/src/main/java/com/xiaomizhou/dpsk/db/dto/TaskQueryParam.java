package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 任务分页查询参数。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class TaskQueryParam {

    private String taskType;

    private String status;

    private String consumerKey;

    private String agentCode;

    private String keyword;        // 模糊搜索 name

    private Integer pageNo = 1;

    private Integer pageSize = 10;
}
