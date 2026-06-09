//package com.xiaomizhou.dpsk.db;
//
//import com.xiaomizhou.dpsk.db.dao.AgentAuthTokenDao;
//import com.xiaomizhou.dpsk.db.dao.AgentDao;
//import com.xiaomizhou.dpsk.db.dto.*;
//import com.xiaomizhou.dpsk.db.model.Agent;
//import com.xiaomizhou.dpsk.db.model.AgentAuthToken;
//import com.xiaomizhou.dpsk.utils.PasswordEncoder;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.Date;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyLong;
//import static org.mockito.Mockito.*;
//
///**
// * AgentComponent 认证功能单元测试（含 Token 持久化）
// *
// * @author eason
// * @date 2026/5/22
// */
//@ExtendWith(MockitoExtension.class)
//class AgentComponentAuthTest {
//
//    @Mock
//    private AgentDao agentDao;
//
//    @Mock
//    private AgentAuthTokenDao agentAuthTokenDao;
//
//    @Mock
//    private PasswordEncoder passwordEncoder;
//
//    @InjectMocks
//    private AgentComponent agentComponent;
//
//    private static final String TEST_EMAIL = "test@example.com";
//    private static final String TEST_PASSWORD = "password123";
//    private static final String TEST_ENCODED_PASSWORD = "$2a$10$encodedPasswordHash";
//
//    private Agent mockAgent;
//
//    @BeforeEach
//    void setUp() {
//        mockAgent = new Agent();
//        mockAgent.setId(1L);
//        mockAgent.setCode("AG_001");
//        mockAgent.setName("测试用户");
//        mockAgent.setNickname("测试");
//        mockAgent.setEmail(TEST_EMAIL);
//        mockAgent.setPassword(TEST_ENCODED_PASSWORD);
//        mockAgent.setType("USER");
//        mockAgent.setStatus("ACTIVE");
//        mockAgent.setSex(0);
//        mockAgent.setCreateTime(new Date());
//        mockAgent.setUpdateTime(new Date());
//        mockAgent.setLastActiveTime(new Date());
//        mockAgent.setIsDeleted(0);
//    }
//
//    // ==================== login 测试 ====================
//
//    @Test
//    void login_shouldReturnAuthDto_whenCredentialsCorrect() {
//        when(agentDao.getByEmail(TEST_EMAIL)).thenReturn(mockAgent);
//        when(passwordEncoder.matches(TEST_PASSWORD, TEST_ENCODED_PASSWORD)).thenReturn(true);
//        when(agentDao.updateLastActiveTime(anyLong())).thenReturn(true);
//
//        LoginCmd cmd = new LoginCmd();
//        cmd.setEmail(TEST_EMAIL);
//        cmd.setPassword(TEST_PASSWORD);
//
//        AuthDto result = agentComponent.login(cmd);
//
//        assertNotNull(result);
//        assertNotNull(result.token(), "登录后应返回 token");
//        assertNotNull(result.agent(), "登录后应返回 agent 信息");
//        assertEquals(mockAgent.getCode(), result.agent().getCode());
//        assertEquals(mockAgent.getName(), result.agent().getName());
//
//        // 验证 Token 被持久化
//        verify(agentAuthTokenDao).upsertToken(any(AgentAuthToken.class));
//        verify(agentDao).updateLastActiveTime(mockAgent.getId());
//    }
//
//    @Test
//    void login_shouldThrowException_whenEmailNotFound() {
//        when(agentDao.getByEmail(TEST_EMAIL)).thenReturn(null);
//
//        LoginCmd cmd = new LoginCmd();
//        cmd.setEmail(TEST_EMAIL);
//        cmd.setPassword(TEST_PASSWORD);
//
//        RuntimeException ex = assertThrows(RuntimeException.class, () -> agentComponent.login(cmd));
//        assertEquals("账号或密码错误", ex.getMessage());
//
//        verify(agentAuthTokenDao, never()).upsertToken(any());
//    }
//
//    @Test
//    void login_shouldThrowException_whenPasswordWrong() {
//        when(agentDao.getByEmail(TEST_EMAIL)).thenReturn(mockAgent);
//        when(passwordEncoder.matches(TEST_PASSWORD, TEST_ENCODED_PASSWORD)).thenReturn(false);
//
//        LoginCmd cmd = new LoginCmd();
//        cmd.setEmail(TEST_EMAIL);
//        cmd.setPassword(TEST_PASSWORD);
//
//        RuntimeException ex = assertThrows(RuntimeException.class, () -> agentComponent.login(cmd));
//        assertEquals("账号或密码错误", ex.getMessage());
//
//        verify(agentAuthTokenDao, never()).upsertToken(any());
//    }
//
//    // ==================== register 测试 ====================
//
//    @Test
//    void register_shouldReturnAuthDto_whenEmailNotExists() {
//        when(agentDao.getByEmail(TEST_EMAIL)).thenReturn(null);
//        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_ENCODED_PASSWORD);
//        when(agentDao.save(any(Agent.class))).thenReturn(true);
//
//        RegisterCmd cmd = new RegisterCmd();
//        cmd.setEmail(TEST_EMAIL);
//        cmd.setPassword(TEST_PASSWORD);
//        cmd.setName("新用户");
//        cmd.setNickname("新人");
//
//        AuthDto result = agentComponent.register(cmd);
//
//        assertNotNull(result);
//        assertNotNull(result.token(), "注册后应返回 token");
//        assertNotNull(result.agent());
//        assertEquals("新用户", result.agent().getName());
//
//        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
//        verify(agentDao).save(captor.capture());
//        Agent saved = captor.getValue();
//        assertEquals(TEST_EMAIL, saved.getEmail());
//        assertEquals(TEST_ENCODED_PASSWORD, saved.getPassword());
//        assertEquals("USER", saved.getType());
//        assertEquals("ACTIVE", saved.getStatus());
//
//        // 验证 Token 被持久化
//        verify(agentAuthTokenDao).upsertToken(any(AgentAuthToken.class));
//    }
//
//    @Test
//    void register_shouldUseNameAsNickname_whenNicknameNotProvided() {
//        when(agentDao.getByEmail(TEST_EMAIL)).thenReturn(null);
//        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_ENCODED_PASSWORD);
//        when(agentDao.save(any(Agent.class))).thenReturn(true);
//
//        RegisterCmd cmd = new RegisterCmd();
//        cmd.setEmail(TEST_EMAIL);
//        cmd.setPassword(TEST_PASSWORD);
//        cmd.setName("新用户");
//        cmd.setNickname(null);
//
//        AuthDto result = agentComponent.register(cmd);
//
//        assertNotNull(result);
//        assertNotNull(result.token());
//
//        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
//        verify(agentDao).save(captor.capture());
//        assertEquals("新用户", captor.getValue().getNickname());
//    }
//
//    @Test
//    void register_shouldThrowException_whenEmailAlreadyExists() {
//        when(agentDao.getByEmail(TEST_EMAIL)).thenReturn(mockAgent);
//
//        RegisterCmd cmd = new RegisterCmd();
//        cmd.setEmail(TEST_EMAIL);
//        cmd.setPassword(TEST_PASSWORD);
//        cmd.setName("新用户");
//
//        RuntimeException ex = assertThrows(RuntimeException.class, () -> agentComponent.register(cmd));
//        assertEquals("该邮箱已被注册", ex.getMessage());
//
//        verify(agentDao, never()).save(any(Agent.class));
//        verify(agentAuthTokenDao, never()).upsertToken(any());
//    }
//
//    // ==================== changePassword 测试 ====================
//
//    @Test
//    void changePassword_shouldUpdatePasswordAndRevokeTokens_whenOldPasswordCorrect() {
//        String newPassword = "newPassword456";
//        String encodedNewPassword = "$2a$10$newEncodedHash";
//
//        when(agentDao.getByEmail(TEST_EMAIL)).thenReturn(mockAgent);
//        when(passwordEncoder.matches(TEST_PASSWORD, TEST_ENCODED_PASSWORD)).thenReturn(true);
//        when(passwordEncoder.encode(newPassword)).thenReturn(encodedNewPassword);
//        when(agentDao.updateById(any(Agent.class))).thenReturn(true);
//
//        ChangePasswordCmd cmd = new ChangePasswordCmd();
//        cmd.setEmail(TEST_EMAIL);
//        cmd.setOldPassword(TEST_PASSWORD);
//        cmd.setNewPassword(newPassword);
//
//        assertDoesNotThrow(() -> agentComponent.changePassword(cmd));
//
//        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
//        verify(agentDao).updateById(captor.capture());
//        assertEquals(encodedNewPassword, captor.getValue().getPassword());
//        assertEquals(mockAgent.getId(), captor.getValue().getId());
//
//        // 密码修改后应撤销所有旧 Token
//        verify(agentAuthTokenDao).revokeByAgentId(mockAgent.getId());
//    }
//
//    @Test
//    void changePassword_shouldThrowException_whenUserNotFound() {
//        when(agentDao.getByEmail(TEST_EMAIL)).thenReturn(null);
//
//        ChangePasswordCmd cmd = new ChangePasswordCmd();
//        cmd.setEmail(TEST_EMAIL);
//        cmd.setOldPassword(TEST_PASSWORD);
//        cmd.setNewPassword("newPassword");
//
//        RuntimeException ex = assertThrows(RuntimeException.class, () -> agentComponent.changePassword(cmd));
//        assertEquals("用户不存在", ex.getMessage());
//
//        verify(agentAuthTokenDao, never()).revokeByAgentId(anyLong());
//    }
//
//    @Test
//    void changePassword_shouldThrowException_whenOldPasswordWrong() {
//        when(agentDao.getByEmail(TEST_EMAIL)).thenReturn(mockAgent);
//        when(passwordEncoder.matches(TEST_PASSWORD, TEST_ENCODED_PASSWORD)).thenReturn(false);
//
//        ChangePasswordCmd cmd = new ChangePasswordCmd();
//        cmd.setEmail(TEST_EMAIL);
//        cmd.setOldPassword(TEST_PASSWORD);
//        cmd.setNewPassword("newPassword");
//
//        RuntimeException ex = assertThrows(RuntimeException.class, () -> agentComponent.changePassword(cmd));
//        assertEquals("旧密码不正确", ex.getMessage());
//
//        verify(agentDao, never()).updateById(any(Agent.class));
//        verify(agentAuthTokenDao, never()).revokeByAgentId(anyLong());
//    }
//}
