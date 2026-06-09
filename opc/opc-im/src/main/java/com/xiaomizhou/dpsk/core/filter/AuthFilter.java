package com.xiaomizhou.dpsk.core.filter;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.db.dao.AgentAuthTokenDao;
import com.xiaomizhou.dpsk.db.dao.AgentDao;
import com.xiaomizhou.dpsk.db.model.Agent;
import com.xiaomizhou.dpsk.db.model.AgentAuthToken;
import com.xiaomizhou.dpsk.core.exceptions.OpErrorCode;
import com.xiaomizhou.dpsk.utils.AuthContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证过滤器
 * <p>
 * 拦截所有请求（除 /agent/auth/** 外），从请求头提取 Agent-Code 和 Access-Token 进行校验。
 * 校验通过后将认证信息设置到 AuthContext ThreadLocal，校验失败返回 403。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/23
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    /**
     * 不需要认证的路径前缀
     */
    private static final String[] EXCLUDE_PATHS = {
            "/xiaomizhou/opc/v1/agent/auth/",
            "/upload",
            "/upload/*",
            "/xiaomizhou/opc/ws",
            "/h2-console/"
    };

    private final AgentAuthTokenDao agentAuthTokenDao;

    private final AgentDao agentDao;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // 放行不需要认证的路径
        if (isExcluded(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1. 提取请求头
            String agentCode = request.getHeader(AuthContext.HEADER_AGENT_CODE);
            String accessToken = request.getHeader(AuthContext.HEADER_ACCESS_TOKEN);

            // 2. 校验请求头是否存在
            if (StringUtils.isBlank(agentCode) || StringUtils.isBlank(accessToken)) {
                writeForbidden(response, "缺少认证信息，请先登录");
                return;
            }

            // 3. 根据 agentCode 查询 Agent
            Agent agent = agentDao.getByCode(agentCode);
            if (agent == null) {
                log.warn("Agent 不存在, agentCode={}", agentCode);
                writeForbidden(response, "认证失败，用户不存在");
                return;
            }

            // 4. 校验 Token 有效性
            AgentAuthToken tokenRecord = agentAuthTokenDao.getActiveByAgentIdAndToken(agent.getId(), accessToken);
            if (tokenRecord == null) {
                log.warn("Token 校验失败, agentCode={}, agentId={}", agentCode, agent.getId());
                writeForbidden(response, "认证失败，Token 无效或已过期");
                return;
            }

            // 5. 认证通过，设置上下文
            AuthContext.setAgentCode(agentCode);
            log.debug("认证通过, agentCode={}, agentId={}", agentCode, agent.getId());

            filterChain.doFilter(request, response);

        } finally {
            // 确保 ThreadLocal 清理，防止内存泄漏
            AuthContext.clear();
        }
    }



    /**
     * 判断请求路径是否需要放行
     */
    private boolean isExcluded(String requestUri) {
        for (String excludePath : EXCLUDE_PATHS) {
            if (requestUri.startsWith(excludePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回认证失败 JSON 响应
     */
    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Response<Void> errorResponse = Results.fail(OpErrorCode.AUTH_FAILED.getCode(), message);
        response.getWriter().write(JsonUtils.toJson(errorResponse));
    }
}
