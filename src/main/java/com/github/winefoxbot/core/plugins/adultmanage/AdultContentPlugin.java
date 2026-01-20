package com.github.winefoxbot.core.plugins.adultmanage;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.plugins.adultmanage.config.AdultContentConfig;

/**
 * @author FlanChan
 */
@Plugin(
        name = "成人内容管理",
        description = "管理福利图片的内容模式、撤回策略等设置。",
        permission = Permission.ADMIN,
        iconPath = "icon/配置.png",
        order = 99,
        config = AdultContentConfig.class
)
public class AdultContentPlugin {}