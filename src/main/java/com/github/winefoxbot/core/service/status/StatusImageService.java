package com.github.winefoxbot.core.service.status;

import java.io.IOException;

public interface StatusImageService {

    /**
     * 生成系统状态图片
     *
     * @return 图片的字节数组 (PNG格式，背景透明)
     * @throws IOException          当读取模板或图片资源失败时
     * @throws InterruptedException 当系统信息采样线程被中断时
     */
    byte[] generateStatusImage() throws IOException, InterruptedException;
}

