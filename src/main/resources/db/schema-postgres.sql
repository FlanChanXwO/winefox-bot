-- PostgreSQL-specific schema
-- This script will only run when the platform is 'postgres'

CREATE TABLE IF NOT EXISTS shiro_users
(
    user_id      BIGINT    NOT NULL PRIMARY KEY,
    nickname     VARCHAR(255),
    avatar_url   TEXT,
    last_updated TIMESTAMP NOT NULL
);


CREATE TABLE IF NOT EXISTS shiro_groups
(
    group_id         BIGINT    NOT NULL PRIMARY KEY,
    group_name       VARCHAR(255),
    group_avatar_url TEXT,
    self_id        BIGINT    NOT NULL,
    member_count     INTEGER NOT NULL DEFAULT 0,
    max_member_count INTEGER NOT NULL DEFAULT 0,
    group_level      INTEGER NOT NULL DEFAULT 0,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    last_updated     TIMESTAMP NOT NULL
);


CREATE TABLE IF NOT EXISTS shiro_group_members
(
    group_id        BIGINT                      NOT NULL,
    user_id         BIGINT                      NOT NULL,
    member_nickname VARCHAR(255),
    role            VARCHAR(8) DEFAULT 'member' NOT NULL,
    last_updated    TIMESTAMP                   NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
comment on column shiro_group_members.role is '角色，owner 或 admin 或 member';
CREATE TABLE IF NOT EXISTS shiro_messages
(
    id           SERIAL PRIMARY KEY,   -- Use SERIAL for auto-incrementing integer
    message_id   BIGINT      NOT NULL,
    time         TIMESTAMP   NOT NULL,
    self_id      BIGINT      NOT NULL,
    direction    VARCHAR(20) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    user_id      BIGINT      NOT NULL,
    session_id   BIGINT NOT NULL ,
    message      JSON        NOT NULL, -- PostgreSQL has native JSON support
    plain_text   TEXT        NOT NULL
);



CREATE TABLE IF NOT EXISTS water_group_msg_stat
(
    id        SERIAL PRIMARY KEY,
    user_id   BIGINT  NOT NULL,
    group_id  BIGINT  NOT NULL,
    msg_count INTEGER NOT NULL DEFAULT 0,
    date      DATE    NOT NULL,      -- 新增：记录统计的日期
    UNIQUE (user_id, group_id, date) -- 确保每个群的每个用户在每个日期只有一条记录
);


CREATE TABLE IF NOT EXISTS shiro_scheduled_task
(
    id              SERIAL PRIMARY KEY,

    -- 核心：目标定义
    target_type     VARCHAR(20) NOT NULL, -- 枚举: 'GROUP', 'PRIVATE'
    target_id       BIGINT      NOT NULL, -- 逻辑关联 shiro_groups.group_id 或 shiro_users.user_id

    -- 任务定义
    task_type       VARCHAR(64) NOT NULL, -- e.g. 'WATER_GROUP_STAT', 'DAILY_NEWS'
    task_param      JSONB,                -- 任务参数，推荐用 JSONB

    -- 调度定义
    cron_expression VARCHAR(64) NOT NULL,
    is_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    description     TEXT,                 -- WebUI 备注
    bot_id          BIGINT      NOT NULL,
    -- 运行状态
    last_run_at     TIMESTAMP,

    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引：为了让 "查询某群所有任务" 和 "查询某人所有任务" 变快
CREATE INDEX idx_bot_task_target ON shiro_scheduled_task (target_type, target_id);


CREATE TABLE IF NOT EXISTS pixiv_author_monitor
(
    author_id         VARCHAR(20) PRIMARY KEY, -- P站 ID 也是字符串
    author_name       VARCHAR(255),

    latest_illust_id  VARCHAR(20),             -- 最新作品ID (Checkpoint)
    latest_checked_at TIMESTAMP,               -- 上次检查时间

    is_monitored      BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS shiro_event_subscription
(
    id              SERIAL PRIMARY KEY,

    -- 订阅源 (Subject)
    event_type      VARCHAR(64) NOT NULL,
    event_key       VARCHAR(64) NOT NULL,

    -- 消息发送目标 (Target)
    target_type     VARCHAR(20) NOT NULL, -- 'GROUP' 或 'PRIVATE'
    target_id       BIGINT      NOT NULL, -- 关联 shiro_groups.group_id 或 shiro_users.user_id

    -- 具体的AT对象 (Mention)
    -- 如果 target_type='GROUP'，这里填需要AT的群员QQ (关联 shiro_users.user_id)
    -- 如果 target_type='PRIVATE'，这里通常为 NULL (或者填和 target_id 一样的值)
    mention_user_id BIGINT,
    bot_id          BIGINT      NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (event_type, event_key, target_type, target_id, mention_user_id)
);

CREATE INDEX idx_event_sub_lookup ON shiro_event_subscription (event_type, event_key);


CREATE TABLE IF NOT EXISTS shiro_friend_requests
(
    id         SERIAL PRIMARY KEY,

    -- OneBot 11 关键数据
    flag       VARCHAR(255) NOT NULL,                   -- 处理请求时的凭证
    user_id    BIGINT       NOT NULL,                   -- 请求者的QQ号
    comment    TEXT,                                    -- 验证消息

    -- 辅助展示数据 (Bot收到请求时尽量获取并缓存，方便WebUI显示)
    nickname   VARCHAR(255),                            -- 请求者昵称快照
    avatar_url TEXT,                                    -- 头像链接快照

    -- 状态管理
    status     VARCHAR(20)  NOT NULL, -- 'PENDING'(待处理), 'APPROVED'(已同意), 'REJECTED'(已拒绝), 'IGNORED'(已忽略)
    bot_id    BIGINT       NOT NULL,                   -- 收到请求的Bot账号
    handled_at TIMESTAMP,                               -- 处理时间
    received_at TIMESTAMP    NOT NULL , -- 收到请求的时间

    -- 防止重复
    UNIQUE (flag)
);

-- 索引：快速加载WebUI中 "未处理的好友请求"
CREATE INDEX idx_shiro_friend_req_status ON shiro_friend_requests (status);

CREATE TABLE IF NOT EXISTS shiro_group_requests
(
    id         SERIAL PRIMARY KEY,

    flag       VARCHAR(255) NOT NULL,                   -- 处理请求时的凭证
    sub_type   VARCHAR(20)  NOT NULL,                   -- 'add' (他人申请加群) 或 'invite' (Bot被邀请入群)
    group_id   BIGINT       NOT NULL,                   -- 目标群号
    user_id    BIGINT       NOT NULL,                   -- 'add'时为申请人QQ，'invite'时为邀请人QQ
    comment    TEXT,                                    -- 验证消息

    -- 辅助展示数据
    group_name VARCHAR(255) NOT NULL ,                            -- 群名称快照
    group_avatar_url TEXT,                            -- 群头像链接快照
    nickname   VARCHAR(255) NOT NULL ,                            -- 用户(申请人/邀请人)昵称快照
    user_avatar_url TEXT,                                    -- 用户(申请人/邀请人)头像链接快照

    -- 状态管理
    status     VARCHAR(20)  NOT NULL, -- 'PENDING', 'APPROVED', 'REJECTED', 'IGNORED'

    bot_id    BIGINT       NOT NULL,                   -- 收到请求的Bot账号
    handled_at TIMESTAMP,                               -- 处理时间
    received_at TIMESTAMP    NOT NULL , -- 收到请求的时间

    UNIQUE (flag)
);

-- 索引：快速加载WebUI中 "未处理的群组请求"
CREATE INDEX idx_shiro_group_req_status ON shiro_group_requests (status);


-- winefoxbot 内置表
CREATE TABLE IF NOT EXISTS winefox_bot_app_config
(
    id           SERIAL PRIMARY KEY,
    -- 配置分组，便于管理和展示，例如在配置菜单中分类显示
    config_group TEXT NOT NULL,          -- e.g., '核心设置', '发言统计', '色图功能'

    -- 配置键，使用点分命名法，清晰明了
    config_key   TEXT NOT NULL,          -- e.g., 'core.owner', 'stats.enabled', 'setu.r18.enabled'

    -- 配置值，可以考虑使用 JSONB 类型以获得更好的灵活性和类型支持
    config_value jsonb,                   -- 对于简单的键值对 TEXT 足够，若配置项复杂可考虑 JSONB (PostgreSQL)

    -- 配置的作用范围
    scope        VARCHAR(16) NOT NULL,   -- 'global', 'group', 'user'
    scope_id     VARCHAR(64) NOT NULL,   -- 'default', '群号', 'QQ号'

    -- 附加信息
    description  TEXT,                   -- 配置项描述
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(), -- 记录创建时间
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(), -- 记录更新时间
    -- 唯一性约束，确保一个范围内配置键不重复
    UNIQUE (scope, scope_id, config_key)
);


-- 1. Bot信息表
-- 存储你的机器人账号自身的信息
CREATE TABLE IF NOT EXISTS shiro_bots
(
    bot_id         BIGINT    NOT NULL PRIMARY KEY, -- Bot自身的QQ号/ID
    nickname       VARCHAR(255),                  -- Bot的昵称
    avatar_url     TEXT,                          -- Bot的头像URL
    last_updated    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP -- 最后同步信息的时间
);
COMMENT ON TABLE shiro_bots IS '存储机器人账号自身的信息';
COMMENT ON COLUMN shiro_bots.bot_id IS 'Bot自身的QQ号或唯一标识符';


-- 2. Bot与好友关系表
-- 存储每个Bot的好友列表
CREATE TABLE IF NOT EXISTS shiro_friends
(
    bot_id       BIGINT    NOT NULL, -- 关联到shiro_bots表
    friend_id    BIGINT    NOT NULL, -- 好友的用户ID，关联到shiro_users表
    nickname     VARCHAR(255),       -- Bot对好友的备注名
    enabled BOOLEAN NOT NULL DEFAULT TRUE, -- 是否启用该好友关系
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE shiro_friends IS '存储每个Bot的好友关系';
COMMENT ON COLUMN shiro_friends.bot_id IS 'Bot自身的ID';
COMMENT ON COLUMN shiro_friends.friend_id IS '好友的用户ID';


-- Final DDL for PostgreSQL (Using Integer for Enums, fully compatible with MybatisPlus and Flyway)

-- =================================================================
-- Section 1:   Table Creation for pixiv_bookmark
-- 使用 IF NOT EXISTS 保证表创建的幂等性 (Flyway friendly)
-- =================================================================
CREATE TABLE IF NOT EXISTS pixiv_bookmark (
                                              id VARCHAR(20) PRIMARY KEY,
                                              tracked_user_id VARCHAR(20) NOT NULL,
                                              title TEXT NOT NULL,
                                              illust_type INT NOT NULL,
    -- x_restrict 使用 SMALLINT 存储数字 (0: ALL_AGES, 1: R18, 2: R18G)
                                              x_restrict SMALLINT NOT NULL,
                                              sl_level INT NOT NULL ,
                                              author_id VARCHAR(20),
                                              author_name TEXT NOT NULL ,
                                              image_url TEXT NOT NULL,
                                              width INT NOT NULL,
                                              height INT NOT NULL,
                                              page_count INT NOT NULL,
                                              tags JSONB NOT NULL,
                                              description TEXT,
                                              ai_type INT NOT NULL,
                                              pixiv_create_date TIMESTAMP NOT NULL,
                                              pixiv_update_date TIMESTAMP NOT NULL,
    -- 由 MybatisPlusMetaObjectHandler 自动填充
                                              create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Indexes for pixiv_bookmark
CREATE INDEX IF NOT EXISTS idx_pixiv_bookmark_tracked_user_id ON pixiv_bookmark (tracked_user_id);
CREATE INDEX IF NOT EXISTS idx_pixiv_bookmark_author_id ON pixiv_bookmark (author_id);
CREATE INDEX IF NOT EXISTS idx_pixiv_bookmark_tags_gin ON pixiv_bookmark USING GIN (tags);

-- Comments
COMMENT ON TABLE pixiv_bookmark IS '存储特定用户 P站收藏作品的信息';
COMMENT ON COLUMN pixiv_bookmark.x_restrict IS '作品分级 (0: ALL_AGES, 1: R18, 2: R18G)';
COMMENT ON COLUMN pixiv_bookmark.create_time IS '记录在本地数据库的创建时间';
COMMENT ON COLUMN pixiv_bookmark.update_time IS '记录在本地数据库的更新时间';

-- 创建运势表
CREATE TABLE IF NOT EXISTS public.fortune_data (
                                                      user_id BIGINT NOT NULL PRIMARY KEY, -- 用户QQ号/ID
                                                      star_num INT NOT NULL DEFAULT 0,     -- 运势星级 (0-7)
                                                      fortune_date DATE NOT NULL          -- 运势日期 (yyyy-MM-dd)
);

-- 添加注释
COMMENT ON TABLE public.fortune_data IS '今日运势数据表';
COMMENT ON COLUMN public.fortune_data.user_id IS '用户ID';
COMMENT ON COLUMN public.fortune_data.star_num IS '运势星级';
COMMENT ON COLUMN public.fortune_data.fortune_date IS '运势归属日期';



CREATE TABLE IF NOT EXISTS winefox_bot_connection_logs (
                                     id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, -- 自增主键
                                     bot_id         BIGINT    NOT NULL , -- Bot自身的QQ号/ID
                                     event_type VARCHAR(20) NOT NULL,    -- 事件类型：CONNECT, DISCONNECT, RECONNECT, ERROR
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 发生时间

    -- 索引优化
                                     CONSTRAINT idx_bot_log_search UNIQUE (created_at, bot_id) -- 联合索引优化按时间和账号查询
);

-- 如果数据量极大，建议按月分区（可选）
-- CREATE TABLE bot_connection_logs_2025_05 PARTITION OF bot_connection_logs ...
