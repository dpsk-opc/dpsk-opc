package com.xiaomizhou.dpsk.core.exceptions;

import com.xiaomizhou.dpsk.core.model.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，统一拦截所有未捕获异常并转换为标准 Response 格式。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常 [{}] {} - {}", ex.getCode(), ex.getMessage(), request.getRequestURI());
        return buildResponse(ex.getCode(), ex.getMessage());
    }

    /**
     * 兜底：未知异常
     */
    @ExceptionHandler(Exception.class)
    public Response<Void> handleUnknownException(Exception ex, HttpServletRequest request) {
        log.error("系统异常 - {} - {}", ex.getMessage(), request.getRequestURI(), ex);
        return buildResponse(OpErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误");
    }

    private Response<Void> buildResponse(int code, String msg) {
        Response<Void> response = new Response<>();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }
}
