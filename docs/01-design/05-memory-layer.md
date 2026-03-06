# Lumen 记忆层设计

## 1. 概述

Lumen 的记忆层基于 [SimpleMem](https://github.com/aiming-lab/SimpleMem) 移植，使用 [ObjectBox](https://objectbox.io/) 替换 LanceDB 作为向量存储后端。

记忆层为 Lumen 提供长期语义记忆能力，使 AI 能够：
- 记住与用户的每次交互关键内容
- 基于语义（而非关键词）检索相关记忆
- 自动去重和合并冗余信息
- 随时间积累对用户的深入了解

---

## 2. SimpleMem 三阶段管道

### 2.1 语义结构化压缩 (Semantic Structured Compression)

将原始对话转化为原子化、自包含的记忆条目。

```
用户对话（多轮、含指代、含时间相对引用）
        ↓ LLM 处理
原子化记忆条目（独立事实、绝对时间戳、无指代歧义）
```

- 输入：一段对话窗口
- 处理：通过 LLM prompt 提取关键信息，解析指代关系，转换时间引用
- 输出：若干个 `MemoryEntry`，每个都是自包含的独立事实

**移植方式**：prompt 直接复用，用 Koog Agent 发起 LLM 调用。

### 2.2 在线语义合成 (Online Semantic Synthesis)

写入时实时检测并合并语义相关的记忆条目。

```
新 MemoryEntry
    ↓ 向量检索相似条目
找到语义相近的已有条目?
    ├── 是 → LLM 判断是否应合并 → 合并为新条目 / 保留两者
    └── 否 → 直接存入
```

- 防止记忆库中出现大量重复/近似信息
- 保持记忆库紧凑且信息密度高

**移植方式**：核心数据处理逻辑 (~300 行 Python → Kotlin)，LLM 调用部分用 Koog。

### 2.3 意图感知检索规划 (Intent-Aware Retrieval Planning)

查询时推断用户真实搜索意图，动态调整检索策略。

```
用户查询 / Agent 内部查询
    ↓ LLM 推断搜索意图
生成多个检索子查询
    ↓ 并行向量搜索
合并、排序、截断
    ↓
返回最相关记忆上下文
```

- 不是简单地用原始查询做向量搜索
- LLM 先分析查询意图，可能拆解为多个子查询
- 并行检索后合并结果，提供更精确的上下文

**移植方式**：prompt 直接复用，检索调用替换为 ObjectBox 向量搜索 API。

---

## 3. 数据模型

### MemoryEntry (ObjectBox Entity)

```kotlin
@Entity
data class MemoryEntry(
    @Id var id: Long = 0,

    // 内容
    var content: String = "",              // 记忆内容（原子化事实）
    var category: String = "",             // 分类标签
    var source: String = "",               // 来源（对话/研究/系统）

    // 时间
    var createdAt: Long = 0,               // 创建时间戳
    var updatedAt: Long = 0,               // 最后更新时间戳
    var originalTimestamp: String = "",     // 原始时间引用（绝对化后）

    // 向量
    @HnswIndex(dimensions = 1536)          // 维度取决于 embedding 模型
    var embedding: FloatArray = floatArrayOf(),

    // 元数据
    var keywords: String = "",             // 关键词（JSON 数组）
    var importance: Float = 0f,            // 重要性评分
    var accessCount: Int = 0,              // 访问次数
    var lastAccessedAt: Long = 0,          // 最后访问时间
    var mergedFrom: String = ""            // 合并来源 ID 列表（JSON 数组）
)
```

---

## 4. Embedding 策略

### 离线模式

调用用户配置的 LLM Provider 的 embedding endpoint：

```
POST {api_base}/v1/embeddings
{
  "model": "text-embedding-3-small",
  "input": "记忆内容文本"
}
```

- 依赖用户的 API key，与 LLM 调用使用同一 Provider
- 不需要本地模型，保持应用体积小

### 在线模式

同上，由 Server 端统一调用。

---

## 5. 与 Koog Agent 的集成

记忆层作为 Koog Agent 的工具（Tool）注册：

```kotlin
// Agent 可调用的记忆工具
tool("recall_memory") {
    description("从记忆中检索与查询相关的信息")
    parameter("query", "搜索查询")
    parameter("limit", "最大返回条数", default = 5)

    execute { params ->
        memoryManager.recall(params["query"], params["limit"])
    }
}

tool("store_memory") {
    description("将重要信息存入长期记忆")
    parameter("content", "要记住的内容")

    execute { params ->
        memoryManager.store(params["content"], source = "conversation")
    }
}
```

Agent 在对话过程中可自主决定何时检索记忆、何时存储新记忆。

---

## 6. 存档中的记忆数据

导出 `.lumen` 存档时，记忆数据包含：
- 所有 `MemoryEntry` 的结构化数据（含 embedding 向量）
- 导入时直接写入目标设备的 ObjectBox，无需重新计算 embedding

---

## 7. 参考资料

- [SimpleMem 论文](https://arxiv.org/abs/2601.02553)
- [SimpleMem 源码](https://github.com/aiming-lab/SimpleMem)
- [SimpleMem 架构分析](https://deepwiki.com/aiming-lab/SimpleMem)
- [ObjectBox 向量搜索文档](https://docs.objectbox.io/on-device-vector-search)
