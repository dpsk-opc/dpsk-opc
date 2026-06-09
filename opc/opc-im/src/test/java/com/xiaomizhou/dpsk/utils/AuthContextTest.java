//package com.xiaomizhou.dpsk.utils;
//
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.Test;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * AuthContext 单元测试
// *
// * @author eason
// * @date 2026/5/23
// */
//class AuthContextTest {
//
//    @AfterEach
//    void tearDown() {
//        AuthContext.clear();
//    }
//
//    @Test
//    void setAndGetAgentCode_shouldWork() {
//        AuthContext.setAgentCode("AGT-001");
//        assertEquals("AGT-001", AuthContext.getAgentCode());
//    }
//
//    @Test
//    void getAgentCode_shouldReturnNull_whenNotSet() {
//        assertNull(AuthContext.getAgentCode());
//    }
//
//    @Test
//    void setAndGetAgentId_shouldWork() {
//        AuthContext.setAgentId(100L);
//        assertEquals(100L, AuthContext.getAgentId());
//    }
//
//    @Test
//    void getAgentId_shouldReturnNull_whenNotSet() {
//        assertNull(AuthContext.getAgentId());
//    }
//
//    @Test
//    void clear_shouldRemoveAllValues() {
//        AuthContext.setAgentCode("AGT-001");
//        AuthContext.setAgentId(100L);
//
//        AuthContext.clear();
//
//        assertNull(AuthContext.getAgentCode());
//        assertNull(AuthContext.getAgentId());
//    }
//
//    @Test
//    void headerConstants_shouldBeCorrect() {
//        assertEquals("Agent-Code", AuthContext.HEADER_AGENT_CODE);
//        assertEquals("Access-Token", AuthContext.HEADER_ACCESS_TOKEN);
//    }
//}
