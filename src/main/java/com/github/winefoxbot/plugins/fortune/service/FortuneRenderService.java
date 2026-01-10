package com.github.winefoxbot.plugins.fortune.service;


import com.github.winefoxbot.plugins.fortune.model.vo.FortuneRenderVO;

public interface FortuneRenderService {
    /**
     * 将运势数据渲染为图片字节数组
     *
     * @param data 视图数据对象
     * @return 图片的 byte[]
     * @throws Exception 渲染失败抛出异常
     */
    byte[] render(FortuneRenderVO data) throws Exception;
}
