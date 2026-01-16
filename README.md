<div align="center">
    <a href="https://github.com/FlanChanXwO/winefox-bot">
    <!-- 请替换为你实际的Logo图片链接 -->
    <img src="https://via.placeholder.com/310x310.png?text=WineFox+Bot" width="310" alt="logo"></a>

## ✨ 酒狐BOT (WineFox) ✨

[![LICENSE](https://img.shields.io/github/license/FlanChanXwO/winefox-bot.svg)](./LICENSE)
[![Java Support](https://img.shields.io/badge/Java-25%2B-ed8b00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/downloads/#java25)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6db33f?logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![OneBot](https://img.shields.io/badge/OneBot-v11-black)](https://github.com/botuniverse/onebot-11)

**基于 Spring Boot 4 + Shiro + JDK 25 打造的现代化 QQ 机器人**
<br/>
集成了 AI 深度对话、Pixiv 插画搜寻、Bilibili 动态解析与群管功能的二次元综合助理。

[📖 配置文档](./docs/CONFIG.md) | [🚀 更新日志](./CHANGELOG.md) | [🐛 提交 Issue](https://github.com/FlanChanXwO/winefox-bot/issues)

</div>

## 📖 介绍

**酒狐BOT** 是一个专为 ACG 爱好者群聊设计的全能型机器人。底层拥抱最新技术栈，利用 JDK 25 虚拟线程 (Project Loom) 处理高并发消息，集成 Playwright 实现高质量的图片渲染。

### 🌟 核心功能

*   **🧠 智能对话 (AI)**: 接入 OpenAI/DeepSeek 模型，支持 GPT-4/GPT-5-mini，具备上下文记忆与人设扮演能力。  
*   **🎨 Pixiv 助手**:
    *   P站搜图、日榜/周榜推送。
    *   画师更新追踪、随机色图（支持自动撤回）。
    *   智能 Cookie 管理与 CSRF 令牌自动维护。
*   **📺 Bilibili 解析**:
    *   视频/动态链接自动解析为卡片。
    *   小程序转卡片、UP主动态监控。
*   **🛡️ 群组管理**:
    *   入群欢迎、违禁词撤回。
    *   一键禁言/踢人、全员禁言控制。
*   **📊 数据报表**: 生成群内日报、词云分析（基于 Playwright 渲染）。
*   **🛠️ 实用工具**: 天气查询、多国语言翻译、网页截图。

## 💿 安装与部署

### 环境要求
*   **JDK**: OpenJDK 21+ (必需，使用虚拟线程)
*   **Node.js**: v22+ (用于 Playwright 渲染服务)
*   **Python**: v3.14 (部分数据处理脚本)
*   **Docker**: 用于快速部署中间件

### 1. 启动基础中间件
本项目依赖 PostgreSQL 和 Redis。为了防止端口冲突，我们使用了自定义端口。

```bash
cd env
docker-compose up -d
```

### 2. 配置机器人
复制配置文件模板并修改。
**⚠️ 警告：** `application-secret.yaml` 包含极度敏感信息（Token/密码），**绝对禁止**提交到 Git 仓库！

详细配置说明请参考：[📝 配置详解文档](./docs/CONFIG.md)

### 3. 连接 OneBot 实现端
本机器人仅作为后端逻辑处理，你需要一个 OneBot v11 实现端（如 LLOneBot, NapCat, Lagrange）。
*   **连接方式**: 反向 WebSocket
*   **地址**: `ws://127.0.0.1:8080/ws/shiro` (默认配置)
*   **Access Token**: 需与配置文件中的 `shiro.ws.access-token` 保持一致。

### 4. 启动项目

#### Linux/macOS
```bash
./mvnw clean package
java -jar target/winefox-bot.jar
```

#### Windows
```bash
mvnw clean package
java -jar target/winefox-bot.jar
```

## ⚙️ 配置文件概览

*   `application.yml`: 通用配置（端口、日志等级）
*   `application-secret.yml`: **[敏感]** 包含密钥、Token、数据库密码
*   `application-prod.yml`: 生产环境特定配置

## 🎨 效果展示

<div align="center">
    <img src="https://via.placeholder.com/300x600?text=Help+Menu+Screenshot" width="300" alt="帮助菜单">
    <br>
    <i>命令帮助菜单</i>
</div>

## 🤝 参与贡献
欢迎提交 Pull Request 或 Issue！

## 📄 开源协议
MIT License
