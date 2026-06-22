package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.utils.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 认证控制器（登录/注册/修改密码）
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/agent/auth")
@Slf4j
@RequiredArgsConstructor
public class AgentAuthController {

    private final AgentComponent agentComponent;

    /**
     * 用户登录
     */
    @PostMapping(value = "login")
    public Response<AuthDto> login(@Valid @RequestBody LoginCmd cmd) {
        return Results.ok(agentComponent.login(cmd));
    }

    /**
     * 用户注册
     */
    @PostMapping(value = "register")
    public Response<AuthDto> register(@Valid @RequestBody RegisterCmd cmd) {
        return Results.ok(agentComponent.register(cmd));
    }

    /**
     * 修改密码
     */
    @PostMapping(value = "change-password")
    public Response<Object> changePassword(@Valid @RequestBody ChangePasswordCmd cmd) {
        agentComponent.changePassword(cmd);
        return Results.ok();
    }

    /**
     * 用户登出
     */
    @PostMapping(value = "logout")
    public Response<Object> logout(HttpServletRequest request) {
        String agentCode = request.getHeader(AuthContext.HEADER_AGENT_CODE);
        String accessToken = request.getHeader(AuthContext.HEADER_ACCESS_TOKEN);
        agentComponent.logout(agentCode, accessToken);
        return Results.ok();
    }
}
