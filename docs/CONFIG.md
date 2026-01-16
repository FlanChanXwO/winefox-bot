# 📝 配置文件详解

本文档详细说明了 `src/main/resources/application-secret.yaml` 中的配置项。
**安全警告**：此文件包含 API Key 和数据库密码，请务必将其加入 `.gitignore`。

## 1. AI 模型配置 (OpenAI/DeepSeek)
支持兼容 OpenAI 格式的 API。

```yaml
spring:
  ai:
    openai:
      # API 密钥 (建议使用系统环境变量 ${OPENAI_API_KEY})
      api-key: "sk-xxxxxxxxxxxxxxxx" 
      # API 接口地址 (例如 DeepSeek 官方或自建代理)
      base-url: "https://api.ablai.top"
      chat:
        options:
          # 模型名称 (gpt-5-mini, deepseek-chat 等)
          model: gpt-5-mini
          # 随机性 (0.0 - 1.0)，0.7 为平衡点
          temperature: 0.7
          # 单次回复最大 Token
          max-completion-tokens: 2000
    chat:
      # 上下文记忆的消息条数
      context-size: 20
      # 是否开启图片分析能力 (Vision)
      enable-image-analysis: true
```

## 2. 数据库与缓存
对应 `env/docker-compose.yaml` 中的服务配置。

```yaml
spring:
  datasource:
    # ⚠️ 注意端口为 54320
    url: "jdbc:postgresql://localhost:54320/winefoxbot"
    username: "postgres"
    password: "postgres123" # ⚠️ 极度敏感
  data:
    redis:
      host: localhost
      # ⚠️ 注意端口为 63790
      port: 63790
      password: redis
      database: 0
```

## 3. Bilibili 解析配置
用于获取高清封面、动态详情，必须配置 Cookie 才能突破访客限制。

```yaml
analysis-bilibili:
  # 浏览器访问 B 站控制台输入 document.cookie 获取
  # 关键字段：SESSDATA (身份), bili_jct (CSRF防御), DedeUserID (用户ID)
  cookie: "DedeUserID=...; SESSDATA=...; bili_jct=...;"
```

## 4. 机器人管理员与更新

```yaml
winefox:
  robot:
    # 超级管理员 QQ 号列表 (拥有重启、更新、执行Shell等最高权限)
    superusers:
      - 3085974224
    nickname: 酒狐
    bot-id: 1357811947
  app:
    update:
      # 用于自动检查 GitHub 仓库更新的 Token (PAT)
      github-token: "ghp_xxxx"
```

## 5. Pixiv 插件配置 (核心功能)
这是最复杂的配置部分，用于 P 站搜图和追踪。

```yaml
pixiv:
  cookie:
    # 推荐使用 Chrome 插件 "Cookie-Editor" 导出
    # 必须包含 PHPSESSID 以维持登录状态
    phpsessid: "xxxx_xxxx"
  bookmark:
    tracker:
      enabled: true
      # 增量更新 Cron 表达式 (每小时第1分钟)
      light-cron: "0 1 * * * ?"
      # 全量更新 Cron 表达式 (每天凌晨3点)
      full-cron: "0 0 3 * * ?"
      # 你要追踪收藏夹的用户 ID (通常是机器人账号自己的 ID)
      target-user-id: "25649510"
  authorization:
    # ⚠️ 关键：CSRF Token，用于执行“收藏/取消收藏”操作
    # 获取方法：
    # 1. 登录 Pixiv 官网
    # 2. 按 F12 打开开发者工具 -> Network 选项卡
    # 3. 点击任意一张图片的“收藏”按钮
    # 4. 在 Network 中找到请求，查看 Request Headers 中的 x-csrf-token 值
    x-csrf-token: "b4b1e14ac72d0b18b3f20001285369e7"
```

## 6. Playwright (渲染引擎)
用于生成图片报表、网页截图。

```yaml
playwright:
  # 页面缩放比例，2.0 为 Retina 屏效果，生成的图片文字更清晰
  device-scale-factor: 2.0
  # 是否无头模式运行 (true=后台运行，不显示浏览器窗口)
  headless: true
```

## 7. Shiro / OneBot 连接鉴权
配置 WebSocket 连接的安全性。

```yaml
shiro:
  ws:
    # 必须与你的 OneBot 实现端 (如 LLOneBot/NapCat) 配置中的 Token 完全一致
    access-token: "suCiTU_{o.JSGeq<"
```
