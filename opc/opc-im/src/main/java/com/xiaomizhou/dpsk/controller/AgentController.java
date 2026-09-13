package com.xiaomizhou.dpsk.controller;

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
import com.xiaomizhou.dpsk.utils.AuthContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
     * 分页查询 Agent（仅查询当前登录用户的好友列表）
     */
    @PostMapping(value = "page")
    public Response<PageResponse<AgentDto>> page(@RequestBody AgentQueryParam param) {

        // 获取当前登录用户，只能查询自己的好友
        String ownerCode = AuthContext.getAgentCode();
        IPage<AgentDto> page = agentComponent.queryPage(param, ownerCode);

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
        return Results.ok(agentComponent.create(AuthContext.getAgentCode(), cmd));
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

    /**
     * 删除好友（仅解除好友关系，不删除 Agent 本身）
     * <p>
     * 入参为好友的 Agent Code；关系双向解除，双方好友列表都会移除。
     * 如需彻底删除 Agent 请调用 delete。
     */
    @PostMapping(value = "deleteFriend")
    public Response<Boolean> deleteFriend(@RequestBody Request<String> request) {
        String friendCode = request.getParam();
        if (StringUtils.isBlank(friendCode)) {
            return Results.fail("好友编码不能为空");
        }

        boolean result = agentComponent.deleteFriend(AuthContext.getAgentCode(), friendCode);
        return result ? Results.ok(true) : Results.fail("删除好友失败，好友关系不存在");
    }

}
