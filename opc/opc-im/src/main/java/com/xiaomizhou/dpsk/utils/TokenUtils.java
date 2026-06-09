package com.xiaomizhou.dpsk.utils;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

/**
 * Token 生成工具类
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/23
 */
public class TokenUtils {

    /**
     * Token 默认过期天数
     */
    private static final int DEFAULT_EXPIRE_DAYS = 7;

    private TokenUtils() {
    }

    /**
     * 生成随机 Token 字符串
     *
     * @return UUID 去横线的字符串
     */
    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成默认 7 天后的过期时间
     *
     * @return 过期时间
     */
    public static Date generateExpireTime() {
        return generateExpireTime(DEFAULT_EXPIRE_DAYS);
    }

    /**
     * 生成指定天数后的过期时间
     *
     * @param days 天数
     * @return 过期时间
     */
    public static Date generateExpireTime(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }
}
