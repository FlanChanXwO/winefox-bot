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


-- 统一的群组推送日程表，替代原有的各插件独立表
CREATE TABLE IF NOT EXISTS group_push_schedule
(
    id              SERIAL PRIMARY KEY,
    group_id        BIGINT       NOT NULL,
    task_type       VARCHAR(64)  NOT NULL, -- 任务类型 e.g. 'WATER_GROUP_STAT', 'PIXIV_RANK'
    task_param      VARCHAR(64),           -- 任务参数 e.g. 'daily', 'weekly' (可选)
    cron_expression VARCHAR(64)  NOT NULL, -- Cron表达式
    description     TEXT,                  -- 描述
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, task_type, task_param)
);
CREATE INDEX IF NOT EXISTS idx_group_push_schedule_group ON group_push_schedule (group_id);



CREATE TABLE IF NOT EXISTS pixiv_author_subscription
(
    author_id       VARCHAR(20) PRIMARY KEY,
    author_name     VARCHAR(255),
    is_active       BOOLEAN   NOT NULL DEFAULT TRUE,
    last_checked_at TIMESTAMP, -- 使用 timestamptz 是 PostgreSQL 的好习惯
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS pixiv_user_author_subscription_schedule_ref
(
    id         SERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL, -- 引用 bot_user.user_id
    author_id  VARCHAR(20) NOT NULL, -- 引用 author.author_id
    group_id   BIGINT      NOT NULL,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 确保一个用户在一个群里只能订阅同一个作者一次
    UNIQUE (user_id, author_id, group_id)
);

-- 创建 pixiv_work 表
CREATE TABLE IF NOT EXISTS pixiv_artwork
(
    id         SERIAL PRIMARY KEY,
    illust_id  VARCHAR(20) NOT NULL,
    author_id  VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_pixiv_work_illust_id ON pixiv_artwork (illust_id);


-- 为 author_id 创建普通索引，加快按作者查询和删除的速度
CREATE INDEX IF NOT EXISTS idx_pixiv_work_author_id ON pixiv_artwork (author_id);

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
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE shiro_friends IS '存储每个Bot的好友关系';
COMMENT ON COLUMN shiro_friends.bot_id IS 'Bot自身的ID';
COMMENT ON COLUMN shiro_friends.friend_id IS '好友的用户ID';

CREATE TABLE IF NOT EXISTS pixiv_author_subscription
(
    author_id       VARCHAR(20) PRIMARY KEY,
    author_name     VARCHAR(255),
    is_active       BOOLEAN   NOT NULL DEFAULT TRUE,
    last_checked_at TIMESTAMP, -- 使用 timestamptz 是 PostgreSQL 的好习惯
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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


CREATE TABLE IF NOT EXISTS webui_admin (
    -- 使用 BIGINT 作为主键，以支持未来大量用户。SERIAL8 是 BIGSERIAL 的别名，自动创建序列。
                                           id SERIAL PRIMARY KEY,

    -- 用户名，必须唯一，用于登录。使用 TEXT 或 VARCHAR 都可以，TEXT更灵活。
    -- 添加 UNIQUE 约束以确保用户名的唯一性，并为快速查找创建索引。
                                           username TEXT NOT NULL UNIQUE,

    -- 存储Bcrypt或Argon2算法生成的加盐哈希值。
    -- 长度通常在60个字符左右，但设置为255以提供足够的余量。
                                           password VARCHAR(255) NOT NULL,

    -- 用户角色，用于权限控制。可以根据需要扩展，例如 'viewer', 'editor'。
                                           role TEXT NOT NULL DEFAULT 'admin',

    -- 记录创建时间，默认为当前事务的时间戳。
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 记录最后更新时间。可以使用触发器自动更新，此处为简化暂不添加。
                                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 用于强制用户首次登录时修改密码。
    -- TRUE 表示需要修改，FALSE 表示已修改或无需修改。
                                           password_needs_change BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO webui_admin (username, password, role, password_needs_change)
VALUES
    (
        'admin',
        -- admin
        '$2a$10$gP4/3T4.3VL5i92158zXyuaGfB3qFf9I.tYm2r.I./qgE.6D/bDGC',
        'super_admin',
        TRUE  -- 推荐设置为 TRUE，强制首次登录时修改这个弱密码。
    )
ON CONFLICT (username) DO NOTHING;
