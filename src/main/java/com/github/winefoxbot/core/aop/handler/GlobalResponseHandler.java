package com.github.winefoxbot.core.aop.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.model.vo.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局统一响应封装
 * @author FlanChan
 */
@RestControllerAdvice(basePackages = "com.github.winefoxbot.core.controller")
@RequiredArgsConstructor
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return !returnType.getParameterType().equals(Result.class)
                && !returnType.getParameterType().equals(ResponseEntity.class);
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {

        // 1. 如果是 String 类型，需要特殊处理，否则会报错 ClassCastException
        if (String.class.equals(returnType.getParameterType())) {
            if (body == null) {
                // 如果原本是 null，手动把 Result 转成 JSON 字符串返回
                try {
                    return objectMapper.writeValueAsString(Result.ok());
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            // 如果原本有 String 内容，可能也需要包装（视你需求而定）
            return objectMapper.writeValueAsString(Result.ok(body));
        }

        // 2. 处理 null (包括 void 方法)
        if (body == null) {
            return Result.ok();
        }

        // 3. 防止重复包装 (如果 Controller 本身就返回了 Result)
        if (body instanceof Result) {
            return body;
        }

        // 4. 正常包装
        return Result.ok(body);
    }
}
