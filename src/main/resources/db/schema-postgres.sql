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
comment on column public.shiro_group_members.role is '角色，owner 或 admin 或 member';
CREATE TABLE IF NOT EXISTS shiro_messages
(
    id           SERIAL PRIMARY KEY,   -- Use SERIAL for auto-incrementing integer
    message_id   BIGINT      NOT NULL,
    time         TIMESTAMP   NOT NULL,
    self_id      BIGINT      NOT NULL,
    direction    VARCHAR(20) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    user_id      BIGINT      NOT NULL,
    group_id     BIGINT,
    message      JSON        NOT NULL, -- PostgreSQL has native JSON support
    plain_text   TEXT        NOT NULL
);
-- winefoxbot 内置表
CREATE TABLE IF NOT EXISTS app_config
(
    id           SERIAL PRIMARY KEY,     -- Use SERIAL for auto-incrementing ID
    config_group TEXT NOT NULL,          -- 配置分组, e.g., '发言统计', '核心设置'
    scope        TEXT NOT NULL,          -- 配置范围, e.g., 'global', 'group', 'user'
    scope_id     TEXT NOT NULL,          -- 范围ID, e.g., 'default', '群号', 'QQ号'
    config_key   TEXT NOT NULL,          -- 配置键, e.g., 'watergroup.stats.enabled'
    config_value TEXT,                   -- 配置值
    description  TEXT,                   -- 配置项描述（可选）
    UNIQUE (scope, scope_id, config_key) -- 确保每个范围下的配置键是唯一的
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

CREATE TABLE if not exists water_group_schedule
(
    id       SERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    time     TIME   NOT NULL,
    UNIQUE (group_id)
);
CREATE TABLE IF NOT EXISTS pixiv_rank_push_schedule
(
    id            SERIAL PRIMARY KEY,
    group_id      BIGINT       NOT NULL,
    rank_type     VARCHAR(10)  NOT NULL,
    -- 存储标准的 Cron 表达式
    -- 例如: '0 9 * * 1'   (每周一上午9:00)
    --       '0 10 1 * *'  (每月1号上午10:00)
    --       '0 8 * * *'   (每天上午8:00)
    cron_schedule VARCHAR(64) NOT NULL,
    description   TEXT, -- [可选] 对 Cron 的可读性描述，方便调试
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, rank_type)
);

-- 为查询创建索引
CREATE INDEX IF NOT EXISTS idx_cron_schedule ON pixiv_rank_push_schedule (cron_schedule);


CREATE TABLE IF NOT EXISTS dna_team
(
    id             SERIAL PRIMARY KEY,
    status         SMALLINT  NOT NULL DEFAULT 0, -- 0-未满 1-已满 2-已解散
    description    VARCHAR(64),
    mode           VARCHAR(32),
    member_count   SMALLINT  NOT NULL DEFAULT 1,
    max_members    SMALLINT  NOT NULL DEFAULT 4,
    create_user_id BIGINT    NOT NULL,
    group_id       BIGINT    NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INTEGER   NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_team_status ON dna_team (status);

CREATE TABLE IF NOT EXISTS dna_team_member
(
    id        SERIAL PRIMARY KEY,
    team_id   BIGINT    NOT NULL,
    user_id   BIGINT    NOT NULL,
    role      SMALLINT  NOT NULL DEFAULT 0, -- 0-成员 1-队长
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_team_user UNIQUE (team_id, user_id),
    CONSTRAINT fk_team_member_team
        FOREIGN KEY (team_id)
            REFERENCES dna_team (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_team_member_team_id ON dna_team_member (team_id);



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
CREATE TABLE IF NOT EXISTS pixiv_work
(
    id         SERIAL PRIMARY KEY,
    illust_id  VARCHAR(20) NOT NULL,
    author_id  VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_pixiv_work_illust_id ON pixiv_work (illust_id);


-- 为 author_id 创建普通索引，加快按作者查询和删除的速度
CREATE INDEX IF NOT EXISTS idx_pixiv_work_author_id ON pixiv_work (author_id);


-- 创建屏蔽用户表
-- 增加了 `group_id` 字段来实现分群管理
CREATE TABLE IF NOT EXISTS qq_group_add_request_blocked_users
(
    id         SERIAL PRIMARY KEY,               -- 自增主键
    group_id   BIGINT    NOT NULL,               -- 群号
    user_id    BIGINT    NOT NULL,               -- 被屏蔽用户的QQ号
    created_at TIMESTAMP NOT NULL DEFAULT NOW(), -- 屏蔽时间 (带时区)
    UNIQUE (group_id, user_id)                   -- 确保每个群里一个用户只被屏蔽一次
);

-- 为常用查询创建索引，提升性能
CREATE INDEX IF NOT EXISTS idx_blocked_users_group_id_user_id ON qq_group_add_request_blocked_users (group_id, user_id);


-- 创建群功能配置表
CREATE TABLE IF NOT EXISTS qq_group_auto_handle_add_request_feature_config
(
    id                              SERIAL PRIMARY KEY,               -- 自增主键
    group_id                        BIGINT    NOT NULL UNIQUE,        -- 群号，设置为唯一，确保每个群只有一条记录
    auto_handle_add_request_enabled BOOLEAN   NOT NULL DEFAULT FALSE, -- 自动处理加群请求功能开关，默认关闭
    block_feature_enabled           BOOLEAN   NOT NULL DEFAULT FALSE, -- 屏蔽功能开关，默认关闭
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW(), -- 记录创建时间
    updated_at                      TIMESTAMP NOT NULL DEFAULT NOW()  -- 记录更新时间
);

-- 为 group_id 创建索引，加快查询速度
CREATE INDEX IF NOT EXISTS idx_group_feature_config_group_id ON qq_group_auto_handle_add_request_feature_config (group_id);


-- 创建色图配置表
CREATE TABLE IF NOT EXISTS setu_config
(
    id                     SERIAL PRIMARY KEY,                  -- 自增主键
    session_id             BIGINT     NOT NULL UNIQUE,          -- 群号，设置为唯一，确保每个群只有一条记录
    max_request_in_session INTEGER    NOT NULL DEFAULT 1,       -- 会话内最大请求数，超过则自动拒绝
    session_type           VARCHAR(8) NOT NULL DEFAULT 'group', -- 会话类型，如 'daily', 'weekly'
    r18_enabled            BOOLEAN    NOT NULL DEFAULT FALSE,   -- 是否开启R18
    auto_revoke            BOOLEAN    NOT NULL DEFAULT TRUE,    -- 是否自动撤回
    created_at             TIMESTAMP  NOT NULL DEFAULT NOW(),   -- 记录创建时间
    updated_at             TIMESTAMP  NOT NULL DEFAULT NOW()    -- 记录更新时间
);

