package com.github.winefoxbot.plugins.imgexploration;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.model.enums.Permission;
import com.mikuac.shiro.annotation.common.Shiro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-15-17:17
 */
@Plugin(
        name = "实用功能",
        description = "提供图片搜索功能",
        permission = Permission.USER,
        iconPath = "icon/实用工具.png",
        order = 5)
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class ImgExplorationPlugin {
}