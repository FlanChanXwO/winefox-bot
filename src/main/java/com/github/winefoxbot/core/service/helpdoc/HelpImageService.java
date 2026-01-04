package com.github.winefoxbot.core.service.helpdoc;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface HelpImageService {
    /**
     * 生成包含所有功能分组的完整帮助图片。
     *
     * @return 图片的字节数组
     */
    byte[] generateAllHelpImage() throws IOException, ExecutionException, InterruptedException, TimeoutException;

    /**
     * 根据分组名称生成该分组的帮助图片。
     *
     * @param groupName 要生成图片的分组名称
     * @return 图片的字节数组，如果分组不存在则返回 null 或抛出异常
     */
    byte[] generateHelpImageByGroup(String groupName) throws IOException, ExecutionException, InterruptedException, TimeoutException;
}
