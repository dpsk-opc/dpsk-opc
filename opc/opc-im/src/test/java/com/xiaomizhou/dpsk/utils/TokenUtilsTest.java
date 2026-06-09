package com.xiaomizhou.dpsk.utils;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenUtils 单元测试
 *
 * @author eason
 * @date 2026/5/23
 */
class TokenUtilsTest {

    @Test
    void generateToken_shouldReturnNonEmptyString() {
        String token = TokenUtils.generateToken();
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateToken_shouldNotContainDash() {
        String token = TokenUtils.generateToken();
        assertFalse(token.contains("-"), "Token 不应包含横线");
    }

    @Test
    void generateToken_shouldBe32Chars() {
        String token = TokenUtils.generateToken();
        assertEquals(32, token.length(), "UUID 去横线后应为 32 字符");
    }

    @Test
    void generateToken_shouldBeUnique() {
        String token1 = TokenUtils.generateToken();
        String token2 = TokenUtils.generateToken();
        assertNotEquals(token1, token2);
    }

    @Test
    void generateExpireTime_default_shouldBe7DaysLater() {
        Date now = new Date();
        Date expireTime = TokenUtils.generateExpireTime();

        long diffMs = expireTime.getTime() - now.getTime();
        long sevenDaysMs = 7L * 24 * 60 * 60 * 1000;

        // 允许 1 秒误差
        assertTrue(Math.abs(diffMs - sevenDaysMs) < 1000,
                "默认过期时间应为 7 天后，实际差值: " + diffMs + "ms");
    }

    @Test
    void generateExpireTime_customDays_shouldBeCorrect() {
        Date now = new Date();
        Date expireTime = TokenUtils.generateExpireTime(30);

        long diffMs = expireTime.getTime() - now.getTime();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;

        assertTrue(Math.abs(diffMs - thirtyDaysMs) < 1000,
                "自定义 30 天过期时间应为 30 天后，实际差值: " + diffMs + "ms");
    }

    @Test
    void generateExpireTime_shouldBeInFuture() {
        Date expireTime = TokenUtils.generateExpireTime();
        assertTrue(expireTime.after(new Date()), "过期时间应该在当前时间之后");
    }
}
