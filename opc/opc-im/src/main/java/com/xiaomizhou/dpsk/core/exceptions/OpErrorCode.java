package com.xiaomizhou.dpsk.core.exceptions;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码枚举（1000 起编号，与 HTTP 状态码解耦）
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public enum OpErrorCode {

    /** 请求参数校验失败 */
    PARAM_ERROR(1000, HttpStatus.BAD_REQUEST),

    /** 认证失败：Token 无效/过期、账号密码错误 */
    AUTH_FAILED(1001, HttpStatus.UNAUTHORIZED),

    /** 无权限操作 */
    FORBIDDEN(1002, HttpStatus.FORBIDDEN),

    /** 资源不存在 */
    NOT_FOUND(1003, HttpStatus.NOT_FOUND),

    /** 资源冲突（如邮箱已注册、Agent Code 重复） */
    CONFLICT(1004, HttpStatus.CONFLICT),

    /** 通用业务逻辑错误 */
    BUSINESS_ERROR(1005, HttpStatus.UNPROCESSABLE_ENTITY),

    /** 请求频率超限 */
    RATE_LIMITED(1006, HttpStatus.TOO_MANY_REQUESTS),

    /** 服务器内部未知异常（兜底） */
    INTERNAL_ERROR(1007, HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final HttpStatus httpStatus;

    OpErrorCode(int code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
