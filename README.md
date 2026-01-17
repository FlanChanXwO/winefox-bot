<div align="center">
    <a href="https://github.com/FlanChanXwO/winefox-bot">
    <img src="https://github.com/FlanChanXwO/winefox-bot/blob/main/assets/logo.png" width="310" alt="logo"></a>

## ✨ 酒狐BOT (WineFoxBot) ✨

[![LICENSE](https://img.shields.io/github/license/FlanChanXwO/winefox-bot.svg)](https://github.com/FlanChanXwO/winefox-bot/blob/main/LICENSE)
[![Java Support](https://img.shields.io/badge/Java-25%2B-ed8b00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/downloads/#java25)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6db33f?logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![OneBot](https://img.shields.io/badge/OneBot-v11-black)](https://github.com/botuniverse/onebot-11)
[![Shiro](https://img.shields.io/badge/Shiro-v2.5.2-purple)](https://github.com/MisakaTAT/Shiro)

**基于 Spring Boot 4 + Shiro + JDK 25 打造的现代化 QQ 机器人**
<br/>
集成了 AI 深度对话、图片搜寻以及一些实用功能的二次元综合助理。

[🐛 提交 PR](https://github.com/FlanChanXwO/winefox-bot/pulls) | [🐛 提交 Issue](https://github.com/FlanChanXwO/winefox-bot/issues)

</div>

## 📖 介绍

**酒狐 BOT** 是一个专为 ACG 爱好者群聊设计的全能型机器人。底层拥抱最新技术栈，利用 JDK 25 虚拟线程 (Project Loom) 处理高并发消息，集成 Playwright 实现高质量的图片渲染。

### 🌟 核心功能（不包含外部插件）
*   **更新机制**: 支持通过 GitHub 进行更新 ，管理员可一键升级至最新版本。
*   **智能对话 (AI)**: 接入 OpenAI/DeepSeek 模型，支持 GPT-4/GPT-5-mini，具备上下文记忆与人设扮演能力。  
*   **Pixiv 服务**:
    *   P站搜图、日/周/月榜推送。
    *   收藏夹更新追踪、随机收藏。
    *   智能 Cookie 管理与 CSRF 令牌自动维护。
    *   支持 Pixiv Ugoira 动图下载与转换。
*   **配置管理**：通过群内命令动态修改机器人配置，无需重启。以完成敏感内容发送和一些其它插件所使用的配置信息更新。
*   **群组管理**: 入群欢迎、违禁链接撤回等。
*   **数据报表**: 生成群内发言日报以及每天的早报，还有本地运行环境的状态图片生成。
*   **实用工具**: 磁力链搜索，图片搜索等。
*   **娱乐功能**: 今日运势获取，以及获取瑟图等。

## 💿 安装与部署

### 环境要求
*   **JDK**: OpenJDK 25+
*   **Docker**: 用于快速部署中间件

### 1. 启动基础中间件
本项目依赖 PostgreSQL 和 Redis。你可以通过 `docker-compose` 借助[ docker-compose.yaml ](env/docker-compose.yaml)快速启动中间件。

```bash
cd env
docker-compose up -d
```

### 2. 配置机器人
复制配置文件模板并修改。 

详细配置说明请参考：[📝 配置详解文档](./docs/CONFIG.md)

### 3. 连接 OneBot 实现端
本机器人仅作为后端逻辑处理，你需要一个 OneBot v11 实现端，这里推荐使用 [NapCatQQ](https://github.com/NapNeko/NapCatQQ)
*   **连接方式**: 反向 WebSocket
*   **地址**: `ws://127.0.0.1:5000/ws/shiro` (默认配置)
*   **Access Token**: 需与配置文件中的 `shiro.ws.access-token` 保持一致。

### 4. 启动项目

#### Linux/macOS
```bash
./mvnw clean package
java -jar winefox-bot.jar
# 随后会生成 `control.sh` 脚本用于管理服务
control.sh start
```

#### Windows
```bash
mvnw clean package
java -jar winefox-bot.jar
# 随后会生成 `control.bat` 脚本用于管理服务
control.bat start
```
💡 Tip: 你也可以通过 `Release` 页面下载预编译的 JAR 包 和 `lib.zip` 来避开`mvn`构建运行项目。

## ⚙️ 配置文件概览

*   `application.yml`: 通用配置（端口、日志等级），一般不用修改
*   `application-secret.yml`: **[敏感]** 包含密钥、Token、数据库密码，以及其它强环境配置

## 🎨 帮助文档展示（不包含外部插件）

<div align="center">
    <img src="https://github.com/FlanChanXwO/winefox-bot/blob/main/assets/help_image.png" width="300" alt="帮助菜单">
    <br>
    <i>酒狐本体可用命令帮助菜单</i>
</div>


## 📝 TODO 列表
- [ ] 提供一个 `docker` 镜像，方便用户一键部署。 
- [ ] 补充更多实用的功能（如 `Minecraft` 服务器方面的管理)，以及词云统计功能的转正移植。
- [ ] 增加娱乐功能模块，如小游戏、抽卡等。
- [ ] 优化 AI 对话模块，提升上下文理解与响应速度。
- [ ] 修复潜在的BUG，重构一些不好的代码。  
- [ ] 完善使用文档与注释，补全常见问题与故障排查步骤。  
- [ ] 整理出一个发布版本与维护变更日志（CHANGELOG）。
- [ ] 开发可视化的 WEBUI 以管理BOT的插件和一些配置选项，查看日志等信息。

## 🤝 参与贡献
欢迎提交 [Pull Request](https://github.com/FlanChanXwO/winefox-bot/pulls) 或 [Issue](https://github.com/FlanChanXwO/winefox-bot/issues)！

## 📄 开源协议
[GNU Affero General Public License v3.0](https://github.com/FlanChanXwO/winefox-bot/blob/main/LICENSE)

## 🙏 致谢
感谢以下开源项目和社区的支持：
*   [Shiro](https://github.com/MisakaTAT/Shiro)
*   [NapCatQQ](https://github.com/NapNeko/NapCatQQ)
*   [酒狐模型](https://github.com/TartaricAcid/WineFoxModel)
