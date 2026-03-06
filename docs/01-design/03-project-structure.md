# Lumen 项目结构

## 1. 顶层结构

```
lumen/
├── shared/              # KMP 共享模块 — 所有核心逻辑
├── android/             # Android 应用
├── desktop/             # Desktop 应用 (Windows/macOS/Linux)
├── server/              # 在线模式服务端
├── bridge-plugin/       # LangBot Bridge Plugin
├── docs/                # 设计文档
├── build.gradle.kts     # 根构建脚本
├── settings.gradle.kts  # 模块声明
└── gradle.properties    # Gradle 配置
```

---

## 2. shared 模块（核心）

所有平台共用的业务逻辑，是 Lumen 的核心。

```
shared/
├── src/commonMain/kotlin/com/lumen/
│   │
│   ├── core/
│   │   ├── config/
│   │   │   ├── AppConfig.kt          # 应用全局配置
│   │   │   ├── LlmConfig.kt          # LLM Provider 配置（key, model, base_url）
│   │   │   └── UserPreferences.kt    # 用户偏好设置
│   │   │
│   │   ├── database/
│   │   │   ├── LumenDatabase.kt      # ObjectBox 数据库初始化
│   │   │   └── entities/             # ObjectBox Entity 定义
│   │   │       ├── Article.kt        # 文章/论文
│   │   │       ├── Source.kt         # 订阅源
│   │   │       ├── Digest.kt         # 摘要报告
│   │   │       ├── Conversation.kt   # 对话记录
│   │   │       └── MemoryEntry.kt    # 记忆条目（含向量）
│   │   │
│   │   ├── memory/
│   │   │   ├── MemoryManager.kt      # 记忆层统一入口
│   │   │   ├── SemanticCompressor.kt # SimpleMem: 语义结构化压缩
│   │   │   ├── SemanticSynthesizer.kt# SimpleMem: 在线语义合成
│   │   │   ├── IntentRetriever.kt    # SimpleMem: 意图感知检索
│   │   │   └── EmbeddingClient.kt    # Embedding API 调用
│   │   │
│   │   └── sync/
│   │       ├── ArchiveExporter.kt    # 导出 .lumen 存档
│   │       └── ArchiveImporter.kt    # 导入 .lumen 存档
│   │
│   ├── research/
│   │   ├── collector/
│   │   │   ├── RssCollector.kt       # RSS 源采集
│   │   │   ├── ArxivCollector.kt     # arxiv 论文采集
│   │   │   └── CollectorManager.kt   # 采集任务调度
│   │   │
│   │   ├── analyzer/
│   │   │   ├── ArticleAnalyzer.kt    # 单篇文章 AI 分析
│   │   │   ├── TrendAnalyzer.kt      # 趋势分析
│   │   │   └── RelevanceAnalyzer.kt  # 与用户方向的关联分析
│   │   │
│   │   └── digest/
│   │       ├── DigestGenerator.kt    # 每日摘要生成
│   │       └── DigestFormatter.kt    # 摘要格式化输出
│   │
│   ├── companion/
│   │   ├── agent/
│   │   │   ├── LumenAgent.kt         # Koog Agent 定义（核心对话引擎）
│   │   │   └── tools/                # Agent 可调用的工具
│   │   │       ├── SearchPapers.kt   # 搜索论文库
│   │   │       ├── QueryMemory.kt    # 查询记忆
│   │   │       ├── WebSearch.kt      # 网页搜索
│   │   │       └── AnalyzeArticle.kt # 分析文章
│   │   │
│   │   └── persona/
│   │       ├── PersonaManager.kt     # 人格管理
│   │       └── PersonaTemplate.kt    # 人格模板定义
│   │
│   └── api/
│       ├── LumenApi.kt               # API 接口定义（供在线模式客户端调用）
│       ├── ResearchApi.kt            # 研究助理相关接口
│       ├── ChatApi.kt                # 对话相关接口
│       └── SettingsApi.kt            # 设置相关接口
│
├── src/commonTest/                    # 共享测试
└── build.gradle.kts
```

---

## 3. android 模块

```
android/
├── src/main/
│   ├── kotlin/com/lumen/android/
│   │   ├── LumenApplication.kt       # Application 入口
│   │   ├── MainActivity.kt           # 主 Activity
│   │   ├── ui/
│   │   │   ├── screens/
│   │   │   │   ├── HomeScreen.kt     # 首页（今日摘要）
│   │   │   │   ├── ArticlesScreen.kt # 文章列表
│   │   │   │   ├── ChatScreen.kt     # 对话界面
│   │   │   │   ├── SourcesScreen.kt  # 订阅源管理
│   │   │   │   └── SettingsScreen.kt # 设置（API key 配置等）
│   │   │   ├── components/           # 可复用 UI 组件
│   │   │   ├── navigation/           # 导航配置
│   │   │   └── theme/                # 主题/样式
│   │   │
│   │   └── service/
│   │       ├── ResearchWorker.kt     # WorkManager: 定时采集任务
│   │       └── NotificationHelper.kt # 本地通知
│   │
│   ├── res/                           # Android 资源文件
│   └── AndroidManifest.xml
│
└── build.gradle.kts
```

---

## 4. desktop 模块

```
desktop/
├── src/main/kotlin/com/lumen/desktop/
│   ├── Main.kt                        # Desktop 入口
│   ├── ui/
│   │   └── (复用 shared UI 组件 + Desktop 适配)
│   ├── tray/
│   │   └── SystemTray.kt             # 系统托盘支持
│   └── scheduler/
│       └── DesktopScheduler.kt        # 协程定时任务
│
└── build.gradle.kts
```

---

## 5. server 模块（在线模式）

```
server/
├── src/main/kotlin/com/lumen/server/
│   ├── Application.kt                # Ktor 服务入口
│   ├── routes/
│   │   ├── ResearchRoutes.kt         # 研究助理 API 路由
│   │   ├── ChatRoutes.kt             # 对话 API 路由（SSE 流式）
│   │   ├── LangBotWebhook.kt         # LangBot webhook 接收端点
│   │   ├── SettingsRoutes.kt         # 设置 API 路由
│   │   └── SyncRoutes.kt             # 数据同步 API
│   │
│   └── plugins/
│       ├── Authentication.kt         # 认证（简单 token）
│       ├── Serialization.kt          # JSON 序列化配置
│       └── WebSockets.kt             # WebSocket 配置
│
├── Dockerfile                         # 容器化部署
├── docker-compose.yml                 # 包含 ntfy、可选 LangBot
└── build.gradle.kts
```

---

## 6. bridge-plugin 模块（LangBot 插件）

```
bridge-plugin/
├── manifest.yaml                      # LangBot 插件清单
├── main.py                            # 插件入口
├── components/
│   └── event_listener/
│       └── bridge.py                  # 消息拦截 → 转发至 Lumen Server
└── README.md
```

---

## 7. 模块依赖关系

```
shared (核心，无平台依赖)
  ↑
  ├── android  (依赖 shared)
  ├── desktop  (依赖 shared)
  └── server   (依赖 shared)

bridge-plugin (独立，Python，通过 HTTP 与 server 通信)
```

核心原则：**所有业务逻辑在 shared 中**，平台模块只做：
- UI 适配（Compose 平台差异）
- 系统能力适配（通知、后台任务、文件系统路径等）
- 平台入口（Activity / main 函数 / Ktor Application）
