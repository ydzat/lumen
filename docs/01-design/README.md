# Lumen 设计文档

本目录包含 Lumen 的正式设计文档，基于 `docs/00-draft/` 中的初始草稿演进而来。

## 文档索引

| 文档 | 内容 |
|------|------|
| [01-architecture.md](01-architecture.md) | 系统架构设计：项目定位、运行模式、演进路线、分层架构、外部集成 |
| [02-tech-stack.md](02-tech-stack.md) | 技术栈选型：Kotlin Multiplatform、Koog、ObjectBox、Compose、Ktor |
| [03-project-structure.md](03-project-structure.md) | 项目结构：KMP 模块划分、目录组织、模块依赖关系 |
| [04-api-design.md](04-api-design.md) | API 设计：研究助理、对话、LangBot Webhook、设置、存档同步 |
| [05-memory-layer.md](05-memory-layer.md) | 记忆层设计：SimpleMem 移植方案、三阶段管道、数据模型 |
| [06-langbot-integration.md](06-langbot-integration.md) | LangBot 集成：Bridge Plugin 设计、消息转发协议 |

## 与初始草稿的主要变化

| 维度 | 草稿 (00-draft) | 正式设计 (01-design) |
|------|-----------------|---------------------|
| 语言 | Python | **Kotlin Multiplatform** |
| 平台 | 服务端 + PWA | **Android APK + Desktop + 可选服务端** |
| 运行模式 | 仅自部署 | **离线/在线双模式** |
| Agent 框架 | Pydantic-AI | **Koog (JetBrains)** |
| 数据库 | SQLite + SQLModel | **ObjectBox (含向量搜索)** |
| LLM 调用 | litellm / One-API | **Koog 内置多 Provider** |
| 记忆层 | SimpleMemory → i5-Copilot | **SimpleMem 移植 + ObjectBox** |
| 前端 | Vue 3 + Vuestic Admin | **Compose Multiplatform** |
| 后端框架 | FastAPI | **Ktor (仅在线模式)** |
| 与 LangBot | Webhook 接收 | **Bridge Plugin + Webhook** |
