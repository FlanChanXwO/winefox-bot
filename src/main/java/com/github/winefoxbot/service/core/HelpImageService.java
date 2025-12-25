package com.github.winefoxbot.service.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface HelpImageService {

    /**
     * 生成包含所有功能分组的帮助图片。
     *
     * @param backgroundImageStream 可选的背景图片输入流
     * @return 生成的图片
     * @throws IOException 如果图片读写发生错误
     */
    BufferedImage generateAllHelpImage(Optional<InputStream> backgroundImageStream) throws IOException;

    /**
     * 生成指定功能分组的帮助图片。
     *
     * @param groupName             要生成帮助的分组名称
     * @param backgroundImageStream 可选的背景图片输入流
     * @return 生成的图片，如果分组不存在则返回 null
     * @throws IOException 如果图片读写发生错误
     */
    BufferedImage generateGroupHelpImage(String groupName, Optional<InputStream> backgroundImageStream) throws IOException;
}
