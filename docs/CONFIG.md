# 前置准备

## 安装字体
字体文件在 `fonts` 目录下，你需要安装才能使用本项目所有功能。

# 📝 配置文件详解

本文档详细说明了 `application-secret.yaml` 中的配置项。

💡 Tip: 你不需要自己运行数据库脚本来初始化数据库，你只需要连接数据库就会开始初始化了。

## 配置项说明

```yaml
spring:
  ai:
    openai:
      # AI服务的API Key
      api-key: "sk-123456" 
      # AI服务的地址，你可以去 api.ablai.top 来申请Key使用
      base-url: "https://api.ablai.top"
      chat:
        options:
          model: gpt-5-mini
          # 设置温度，越低回答越简短直接
          temperature: 0.7
          max-completion-tokens: 2000
  # 数据库和缓存配置（必配）
  datasource:
    # 数据库连接URL
    url: "jdbc:postgresql://localhost:54320/winefoxbot"
    # 数据库用户名
    username: "postgres" 
    # 数据库密码
    password: "postgres123" 
  data:
    redis:
      host: localhost
      port: 63790
      password: redis
      database: 0 # 使用哪个数据库，默认为 0
# 应用管理员和机器人账号
winefox:
  robot:
    # 超级管理员QQ号
    superusers:
      - 1241414114
    nickname: 酒狐
    bot-id: 1123141
    master-name: master
  ai:
    chat:
      context-size: 20 # 聊天上下文消息数量
      enable-image-analysis: true
      avatar: arti
      max-context-tokens: 30000

# Shiro WebSocket 访问令牌
shiro:
  ws:
    # WebSocket的访问令牌
    access-token: "suCiTU_{o.adadadad<"

playwright:
  device-scale-factor: 2.0
  headless: true
winefoxbot:
  plugins:
    dailyreport:
      pre-generate-cron: "0 0 8 * * ?" # 每天8点执行一次
    img-exploration:
      serp-apikeys: [sdadada]
      sauce-nao-api-key: dadad
      ascii2d-session-id: deda
    # Pixiv 相关敏感配置
    pixiv:
      cookie:
        # Cookie-Editor 插件导出的 Cookie 信息
        # 如果收藏夹不公开，请填入你自己的Cookie信息
        PAb-id: "5"
        phpsessid: "11424"
      bookmark:
        tracker:
          enabled: true # 是否启用定时任务
          light-cron: "0 1 * * * ?" # 每小时的第1分钟执行一次增量更新
          full-cron: "0 0 3 * * ?" # 每天凌晨3点执行一次全量更新
          target-user-id: "11" # 需要跟踪的用户ID
        # 是否允许自动定时取消收藏已下架或过期的作品
        allow-unmark-expired-artworks: true
      authorization:
        # 登录Pixiv(一定要登录成功后再获取x-csrf-token)
        # 按F12打开控制台
        # 随便点击一张图片的收藏按钮,收藏一张图片
        # 在请求头中查看 x-csrf-token,复制值
        x-csrf-token: 3131
```
