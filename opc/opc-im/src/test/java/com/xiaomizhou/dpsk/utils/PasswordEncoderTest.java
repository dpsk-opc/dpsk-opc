//package com.xiaomizhou.dpsk.utils;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * PasswordEncoder 单元测试
// *
// * @author eason
// * @date 2026/5/22
// */
//class PasswordEncoderTest {
//
//    private PasswordEncoder passwordEncoder;
//
//    @BeforeEach
//    void setUp() {
//        passwordEncoder = new PasswordEncoder();
//    }
//
//    @Test
//    void encode_shouldReturnBcryptHash() {
//        String raw = "myPassword123";
//        String encoded = passwordEncoder.encode(raw);
//
//        assertNotNull(encoded);
//        assertTrue(encoded.startsWith("$2a$") || encoded.startsWith("$2b$") || encoded.startsWith("$2y$"),
//                "Should produce BCrypt hash");
//        assertNotEquals(raw, encoded, "Encoded password should not equal raw password");
//    }
//
//    @Test
//    void matches_shouldReturnTrue_whenPasswordIsCorrect() {
//        String raw = "correctPassword";
//        String encoded = passwordEncoder.encode(raw);
//
//        assertTrue(passwordEncoder.matches(raw, encoded));
//    }
//
//    @Test
//    void matches_shouldReturnFalse_whenPasswordIsWrong() {
//        String raw = "correctPassword";
//        String encoded = passwordEncoder.encode(raw);
//
//        assertFalse(passwordEncoder.matches("wrongPassword", encoded));
//    }
//
//    @Test
//    void encode_shouldProduceDifferentHashes_forSamePassword() {
//        String raw = "samePassword";
//
//        String hash1 = passwordEncoder.encode(raw);
//        String hash2 = passwordEncoder.encode(raw);
//
//        // BCrypt 每次生成不同的 salt，因此哈希值不同
//        assertNotEquals(hash1, hash2);
//    }
//
//    @Test
//    void matches_shouldWork_withBothHashes_forSamePassword() {
//        String raw = "samePassword";
//
//        String hash1 = passwordEncoder.encode(raw);
//        String hash2 = passwordEncoder.encode(raw);
//
//        assertTrue(passwordEncoder.matches(raw, hash1));
//        assertTrue(passwordEncoder.matches(raw, hash2));
//    }
//
//    @Test
//    void matches_shouldReturnFalse_whenPasswordIsEmpty() {
//        String encoded = passwordEncoder.encode("somePassword");
//
//        assertFalse(passwordEncoder.matches("", encoded));
//        assertFalse(passwordEncoder.matches(null, encoded));
//    }
//}
