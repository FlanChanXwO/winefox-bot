-- SQLite3-specific schema
-- This script will only run when the platform is 'sqlite'

CREATE TABLE IF NOT EXISTS shiro_users (
                                           user_id      VARCHAR(64) NOT NULL PRIMARY KEY,
                                           nickname     VARCHAR(255),
                                           avatar_url   TEXT,
                                           last_updated DATETIME    NOT NULL
);
CREATE TABLE IF NOT EXISTS shiro_groups (
                                            group_id         VARCHAR(64) NOT NULL PRIMARY KEY,
                                            group_name       VARCHAR(255),
                                            group_avatar_url TEXT,
                                            last_updated     DATETIME    NOT NULL
);
CREATE TABLE IF NOT EXISTS shiro_group_members (
                                                   group_id         VARCHAR(64) NOT NULL,
                                                   user_id          VARCHAR(64) NOT NULL,
                                                   member_nickname  VARCHAR(255),
                                                   last_updated     DATETIME NOT NULL,
                                                   PRIMARY KEY (group_id, user_id),
                                                   FOREIGN KEY (group_id) REFERENCES shiro_groups(group_id),
                                                   FOREIGN KEY (user_id) REFERENCES shiro_users(user_id)
);
CREATE TABLE shiro_messages (
                                id           INTEGER      NOT NULL PRIMARY KEY, -- In SQLite, this is auto-incrementing
                                message_id   VARCHAR(255) NOT NULL,
                                time         DATETIME     NOT NULL,
                                self_id      VARCHAR(64)  NOT NULL,
                                direction    VARCHAR(20)  NOT NULL,
                                message_type VARCHAR(32)  NOT NULL,
                                user_id      VARCHAR(64)  NOT NULL,
                                group_id     VARCHAR(64),
                                message      TEXT         NOT NULL,             -- Store JSON as TEXT in SQLite
                                plain_text   TEXT         NOT NULL,
                                FOREIGN KEY (user_id) REFERENCES shiro_users(user_id),
                                FOREIGN KEY (group_id) REFERENCES shiro_groups(group_id)
);
CREATE TABLE IF NOT EXISTS shiro_schedule_task (
    -- 任务的机器唯一ID，由程序生成（如UUID），用作主键
                                                   id TEXT PRIMARY KEY,

    -- [新增] 任务的业务唯一标识符。
    -- 用于防止重复订阅，例如 "pixiv_rank_push:group:123456"。
    -- UNIQUE 约束由数据库保证其唯一性，查询速度极快。
    -- 允许为 NULL，以兼容那些没有“订阅”概念的一次性或内部任务。
                                                   task_id TEXT UNIQUE,

    -- 任务的可读描述，用于在后台或日志中展示给人看
                                                   description TEXT,

    -- 要执行的Spring Bean的名称
                                                   bean_name TEXT NOT NULL,

    -- 要调用的具体方法名
                                                   method_name TEXT NOT NULL,

    -- 调用方法时需要传递的参数，JSON字符串
                                                   task_params TEXT,

    -- Cron表达式，用于定义周期性任务
                                                   cron_expression TEXT,

    -- 任务调度类型 (RECURRING_INDEFINITE, RECURRING_WITH_COUNT, ONE_TIME)
                                                   schedule_type TEXT NOT NULL,

    -- 任务下一次的计划执行时间
                                                   next_execution_time DATETIME NOT NULL,

    -- 任务状态 (PENDING, RUNNING, COMPLETED, FAILED, CANCELLED)
                                                   status TEXT NOT NULL,

    -- [可选] 对于固定次数的任务，记录总共需要执行的次数
                                                   total_executions INTEGER,

    -- [可选] 记录已经成功执行了多少次
                                                   executed_count INTEGER DEFAULT 0,

    -- 任务创建时间
                                                   create_time DATETIME NOT NULL DEFAULT (datetime('now','localtime')),

    -- 任务最后更新时间
                                                   update_time DATETIME NOT NULL DEFAULT (datetime('now','localtime'))
);
CREATE INDEX IF NOT EXISTS idx_next_execution_time ON shiro_schedule_task(next_execution_time);

-- winefoxbot 内置表
CREATE TABLE IF NOT EXISTS app_config (
                                          id            INTEGER PRIMARY KEY AUTOINCREMENT,
                                          config_group  TEXT    NOT NULL,  -- 新增：配置分组, e.g., '发言统计', '核心设置'
                                          scope         TEXT    NOT NULL,  -- 配置范围, e.g., 'global', 'group', 'user'
                                          scope_id      TEXT    NOT NULL,  -- 范围ID, e.g., 'default', '群号', 'QQ号'
                                          config_key    TEXT    NOT NULL,  -- 配置键, e.g., 'watergroup.stats.enabled'
                                          config_value  TEXT   ,           -- 配置值
                                          description   TEXT   ,           -- 配置项描述（可选）
                                          UNIQUE(scope, scope_id, config_key) -- 确保每个范围下的配置键是唯一的
);



-- pixiv 插件
CREATE TABLE IF NOT EXISTS pixiv_rank_push_subscription
(
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,  -- 自增ID
    type                TEXT NOT NULL,                      -- 订阅类型，'group' 或 'user'
    group_id            INTEGER,                             -- 群组ID，只有当 type 为 'group' 时有效
    user_id             INTEGER,                             -- 用户ID，只有当 type 为 'user' 时有效
    enabled_r18         INTEGER,                             -- 是否启用R18 (0 或 1)
    subscription_ranges TEXT,                                -- 订阅范围列表，存储为逗号分隔的字符串
    UNIQUE(type, group_id, user_id)                          -- 确保每个号码对于每种类型的订阅只有一条记录
);