package com.github.winefoxbot.core.aop.handler;

import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.core.model.vo.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * 全局异常处理器
 * 将异常转换为统一的 Result 响应结构
 * @author FlanChan
 */
@RestControllerAdvice
@Slf4j
public class GlobalMvcExceptionHandler {


    /**
     * 处理自定义业务逻辑校验异常 (BusinessException)
     * 例如：我们在 Service 中抛出的 "用户不存在"、"余额不足"
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务参数校验失败: {}", e.getMessage());
        return Result.error(e.getMessage());
    }


    /**
     * 处理自定义业务逻辑校验异常 (IllegalArgumentException)
     * 例如：我们在 Service 中抛出的 "路径不存在"、"文件过大"
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("业务参数校验失败: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    /**
     * 处理 IO 异常 (文件读写失败)
     */
    @ExceptionHandler(IOException.class)
    public Result<Void> handleIOException(IOException e) {
        log.error("文件操作失败: {}", e.getMessage());
        return Result.error("文件操作失败: " + e.getMessage());
    }

    /**
     * 处理 Spring 参数校验异常 (@RequestBody @Valid 校验失败)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        log.warn("参数格式校验失败: {}", msg);
        return Result.error(msg);
    }

    /**
     * 处理 Spring 参数绑定异常 (Get 请求参数绑定失败)
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getAllErrors().getFirst().getDefaultMessage();
        log.warn("参数绑定失败: {}", msg);
        return Result.error(msg);
    }

    /**
     * 处理 404 静态资源未找到 (Spring Boot 3+ 常见)
     * 防止前端请求了不存在的 API 导致返回 HTML 报错页面
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        return Result.error("请求的资源不存在: " + e.getResourcePath());
    }

    /**
     * 兜底处理：处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统未知异常 [URL: {}]: ", request.getRequestURI(), e);

        // 生产环境通常不直接把 e.getMessage() 给前端，防止泄露敏感信息
        // 但为了方便你调试文件管理器，这里暂时直接返回异常信息
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = "系统内部错误，请联系管理员";
        }
        return Result.error(message);
    }
}
