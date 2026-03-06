# Lumen 与 LangBot 集成设计

## 1. 集成模式

Lumen 与 LangBot 的集成采用 **Bridge Plugin + Webhook** 模式。Lumen 不依赖 LangBot，LangBot 仅作为消息平台的桥梁。

```
用户 (QQ / Telegram / 微信 / ...)
        ↓ 发送消息
LangBot
  └── Lumen Bridge Plugin (EventListener)
        ├── 拦截消息
        ├── HTTP POST → Lumen Server /api/langbot/webhook
        ├── 等待 Lumen 响应
        └── 将响应回复到消息平台
        (skip_pipeline = true, 不走 LangBot 的 LLM)

Lumen Server
  └── 接收消息 → Koog Agent 处理（人格 + 记忆 + 工具）→ 返回回复
```

---

## 2. Bridge Plugin 设计

### 2.1 插件结构

```
lumen-bridge/
├── manifest.yaml
├── main.py
└── components/
    └── event_listener/
        └── bridge.py
```

### 2.2 manifest.yaml

```yaml
apiVersion: v1
kind: Plugin
metadata:
  author: "lumen"
  name: "lumen-bridge"
  version: "0.1.0"
  description: "Lumen AI 助手消息桥接插件"
  label: "Lumen Bridge"
spec:
  config:
    - name: lumen_server_url
      label: "Lumen Server 地址"
      type: string
      default: "http://localhost:8000"
    - name: lumen_token
      label: "Lumen Access Token"
      type: string
      default: ""
    - name: enable_group
      label: "启用群消息转发"
      type: bool
      default: false
    - name: group_trigger
      label: "群消息触发关键词（为空则@触发）"
      type: string
      default: ""
  components:
    EventListener:
      - fromDirs: components/event_listener/
execution:
  python:
    path: main.py
    attr: LumenBridge
```

### 2.3 核心逻辑 (bridge.py)

```python
# 伪代码，展示核心逻辑

class BridgeEventListener(BaseEventListener):

    @handler(PersonMessageReceived)
    async def on_person_message(self, ctx):
        """私聊消息：全部转发至 Lumen"""
        reply = await self._forward_to_lumen(ctx.event)
        if reply:
            await ctx.reply(reply)
            ctx.skip_pipeline = True

    @handler(GroupMessageReceived)
    async def on_group_message(self, ctx):
        """群消息：仅当@机器人或包含触发词时转发"""
        if not self._should_handle_group(ctx.event):
            return
        reply = await self._forward_to_lumen(ctx.event)
        if reply:
            await ctx.reply(reply)
            ctx.skip_pipeline = True

    async def _forward_to_lumen(self, event):
        """转发消息到 Lumen Server"""
        payload = {
            "bot_uuid": event.bot_uuid,
            "event_type": event.type,
            "sender": {"id": event.sender_id, "nickname": event.nickname},
            "group_id": getattr(event, "group_id", None),
            "message": event.text_content,
            "timestamp": event.timestamp
        }
        response = await http_post(
            f"{config.lumen_server_url}/api/langbot/webhook",
            json=payload,
            headers={"Authorization": f"Bearer {config.lumen_token}"},
            timeout=30
        )
        return response.get("reply")
```

---

## 3. 设计要点

### 3.1 Bridge Plugin 职责边界

Bridge Plugin **只做**：
- 拦截消息
- 格式化为 JSON
- 转发至 Lumen Server
- 接收响应并回复

Bridge Plugin **不做**：
- 不调用 LangBot 的 LLM
- 不维护对话状态
- 不做任何 AI 处理
- 不存储数据

### 3.2 为什么不用 LangBot 的 LLM

Lumen 的对话引擎需要：
- 注入人格 system prompt
- 注入从记忆层检索的上下文
- 调用 Lumen 专有工具（查论文库、分析文章等）
- 维护跨平台的统一对话历史

这些都无法通过 LangBot 的 pipeline 实现。LangBot 的 LLM 继续服务其他插件（如 daily_magazine），与 Lumen 互不干扰。

### 3.3 LangBot Webhook 系统 vs Bridge Plugin

LangBot 自带 Webhook 推送功能，为什么还要写 Bridge Plugin？

| 方式 | 优劣 |
|------|------|
| LangBot 自带 Webhook | 只能推送消息，无法控制回复内容和回复时机 |
| **Bridge Plugin** | 可以拦截消息、自主回复、跳过 pipeline，完全控制交互流程 |

Bridge Plugin 是必要的，因为 Lumen 需要完全接管对话流程。

---

## 4. 部署配置

在 LangBot 管理面板中安装 lumen-bridge 插件后，配置：

- `lumen_server_url`：Lumen Server 的访问地址
- `lumen_token`：与 Lumen Server 设置中一致的 access token
- `enable_group`：是否响应群消息
- `group_trigger`：群消息触发方式（@机器人 / 关键词前缀）
