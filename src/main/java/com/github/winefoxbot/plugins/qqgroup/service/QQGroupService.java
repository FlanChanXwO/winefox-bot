package com.github.winefoxbot.plugins.qqgroup.service;

import com.github.winefoxbot.plugins.qqgroup.model.entity.QQGroupAutoHandleAddRequestFeatureConfig;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-22:03
 */
public interface QQGroupService {
    QQGroupAutoHandleAddRequestFeatureConfig getOrCreateAutoHandleAddRequestConfig(Long groupId);


    boolean toggleAutoHandleAddRequestFeature(Long groupId, boolean enable, QQGroupAutoHandleAddRequestFeatureConfig config);

    boolean toggleAutoBlockAddRequestFeature(Long groupId, boolean enable, QQGroupAutoHandleAddRequestFeatureConfig config);
}
