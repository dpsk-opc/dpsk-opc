package com.xiaomizhou.dpsk.db.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 工作流任务响应 DTO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Data
public class WorkflowTaskDto {

    /** 任务编码 */
    private Long id;

    /** 任务编码*/
    private String code;

    /** 任务名称 */
    private String name;

    /** 关联模板编码 */
    private String templateCode;

    /** 锁定模板版本号 */
    private Integer templateVersion;

    /**头像 **/
    private String avatar;

    /** 状态 */
    private Integer status;

    /** 状态中文名称 */
    private String statusName;

    /** 当前执行节点ID */
    private String currentNodeId;

    /** 当前步骤序号 */
    private Integer currentStep;

    /** 工作流JSON */
    private String workflowJson;

    /** 输入参数JSON */
    private String inputParams;

    /** 运行时上下文JSON */
    private String contextData;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    /** 失败原因 */
    private String errorMessage;

    /** 触发来源 */
    private Integer source;

    /** 来源中文名称 */
    private String sourceName;

    /** 关联会话编码 */
    private String conversationCode;

    /** 触发Agent编码 */
    private String agentCode;

    /** 关联定时任务编码 */
    private String scheduledTaskCode;

    /** 创建者编码 */
    private String ownerCode;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
