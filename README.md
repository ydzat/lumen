# Lumen

> [English](README_EN.md)

**Lumen** 是一款跨平台的个人 AI 智能助手。她以科研辅助为起点，逐步演进为一个长期陪伴、持续成长、真正了解用户的个人 AI 实体。

---

## 特性

- **跨平台** — Android、Desktop (Windows/macOS/Linux)、自部署服务端，一套代码
- **离线优先** — 离线模式单设备独立运行，无需后端服务
- **在线可选** — 自部署 Lumen Server 实现多设备同步与消息平台桥接
- **用户数据自主** — API key 由用户自行提供，数据完全由用户掌控
- **长期记忆** — 基于 SimpleMem 的语义记忆系统，让 AI 真正记住你
- **可定制人格** — 可设定角色人格与语言风格
- **开源** — AGPL-3.0 许可证，源码可审计，社区共建

---

## 演进路线

| 阶段 | 说明 |
|---|---|
| 1. 研究助理 | RSS/arxiv 采集、AI 摘要与趋势分析、每日 Digest |
| 2. 感情助手 | 多轮对话、角色人格、记忆层上下文感知、工具调用 |
| 3. 长期记忆 | 情景记忆、知识图谱、偏好学习、主动关联 |
| 4. 自主 Agent | 主动推荐、独立任务执行、工作流自动化 |
| 5. 多模态 | 语音交互、图像理解、生活感知 |

---

## 技术栈

| 层级 | 选型 |
|---|---|
| 语言 | Kotlin Multiplatform |
| AI Agent | [Koog](https://github.com/JetBrains/koog) (JetBrains) |
| 数据库 + 向量搜索 | [ObjectBox](https://github.com/objectbox/objectbox-java) (HNSW) |
| UI | [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) |
| 服务端 | [Ktor](https://ktor.io/) |
| 记忆层 | [SimpleMem](https://github.com/aiming-lab/SimpleMem) 移植 (Python → Kotlin) |
| 消息桥接 | [LangBot](https://github.com/RockChinQ/LangBot) Bridge Plugin |
| 序列化 | kotlinx-serialization |
| 依赖注入 | Koin |

---

## 项目结构

```
lumen/
├── shared/          # KMP 共享模块 — 所有核心逻辑与 UI
├── shared-db/       # ObjectBox 数据库模块 (纯 JVM)
├── android/         # Android 应用
├── desktop/         # Desktop 应用
├── server/          # Ktor 服务端 (在线模式)
├── bridge-plugin/   # LangBot 桥接插件 (Python)
└── docs/            # 设计文档
```

---

## 运行模式

### 离线模式

安装即用，输入 API key 即可运行。数据存储在本地，设备间通过 `.lumen` 存档文件手动迁移。

### 在线模式

自部署 Lumen Server，多设备连接同一后端，数据实时同步。支持 LangBot 消息桥接 (QQ/Telegram 等) 和 ntfy 推送。

---

## 支持的 LLM

Lumen 通过用户自带 API key 调用大语言模型，支持以下 Provider：

- **DeepSeek** — `https://api.deepseek.com`
- **OpenAI** — `https://api.openai.com`
- **Anthropic** — `https://api.anthropic.com`
- **自定义** — 任何 OpenAI 兼容的端点 (如 One-API)

---

## Docker 部署

```bash
# 快速启动
docker compose up -d

# 查看日志
docker compose logs -f
```

通过环境变量配置：

```bash
LUMEN_ACCESS_TOKEN=your-token \
LUMEN_LLM_API_KEY=sk-xxx \
LUMEN_NTFY_URL=https://ntfy.sh \
LUMEN_NTFY_TOPIC=lumen \
docker compose up -d
```

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `LUMEN_ACCESS_TOKEN` | API 访问令牌 | 自动生成 UUID |
| `LUMEN_LLM_API_KEY` | LLM API 密钥 | 空 |
| `LUMEN_NTFY_URL` | ntfy 服务器地址 | 空 (禁用) |
| `LUMEN_NTFY_TOPIC` | ntfy 推送主题 | 空 (禁用) |

数据通过 Docker 命名卷 `lumen-data` 持久化，包括数据库、配置文件和模型文件。

---

## 许可证

[AGPL-3.0](LICENSE)

Copyright (c) 2025 ydzat
