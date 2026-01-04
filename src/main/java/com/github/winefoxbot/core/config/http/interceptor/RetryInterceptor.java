package com.github.winefoxbot.core.config.http.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RetryInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(RetryInterceptor.class);

    private final int maxRetries;
    private final long retryDelayMs;

    public RetryInterceptor(int maxRetries, long retryDelayMs) {
        // 总尝试次数 = 1次初始尝试 + (maxRetries-1)次重试
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = null;
        IOException exception = null;

        int attempt = 0;
        // 使用 <= 是因为我们总共要尝试 maxRetries 次
        while (attempt < maxRetries) {
            attempt++;
            try {
                // 如果 response 已存在 (来自上一次失败的循环)，先关闭它
                if (response != null) {
                    response.close();
                }

                response = chain.proceed(request);

                // 如果请求成功，或者这是最后一次尝试，就跳出循环
                if (response.isSuccessful() || attempt == maxRetries) {
                    break;
                }

                log.debug("Attempt {} for {} failed with code {}. Retrying in {} ms...",
                        attempt, request.url(), response.code(), retryDelayMs);

            } catch (IOException e) {
                exception = e;
                log.debug("Request failed on attempt {}: {}", attempt, e.getMessage());

                // 如果这是最后一次尝试，就跳出循环，让外部抛出异常
                if (attempt == maxRetries) {
                    break;
                }
                log.debug("Retrying in {} ms...", retryDelayMs);
            }

            // 如果不是最后一次尝试，就等待
            try {
                TimeUnit.MILLISECONDS.sleep(retryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Retry was interrupted", e);
            }
        }

        // 循环结束后，处理最终结果
        if (exception != null) {
            // 如果最后是以IO异常结束，抛出它
            throw new IOException("Failed to execute request for " + request.url() + " after " + maxRetries + " attempts.", exception);
        }

        // 无论是成功的响应，还是最后一次失败的响应，都原样返回
        // 此时的 response Body 是未被读取和关闭的，调用方可以安全使用
        return response;
    }
}
