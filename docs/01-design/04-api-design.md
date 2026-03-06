# Lumen API 设计

本文档定义 Lumen Server（在线模式）对外暴露的 REST API，同时也是离线模式下 shared 模块内部的逻辑接口。

---

## 1. 研究助理 API

### 文章管理

```
GET    /api/articles                    # 获取文章列表（支持筛选、分页）
       ?source={sourceId}              # 按订阅源筛选
       &keyword={keyword}              # 关键词搜索
       &date_from={date}               # 起始日期
       &date_to={date}                 # 截止日期
       &page={n}&size={n}              # 分页

GET    /api/articles/{id}              # 获取单篇文章详情及 AI 分析结果
```

### 订阅源管理

```
GET    /api/sources                     # 获取所有订阅源
POST   /api/sources                     # 添加订阅源
PUT    /api/sources/{id}               # 修改订阅源
DELETE /api/sources/{id}               # 删除订阅源
POST   /api/sources/{id}/refresh       # 手动刷新某个源
```

### 摘要与分析

```
GET    /api/digest/daily               # 获取每日摘要报告
       ?date={date}                    # 指定日期，默认今日

GET    /api/digest/trends              # 获取趋势分析
       ?days={n}                       # 分析天数范围

POST   /api/analyze/manual             # 手动触发一次全量分析
```

---

## 2. 对话 API

### 会话管理

```
GET    /api/conversations              # 获取会话列表
POST   /api/conversations              # 创建新会话
GET    /api/conversations/{id}         # 获取会话详情（含消息历史）
DELETE /api/conversations/{id}         # 删除会话
```

### 消息与流式响应

```
POST   /api/conversations/{id}/messages
       Body: { "content": "..." }
       Response: SSE 流式响应

       # SSE 事件格式:
       # event: message_start
       # data: {"conversation_id": "...", "message_id": "..."}
       #
       # event: content_delta
       # data: {"delta": "部分文本..."}
       #
       # event: tool_call
       # data: {"tool": "search_papers", "args": {...}}
       #
       # event: tool_result
       # data: {"tool": "search_papers", "result": {...}}
       #
       # event: message_end
       # data: {"usage": {"prompt_tokens": n, "completion_tokens": n}}
```

---

## 3. LangBot Webhook

```
POST   /api/langbot/webhook            # 接收 LangBot 推送的消息

       # 请求体 (由 LangBot Bridge Plugin 发送):
       {
         "bot_uuid": "...",
         "event_type": "bot.person_message" | "bot.group_message",
         "sender": {
           "id": "...",
           "nickname": "..."
         },
         "group_id": "...",            # 仅群消息
         "message": "用户发送的文本",
         "timestamp": 1234567890
       }

       # 响应体:
       {
         "reply": "Lumen 的回复文本",
         "skip_pipeline": true
       }
```

---

## 4. 设置 API

```
GET    /api/settings                    # 获取用户设置
PUT    /api/settings                    # 更新用户设置

       # 设置项:
       {
         "llm": {
           "provider": "deepseek",
           "model": "deepseek-chat",
           "api_key": "sk-***",
           "api_base": ""              # 可选，自定义端点
         },
         "research": {
           "auto_collect_interval": 3600,    # 自动采集间隔（秒）
           "digest_time": "08:00",           # 每日摘要生成时间
           "research_interests": ["..."]     # 用户研究兴趣关键词
         },
         "companion": {
           "persona": "default",             # 当前人格模板
           "custom_system_prompt": ""        # 自定义系统 prompt
         },
         "notification": {
           "ntfy_server": "",                # ntfy 服务地址
           "ntfy_topic": ""                  # ntfy 主题
         }
       }
```

---

## 5. 数据同步 API（在线模式）

```
GET    /api/sync/status                 # 获取同步状态
POST   /api/sync/pull                   # 客户端拉取最新数据
POST   /api/sync/push                   # 客户端推送本地变更
```

---

## 6. 存档 API

```
POST   /api/archive/export              # 导出 .lumen 存档文件
       Response: application/octet-stream

POST   /api/archive/import              # 导入 .lumen 存档文件
       Body: multipart/form-data (存档文件)
       Response: { "status": "ok", "imported": {...} }
```

---

## 7. 认证

在线模式下使用简单 Token 认证：

```
Authorization: Bearer <token>
```

- 用户在 Server 首次启动时设置 access token
- 所有 API 请求需携带此 token
- 离线模式无需认证（应用内直接调用）
