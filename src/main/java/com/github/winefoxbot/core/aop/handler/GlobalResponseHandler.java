package com.github.winefoxbot.core.aop.handler;

import com.github.winefoxbot.core.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * @author FlanChan
 */
@RestControllerAdvice(basePackages = "com.flanchan.webui.controller")
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        // 排除已经被 Result 包装过的，或者是 Swagger/Actuator 等不需要包装的接口
        return !returnType.getParameterType().equals(Result.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // 如果返回的是 String，需要特殊处理，因为 StringConverter 会报错
        if (body instanceof String) {
            // 这里可能需要依赖 Jackson 或其他 JSON 库手动序列化
            return body; 
        }
        // 自动包装
        return Result.success(body);
    }
}
