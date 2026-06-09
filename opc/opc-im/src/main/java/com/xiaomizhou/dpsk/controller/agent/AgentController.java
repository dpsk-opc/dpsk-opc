package com.xiaomizhou.dpsk.controller.agent;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaomizhou.dpsk.db.dto.AgentCreateCmd;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.dto.AgentQueryParam;
import com.xiaomizhou.dpsk.db.dto.AgentUpdateCmd;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 控制器
 *
 * @author Administrator
 * @date 2026/5/13 21:31
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/agent")
@Slf4j
@RequiredArgsConstructor
public class AgentController {

    private final AgentComponent agentComponent;


    /**
     * 分页查询 Agent（基于数据库）
     */
    @PostMapping(value = "page")
    public Response<PageResponse<AgentDto>> page(@RequestBody AgentQueryParam param) {

        IPage<AgentDto> page = agentComponent.queryPage(param);

        return Results.page(
                page.getRecords(),
                param.getPageNo(),
                param.getPageSize(),
                page.getTotal());
    }

    /**
     * 根据ID查询
     */
    @PostMapping(value = "get")
    public Response<AgentDto> get(@RequestBody Request<String> request) {
        return Results.ok(agentComponent.getByCode(request.getParam()));
    }

    /**
     * 创建 Agent
     */
    @PostMapping(value = "create")
    public Response<AgentDto> create(@Valid @RequestBody AgentCreateCmd cmd) {
        return Results.ok(agentComponent.create(cmd));
    }

    /**
     * 更新 Agent
     */
    @PostMapping(value = "update")
    public Response<AgentDto> update(@Valid @RequestBody AgentUpdateCmd cmd) {
        return Results.ok(agentComponent.update(cmd));
    }

    /**
     * 删除 Agent（逻辑删除）
     */
    @PostMapping(value = "delete")
    public Response<Object> delete(@RequestBody AgentUpdateCmd request) {
        agentComponent.delete(request.getCode());
        return Results.ok();
    }

}
