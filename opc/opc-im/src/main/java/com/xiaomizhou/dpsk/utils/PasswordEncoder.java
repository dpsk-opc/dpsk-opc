package com.xiaomizhou.dpsk.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码编码器工具
 * <p>
 * 使用 BCrypt 算法对密码进行哈希，内置 salt 机制。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22
 */
@Component
public class PasswordEncoder {

    private final BCryptPasswordEncoder encoder;

    public PasswordEncoder() {
        this.encoder = new BCryptPasswordEncoder();
    }

    /**
     * 对明文密码进行 BCrypt 编码
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希
     */
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 校验明文密码与 BCrypt 哈希是否匹配
     *
     * @param rawPassword     明文密码
     * @param encodedPassword BCrypt 哈希
     * @return true 匹配, false 不匹配
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
