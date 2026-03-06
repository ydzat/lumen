# Lumen 设计文档

## 1. 项目定位

Lumen 是一款自部署的、以个人研究辅助为起点的 AI 智能助手软件。她是一个独立软件，不依赖任何聊天机器人框架（如 langbot），对外提供 REST API，任何第三方（包括 langbot 插件）均可通过 API 接入。

**核心理念**：一个长期陪伴、持续成长、真正了解用户的个人 AI 实体。

---

## 2. 演进路线

### 第一阶段：研究助理（当前阶段）

Lumen 替用户监测学术与技术信息流。

- 聚合 arxiv、技术博客、RSS 新闻源
- 调用 LLM 进行中文摘要、趋势分析、与用户研究方向的关联分析
- 每日推送 digest 到手机（通过 ntfy）
- Web 仪表盘展示分析结果、管理订阅源

**不依赖 i5-Copilot 项目，可立即开始开发。**

### 第二阶段：感情助手 + 对话

Lumen 拥有自己的人格与声音。

- 多轮对话，可设定角色人格
- 能主动讨论她观察到的研究内容
- 具备工具调用能力（搜索网页、查询文章库、与用户讨论分析结果等）
- 情感陪伴功能

### 第三阶段：长期记忆 + 知识图谱

Lumen 真正"认识"用户。

- 情景记忆：记住每次对话的关键内容
- 知识图谱：用户读过的论文、观点、项目进展全部关联
- 偏好学习：了解用户的兴趣、习惯、作息
- 主动关联：跨时间跨主题的信息连接

**此阶段计划接入 i5-Copilot 项目的上下文基础设施，替换临时的 SimpleMemory 实现。**

### 第四阶段：自主 Agent

- 主动推荐与发现
- 独立执行复杂任务
- 跨工具协作（笔记软件、邮件、日程、数据库等）
- 工作流自动化

### 第五阶段：多模态 + 生活融入

- 语音交互
- 图像理解
- 生活感知（日程、健康、天气、位置）
- 情绪感知

---

## 3. 技术架构

### 3.1 整体架构

```
┌────────────────────────────────┐
│       Lumen 后端 (FastAPI)      │
│                                │
│  ┌──────────┐  ┌────────────┐  │
│  │ 研究助理  │  │  感情助手   │  │
│  │ 采集/分析 │◄►│ 对话/角色   │  │
│  └────┬─────┘  └─────┬──────┘  │
│       │              │         │
│  ┌────┴──────────────┴──────┐  │
│  │       共享基础设施         │  │
│  │  - LLM 调用层            │  │
│  │  - 数据库                │  │
│  │  - 用户配置              │  │
│  │  - 工具注册表            │  │
│  │  - 记忆层（接口抽象）     │  │
│  └──────────────────────────┘  │
│                                │
│  REST API + WebSocket          │
└───────────────┬────────────────┘
                │
         ┌──────┼──────┐
         ▼      ▼      ▼
       PWA    ntfy   外部API
      前端    推送   (langbot等)
```

### 3.2 最终形态的分层架构

```
感知层 (Perception)  ：RSS/arxiv、新闻、日程、传感器、语音
记忆层 (Memory)      ：短期记忆、情景记忆、知识图谱
思考层 (Reasoning)   ：LLM推理、工具调用、规划、情感模型
行动层 (Action)      ：对话回复、推送通知、执行任务、调用API
人格层 (Persona)     ：性格设定、语言风格、情感状态、成长性
```

---

## 4. 项目结构

```
lumen/
├── core/
│   ├── llm.py          # 统一的 LLM 调用（支持 tool use）
│   ├── database.py     # 数据库连接和模型
│   ├── config.py       # 配置管理
│   ├── tools.py        # 工具注册（搜索、查文章、计算等）
│   └── memory.py       # 记忆层抽象接口 + SimpleMemory 实现
├── research/           # 研究助理模块
│   ├── collector.py    # RSS / arxiv 采集
│   ├── analyzer.py     # AI 分析管道
│   └── scheduler.py    # 定时任务
├── companion/          # 感情助手模块（第二阶段）
│   ├── chat.py         # 对话管理
│   ├── persona.py      # 角色 / 人格设定
│   └── memory.py       # 对话记忆
├── api/                # FastAPI 路由
│   ├── research.py     # 研究助理 API
│   ├── chat.py         # 聊天 WebSocket（第二阶段）
│   └── settings.py     # 设置 API
└── web/                # PWA 前端
```

