package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.ListResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.AgentToolComponent;
import com.xiaomizhou.dpsk.db.dto.AgentToolBindCmd;
import com.xiaomizhou.dpsk.db.dto.AgentToolRefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 工具绑定关系控制器。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/agent-tool")
@Slf4j
@RequiredArgsConstructor
public class AgentToolController {

    private final AgentToolComponent agentToolComponent;

    /**
     * 查询 Agent 绑定的工具列表。
     * agentCode 为空时查询所有在用的工具。
     */
    @PostMapping(value = "query")
    public Response<ListResponse<AgentToolRefVO>> query(@RequestBody Request<String> request) {
        String agentCode = request != null ? request.getParam() : null;
        List<AgentToolRefVO> list = agentToolComponent.queryByAgentCode(agentCode);
        return Results.list(list);
    }

    /**
     * 修改 Agent 与工具的绑定关系（全量替换）。
     */
    @PostMapping(value = "bind")
    public Response<Object> bind(@RequestBody AgentToolBindCmd cmd) {
        agentToolComponent.bindTools(cmd);
        return Results.ok();
    }

    @PostMapping(value = "unbind")
    public Response<Object> unbind(@RequestBody AgentToolBindCmd cmd) {
        agentToolComponent.unbindTools(cmd);
        return Results.ok();
    }
}
