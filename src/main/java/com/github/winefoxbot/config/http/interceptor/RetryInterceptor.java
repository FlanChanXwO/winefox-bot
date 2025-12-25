package com.github.winefoxbot.config.http.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(RetryInterceptor.class);

    private final int maxRetries;
    private final long retryDelayMs;

    public RetryInterceptor(int maxRetries, long retryDelayMs) {
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = null;
        IOException exception = null;

        int attempt = 0;
        while (attempt < maxRetries) {
            attempt++;
            try {
                response = chain.proceed(request);
                // 如果请求成功 (2xx)，直接返回响应
                if (response.isSuccessful()) {
                    return response;
                }
            } catch (IOException e) {
                exception = e;
                log.warn("Request failed on attempt {}: {}", attempt, e.getMessage());
            }

            // 如果响应不成功或捕获到IO异常，则进行重试
            if (response != null) {
                // 关闭上一次失败的响应体，防止资源泄露
                response.close();
            }

            log.warn("Attempt {} for {} failed. Retrying in {} ms.", attempt, request.url(), retryDelayMs);

            if (attempt < maxRetries) {
                try {
                    TimeUnit.MILLISECONDS.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry was interrupted", e);
                }
            }
        }
        
        // 如果所有尝试都失败了
        if (exception != null) {
            throw new IOException("Failed to execute request for " + request.url() + " after " + maxRetries + " attempts.", exception);
        } else {
            // 如果最后一次是HTTP错误而不是IO异常
            return response; // 返回最后一次失败的响应，让调用方处理
        }
    }
}