---

## 5. 记忆层设计（关键抽象）

为未来接入 i5-Copilot 预留接口：

```python
class MemoryProvider(ABC):
    """记忆层抽象接口 - 未来由 i5-Copilot 实现替换"""

    @abstractmethod
    async def store(self, entry: MemoryEntry) -> None: ...

    @abstractmethod
    async def recall(self, query: str, limit: int) -> list[MemoryEntry]: ...

    @abstractmethod
    async def relate(self, entry_id: str) -> list[MemoryEntry]: ...


# 第一阶段使用
class SimpleMemory(MemoryProvider):
    """SQLite + 关键词匹配，临时方案"""
    ...

# 未来第三阶段替换为
class I5CopilotMemory(MemoryProvider):
    """基于 i5-Copilot 的上下文基础设施"""
    ...
```

---

## 6. 技术栈

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 开发环境 | WSL (Windows 11) | 与服务器部署环境一致 |
| 后端框架 | Python + FastAPI | 轻量、异步、适合 API 服务 |
| 定时任务 | APScheduler | Python 原生调度 |
| 数据库 | SQLite（第一阶段） | 后续可迁移至 PostgreSQL |
| LLM 调用 | anthropic / openai SDK | 通过 core/llm.py 统一封装 |
| RSS 解析 | feedparser | 成熟的 RSS/Atom 解析库 |
| arxiv 抓取 | arxiv Python 库 | 官方 API 封装 |
| 前端模板 | Vuestic Admin (Vue 3) | 内置 PWA 支持，150+ 组件，MIT 协议 |
| 聊天组件 | Deep Chat | 框架无关 Web 组件，内置 AI API 对接，支持流式响应 |
| 推送 | ntfy | 自部署推送服务，Android 客户端可用 |
| 部署 | Docker Compose | 后端 + 前端统一容器化部署 |

---

## 7. 第一阶段开发范围

仅实现以下内容：

- `core/`：LLM 调用层、数据库、配置管理、SimpleMemory
- `research/`：RSS/arxiv 采集、AI 分析管道、定时任务
- `api/research.py`：研究助理相关 API
- `api/settings.py`：订阅源管理 API
- 前端：基于 Vuestic Admin 的仪表盘（文章列表、分析报告、订阅管理）
- ntfy 推送集成

**不实现**：感情助手、聊天界面、长期记忆、知识图谱、多模态。

---

## 8. API 设计（第一阶段）

```
GET    /api/articles              # 获取文章列表（支持筛选、分页）
GET    /api/articles/{id}         # 获取单篇文章详情及 AI 分析
GET    /api/digest/daily          # 获取每日摘要报告
GET    /api/digest/trends         # 获取趋势分析
POST   /api/sources               # 添加订阅源
GET    /api/sources               # 获取订阅源列表
PUT    /api/sources/{id}          # 修改订阅源
DELETE /api/sources/{id}          # 删除订阅源
POST   /api/analyze/manual        # 手动触发一次分析
GET    /api/settings              # 获取用户设置
PUT    /api/settings              # 更新用户设置
```

---

## 9. 与外部系统的关系

| 系统 | 关系 |
|------|------|
| langbot | Lumen 不依赖 langbot。langbot 可作为客户端通过 REST API 获取数据并转发至 QQ |
| i5-Copilot | 第三阶段通过 MemoryProvider 接口接入，替换 SimpleMemory |
| ntfy | Lumen 主动向 ntfy 服务推送通知，手机端安装 ntfy 客户端接收 |

---

## 10. 设计原则

1. **先做工具，不做平台**：不搭建插件系统或模块框架，用正常的代码组织即可
2. **接口抽象仅用于确定会替换的组件**：目前只有 MemoryProvider 需要抽象
3. **不过度设计**：第一阶段不为后续阶段预写任何代码，只保证代码结构干净
4. **自部署优先**：所有数据留在用户自己的服务器上
5. **API-first**：所有功能通过 API 暴露，前端和外部系统都是 API 的消费者
