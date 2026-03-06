# Lumen 技术栈

## 1. 语言与平台

| 项目 | 选型 | 说明 |
|------|------|------|
| 编程语言 | **Kotlin** | Kotlin Multiplatform，一套代码覆盖 Android + Desktop + Server |
| 构建系统 | Gradle (KTS) | Kotlin 项目标准构建工具 |
| 最低 Android 版本 | API 26 (Android 8.0) | 覆盖 95%+ 设备 |
| JVM 目标 | Java 17+ | Desktop 和 Server 运行环境 |

---

## 2. 核心框架

### 2.1 AI Agent：Koog (JetBrains)

- **项目**：https://github.com/JetBrains/koog
- **作用**：AI Agent 框架，提供 LLM 调用、工具调用、MCP 协议支持
- **选择理由**：
  - JetBrains 官方维护，Kotlin Multiplatform 原生支持
  - 内置多 LLM Provider 路由（OpenAI、Anthropic、Google、DeepSeek 等）
  - 内置 Tool Calling、MCP 协议支持
  - 内置历史上下文压缩
  - 内置记忆管理能力
  - 可运行在 Android、JVM、iOS、浏览器
- **替代的方案**：Pydantic-AI (Python only)、LangChain (过重)

### 2.2 数据库 + 向量搜索：ObjectBox

- **项目**：https://github.com/objectbox/objectbox-java
- **作用**：嵌入式数据库 + 向量搜索引擎（HNSW 算法）
- **选择理由**：
  - 专为 Android/JVM 设计，轻量嵌入式
  - 同时支持结构化数据存储和向量搜索，一个库解决两个问题
  - 内置 HNSW 索引，支持 on-device 语义搜索
  - 数据完全本地，隐私友好
  - Kotlin 原生支持
- **替代的方案**：SQLite + JVector (需要两个库)、Room + 自建向量索引

### 2.3 UI 框架：Compose Multiplatform

- **项目**：https://www.jetbrains.com/compose-multiplatform/
- **作用**：跨平台 UI 框架
- **选择理由**：
  - JetBrains 官方，与 Kotlin 生态无缝集成
  - Android 原生 Jetpack Compose + Desktop + iOS (beta)
  - 声明式 UI，现代开发体验
- **平台覆盖**：Android (稳定)、Desktop (稳定)、iOS (Beta)

### 2.4 后端框架（在线模式）：Ktor

- **项目**：https://ktor.io/
- **作用**：在线模式下的 REST API + WebSocket 服务端
- **选择理由**：
  - JetBrains 官方，Kotlin 原生异步框架
  - 轻量，适合嵌入式部署
  - 与 Koog 原生集成
  - 支持 WebSocket、SSE、HTTP/2

---

## 3. 功能组件

| 功能 | 库/方案 | 说明 |
|------|---------|------|
| RSS 解析 | [RSSParser](https://github.com/nicrico/RSSParser) (Kotlin) | Kotlin/Android RSS 解析库 |
| arxiv 采集 | Ktor HttpClient + arxiv API | arxiv 提供 REST API，直接 HTTP 调用 |
| 定时任务 (Android) | WorkManager | Android 后台任务标准方案 |
| 定时任务 (Desktop/Server) | kotlinx-coroutines 定时调度 | 协程 + delay 循环 |
| 推送通知 | [ntfy](https://ntfy.sh/) | HTTP POST 即可发送，无需 SDK |
| 消息平台桥接 | LangBot Bridge Plugin | 极薄 LangBot 插件，纯消息转发 |
| JSON 序列化 | kotlinx-serialization | Kotlin 官方序列化框架 |
| HTTP 客户端 | Ktor HttpClient | KMP 兼容的 HTTP 客户端 |
| 依赖注入 | Koin | 轻量 KMP 兼容 DI 框架 |
| 日志 | Napier 或 kotlin-logging | KMP 兼容日志库 |
| 存档打包 | kotlinx-io + ZIP | 存档导出/导入 |

---

## 4. 记忆层：SimpleMem 移植

### 4.1 原方案

[SimpleMem](https://github.com/aiming-lab/SimpleMem) (Python) 是一个高效的 LLM Agent 长期记忆系统，使用 LanceDB 作为向量存储。

### 4.2 移植策略

SimpleMem 的三阶段管道中，大部分逻辑是语言无关的（LLM API 调用 + 数据处理），仅向量存储层需要替换：

| SimpleMem 组件 | 移植方案 | 工作量 |
|----------------|---------|--------|
| 语义结构化压缩 | 用 Koog 调 LLM API（prompt 复用） | 低 — prompt 工程 |
| 在线语义合成 | 移植数据处理逻辑 (~300 行) | 中 — 核心移植工作 |
| 意图感知检索规划 | 用 Koog 调 LLM API（prompt 复用） | 低 — prompt 工程 |
| LanceDB 向量存储 | 替换为 ObjectBox 向量搜索 | 低 — API 层替换 |
| Embedding 模型 | 调用 API (OpenAI embedding endpoint) | 低 — HTTP 调用 |
| 数据模型 (MemoryEntry) | 移植为 Kotlin data class + ObjectBox Entity | 低 |

**移植总工作量**：~500 行 Python → Kotlin，主要是数据处理逻辑。远低于从零实现。

---

## 5. LLM 调用策略

### 5.1 用户配置

用户在设置界面配置：

```
Provider:  [下拉选择: OpenAI / Anthropic / DeepSeek / 自定义]
Model:     [模型名称]
API Key:   [用户自己的 key]
API Base:  [可选，自定义端点 / One-API 地址]
```

### 5.2 调用链路

```
Lumen 业务逻辑
    ↓
Koog Agent (Tool Calling / 对话 / 分析)
    ↓
Koog LLM Provider (内置多 Provider 路由)
    ↓
用户配置的 LLM API (OpenAI / Anthropic / DeepSeek / One-API / ...)
```

- Koog 内置多 Provider 支持，无需额外封装
- 用户如已有 One-API 网关，填入其地址作为 API Base 即可
- 所有 LLM 调用走统一路径，便于 token 统计和成本控制

---

## 6. 技术栈总结

```
JetBrains Kotlin 生态全家桶：
  Kotlin Multiplatform ─── 跨平台代码共享
  Koog ────────────────── AI Agent + LLM + Tool Calling
  Compose Multiplatform ── 跨平台 UI
  Ktor ────────────────── 服务端 (在线模式)
  kotlinx-serialization ── 序列化
  kotlinx-coroutines ───── 异步/并发

数据层：
  ObjectBox ───── 嵌入式数据库 + 向量搜索

记忆层：
  SimpleMem (移植) ── 语义压缩 + 检索
  ObjectBox HNSW ──── 向量存储 (替代 LanceDB)

外部集成：
  LangBot Bridge Plugin ── 消息平台桥接 (仅在线模式)
  ntfy ─────────────────── 推送通知 (仅在线模式)
  One-API ─────────────── 可选 LLM 网关
```
