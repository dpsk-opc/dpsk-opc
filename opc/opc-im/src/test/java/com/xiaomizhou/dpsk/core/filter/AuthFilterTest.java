//package com.xiaomizhou.dpsk.core.filter;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.xiaomizhou.dpsk.db.dao.AgentAuthTokenDao;
//import com.xiaomizhou.dpsk.db.dao.AgentDao;
//import com.xiaomizhou.dpsk.db.model.Agent;
//import com.xiaomizhou.dpsk.db.model.AgentAuthToken;
//import com.xiaomizhou.dpsk.utils.AuthContext;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.http.HttpStatus;
//
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.io.StringWriter;
//import java.util.Date;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyLong;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.*;
//
///**
// * AuthFilter 单元测试
// *
// * @author eason
// * @date 2026/5/23
// */
//@ExtendWith(MockitoExtension.class)
//class AuthFilterTest {
//
//    @Mock
//    private AgentAuthTokenDao agentAuthTokenDao;
//
//    @Mock
//    private AgentDao agentDao;
//
//    @Mock
//    private HttpServletRequest request;
//
//    @Mock
//    private HttpServletResponse response;
//
//    @Mock
//    private FilterChain filterChain;
//
//    private ObjectMapper objectMapper;
//
//    @InjectMocks
//    private AuthFilter authFilter;
//
//    private StringWriter stringWriter;
//    private PrintWriter printWriter;
//
//    private static final String VALID_AGENT_CODE = "AGT-001";
//    private static final String VALID_TOKEN = "abc123def456";
//
//    @BeforeEach
//    void setUp() throws IOException {
//        objectMapper = new ObjectMapper();
//        // InjectObjectMapper via reflection since @InjectMocks can't inject manually
//        authFilter = new AuthFilter(agentAuthTokenDao, agentDao, objectMapper);
//
//        stringWriter = new StringWriter();
//        printWriter = new PrintWriter(stringWriter);
//        when(response.getWriter()).thenReturn(printWriter);
//    }
//
//    @AfterEach
//    void tearDown() {
//        AuthContext.clear();
//    }
//
//    // ==================== 放行路径测试 ====================
//
//    @Test
//    void shouldSkipAuth_whenAuthPath() throws ServletException, IOException {
//        when(request.getRequestURI()).thenReturn("/xiaomizhou/opc/v1/agent/auth/login");
//
//        authFilter.doFilterInternal(request, response, filterChain);
//
//        verify(filterChain).doFilter(request, response);
//        verifyNoInteractions(agentDao);
//    }
//
//    @Test
//    void shouldSkipAuth_whenUploadsPath() throws ServletException, IOException {
//        when(request.getRequestURI()).thenReturn("/uploads/avatar.png");
//
//        authFilter.doFilterInternal(request, response, filterChain);
//
//        verify(filterChain).doFilter(request, response);
//        verifyNoInteractions(agentDao);
//    }
//
//    // ==================== 缺少认证信息测试 ====================
//
//    @Test
//    void shouldReturn403_whenMissingAgentCode() throws ServletException, IOException {
//        when(request.getRequestURI()).thenReturn("/xiaomizhou/opc/v1/agent/list");
//        when(request.getHeader(AuthContext.HEADER_AGENT_CODE)).thenReturn(null);
//        when(request.getHeader(AuthContext.HEADER_ACCESS_TOKEN)).thenReturn(VALID_TOKEN);
//
//        authFilter.doFilterInternal(request, response, filterChain);
//
//        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
//        verify(filterChain, never()).doFilter(any(), any());
//
//        String responseBody = stringWriter.toString();
//        assertTrue(responseBody.contains("缺少认证信息"));
//    }
//
//    @Test
//    void shouldReturn403_whenMissingAccessToken() throws ServletException, IOException {
//        when(request.getRequestURI()).thenReturn("/xiaomizhou/opc/v1/agent/list");
//        when(request.getHeader(AuthContext.HEADER_AGENT_CODE)).thenReturn(VALID_AGENT_CODE);
//        when(request.getHeader(AuthContext.HEADER_ACCESS_TOKEN)).thenReturn(null);
//
//        authFilter.doFilterInternal(request, response, filterChain);
//
//        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
//        verify(filterChain, never()).doFilter(any(), any());
//    }
//
//    @Test
//    void shouldReturn403_whenBothHeadersMissing() throws ServletException, IOException {
//        when(request.getRequestURI()).thenReturn("/xiaomizhou/opc/v1/agent/list");
//        when(request.getHeader(anyString())).thenReturn(null);
//
//        authFilter.doFilterInternal(request, response, filterChain);
//
//        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
//    }
//
//    // ==================== Agent 不存在测试 ====================
//
//    @Test
//    void shouldReturn403_whenAgentNotFound() throws ServletException, IOException {
//        when(request.getRequestURI()).thenReturn("/xiaomizhou/opc/v1/agent/list");
//        when(request.getHeader(AuthContext.HEADER_AGENT_CODE)).thenReturn(VALID_AGENT_CODE);
//        when(request.getHeader(AuthContext.HEADER_ACCESS_TOKEN)).thenReturn(VALID_TOKEN);
//        when(agentDao.getByCode(VALID_AGENT_CODE)).thenReturn(null);
//
//        authFilter.doFilterInternal(request, response, filterChain);
//
//        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
//        String responseBody = stringWriter.toString();
//        assertTrue(responseBody.contains("用户不存在"));
//    }
//
//    // ==================== Token 无效测试 ====================
//
//    @Test
//    void shouldReturn403_whenTokenInvalid() throws ServletException, IOException {
//        Agent agent = new Agent();
//        agent.setId(100L);
//        agent.setCode(VALID_AGENT_CODE);
//
//        when(request.getRequestURI()).thenReturn("/xiaomizhou/opc/v1/agent/list");
//        when(request.getHeader(AuthContext.HEADER_AGENT_CODE)).thenReturn(VALID_AGENT_CODE);
//        when(request.getHeader(AuthContext.HEADER_ACCESS_TOKEN)).thenReturn(VALID_TOKEN);
//        when(agentDao.getByCode(VALID_AGENT_CODE)).thenReturn(agent);
//        when(agentAuthTokenDao.getActiveByAgentIdAndToken(100L, VALID_TOKEN)).thenReturn(null);
//
//        authFilter.doFilterInternal(request, response, filterChain);
//
//        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
//        String responseBody = stringWriter.toString();
//        assertTrue(responseBody.contains("Token 无效或已过期"));
//    }
//
//    // ==================== 认证通过测试 ====================
//
//    @Test
//    void shouldPassFilter_whenAuthValid() throws ServletException, IOException {
//        Agent agent = new Agent();
//        agent.setId(100L);
//        agent.setCode(VALID_AGENT_CODE);
//
//        AgentAuthToken tokenRecord = new AgentAuthToken();
//        tokenRecord.setId(1L);
//        tokenRecord.setAgentCode(100L);
//        tokenRecord.setToken(VALID_TOKEN);
//        tokenRecord.setExpireTime(new Date(System.currentTimeMillis() + 86400000L));
//        tokenRecord.setStatus("ACTIVE");
//
//        when(request.getRequestURI()).thenReturn("/xiaomizhou/opc/v1/agent/list");
//        when(request.getHeader(AuthContext.HEADER_AGENT_CODE)).thenReturn(VALID_AGENT_CODE);
//        when(request.getHeader(AuthContext.HEADER_ACCESS_TOKEN)).thenReturn(VALID_TOKEN);
//        when(agentDao.getByCode(VALID_AGENT_CODE)).thenReturn(agent);
//        when(agentAuthTokenDao.getActiveByAgentIdAndToken(100L, VALID_TOKEN)).thenReturn(tokenRecord);
//
//        authFilter.doFilterInternal(request, response, filterChain);
//
//        verify(filterChain).doFilter(request, response);
//        verify(response, never()).setStatus(anyInt());
//    }
//
//    // ==================== AuthContext 清理测试 ====================
//
//    @Test
//    void shouldClearAuthContext_afterFilter() throws ServletException, IOException {
//        when(request.getRequestURI()).thenReturn("/xiaomizhou/opc/v1/agent/auth/login");
//
//        authFilter.doFilterInternal(request, response, filterChain);
//
//        // AuthContext 应该在 finally 中被清理
//        assertNull(AuthContext.getAgentCode());
//        assertNull(AuthContext.getAgentId());
//    }
//}
