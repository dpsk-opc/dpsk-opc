package com.xiaomizhou.dpsk.core.config;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.response.Response;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Flux;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/21 13:55
 * @description
 */
@RestControllerAdvice
public class ResponseWrapperAdvice implements org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {

        Class<?> parameterType = returnType.getParameterType();

        // flux
        if (parameterType == Flux.class) {
            return false;
        }

        if (parameterType == Response.class) {
            return false;
        }

        // 排除静态资源、文件下载等特殊响应
        String path = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest().getRequestURI();
        if (path.contains("/error") || path.contains("/swagger") || path.contains("/uploads")) {
            return false;
        }

        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // 已经是包装过的响应，直接返回
        if (body instanceof Response<?>) {
            return body;
        }

//        // 处理 String 类型（特殊处理，避免类型转换异常）
//        if (body instanceof String) {
//            return JSON.toJSONString(Result.success(body));
//        }

        // 正常包装
        return Results.ok(body);
    }
}
