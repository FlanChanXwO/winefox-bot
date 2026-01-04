// File: MybatisPlusMetaObjectHandler.java
package com.github.winefoxbot.core.aop.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * 用于自动填充 create_time 和 update_time 字段
 */
@Slf4j
@Component // 注册为 Spring Bean
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时的填充策略
     * @param metaObject 元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("start insert fill ....");
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "time", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "lastUpdated", LocalDateTime.class, now);
    }

    /**
     * 更新时的填充策略
     * @param metaObject 元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("start update fill ....");
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject,"lastUpdated", LocalDateTime.class, now);
    }
}
