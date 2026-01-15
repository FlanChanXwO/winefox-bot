package com.github.winefoxbot.core.config.request.interceptor;

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
        IOException lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                response = chain.proceed(request);

                // 如果请求成功，直接返回响应，让调用者处理
                if (response.isSuccessful()) {
                    return response;
                }

                // 如果是客户端错误 (4xx)，这种错误重试也无用，直接返回
                if (isClientError(response.code())) {
                    log.warn("Request to {} failed with client error code {}. No retries will be performed.", request.url(), response.code());
                    return response; // 直接返回这个失败的响应
                }

                // 请求失败（例如 404, 500），关闭当前响应体准备重试
                // 注意：这里必须关闭，因为我们要进行下一次循环，这个response没用了
                response.close();

                log.warn("Request to {} failed with code {}. Attempt {}/{}. Retrying...",
                        request.url(), response.code(), attempt + 1, maxRetries);

            } catch (IOException e) {
                lastException = e;
                log.warn("Request to {} failed with IOException. Attempt {}/{}. Retrying... Error: {}",
                        request.url(), attempt + 1, maxRetries, e.getMessage());
            }

            // 如果还不是最后一次尝试，就等待一段时间
            if (attempt < maxRetries - 1) {
                try {
                    TimeUnit.MILLISECONDS.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 提前因中断而抛出异常，附加上一次的网络异常信息
                    throw new IOException("Retry was interrupted", lastException != null ? lastException : e);
                }
            }
        }

        // 如果循环结束仍然没有成功，抛出最后的异常
        if (lastException != null) {
            throw new IOException("Failed to execute request for " + request.url() + " after " + maxRetries + " attempts.", lastException);
        }

        // 如果循环结束是因为最后一次尝试返回了失败的HTTP代码，则返回这个失败的response
        // 注意：此时response不能关闭，需要由最终的调用者关闭
        return response;
    }

    /**
     * 判断HTTP状态码是否为客户端错误 (4xx).
     * @param code The HTTP status code.
     * @return true if the code is between 400 and 499, false otherwise.
     */
    private boolean isClientError(int code) {
        return code >= 400 && code < 500;
    }
}
