package com.xiaomizhou.dpsk.core.exceptions;

/**
 * 业务异常基类，所有业务异常均继承此类。
 * 由 GlobalExceptionHandler 统一拦截并转换为标准 Response。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class BusinessException extends RuntimeException {

    private final OpErrorCode errorCode;

    public BusinessException(OpErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(OpErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public OpErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    /**
     * 便捷工厂方法
     */

    public static BusinessException paramError(String message) {
        return new BusinessException(OpErrorCode.PARAM_ERROR, message);
    }

    public static BusinessException authFailed(String message) {
        return new BusinessException(OpErrorCode.AUTH_FAILED, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(OpErrorCode.FORBIDDEN, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(OpErrorCode.NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(OpErrorCode.CONFLICT, message);
    }

    public static BusinessException businessError(String message) {
        return new BusinessException(OpErrorCode.BUSINESS_ERROR, message);
    }

    public static BusinessException rateLimited(String message) {
        return new BusinessException(OpErrorCode.RATE_LIMITED, message);
    }

    public static BusinessException internalError(String message, Throwable cause) {
        return new BusinessException(OpErrorCode.INTERNAL_ERROR, message, cause);
    }

    public static BusinessException internalError(String message) {
        return new BusinessException(OpErrorCode.INTERNAL_ERROR, message);
    }
}
