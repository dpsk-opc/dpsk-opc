package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

@Data
public class WorkflowNodeProgressDto {

    private String nodeId;      // 节点ID

    private String nodeName;    // 节点名称（前端直接展示，如"节点a"）

    private Integer nodeType;   // 节点类型（start/process/confirm/end...）

    private Integer status;     // 状态：0待执行/1运行中/2成功/3失败/4跳过

    private String agentCode;   // 绑定的Agent编码（可选，用于节点tooltip）

    private String agentName;   // Agent名称（可选）

}
