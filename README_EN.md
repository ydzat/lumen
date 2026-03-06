# Lumen

> [中文](README.md)

**Lumen** is a cross-platform personal AI assistant. Starting as a research aide, it evolves into a long-term companion that grows with you and truly understands you.

---

## Features

- **Cross-platform** — One codebase for Android, Desktop (Windows/macOS/Linux), and self-hosted server
- **Offline-first** — Runs fully standalone on a single device, no backend required
- **Online optional** — Self-host Lumen Server for multi-device sync and messaging bridge
- **User-owned data** — Bring your own API key; all data stays under your control
- **Long-term memory** — Semantic memory system based on SimpleMem, so your AI truly remembers you
- **Customizable persona** — Define the personality and language style of your companion
- **Open source** — AGPL-3.0 licensed, auditable source code, community-driven

---

## Roadmap

| Phase | Description |
|---|---|
| 1. Research Aide | RSS/arxiv collection, AI summarization & trend analysis, daily digest |
| 2. Emotional Companion | Multi-turn chat, persona, memory-aware context, tool calling |
| 3. Long-term Memory | Episodic memory, knowledge graph, preference learning |
| 4. Autonomous Agent | Proactive recommendations, autonomous task execution, workflow automation |
| 5. Multimodal | Voice interaction, image understanding, life-aware sensing |

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin Multiplatform |
| AI Agent | [Koog](https://github.com/JetBrains/koog) (JetBrains) |
| DB + Vector Search | [ObjectBox](https://github.com/objectbox/objectbox-java) (HNSW) |
| UI | [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) |
| Server | [Ktor](https://ktor.io/) |
| Memory | [SimpleMem](https://github.com/aiming-lab/SimpleMem) port (Python → Kotlin) |
| Messaging Bridge | [LangBot](https://github.com/RockChinQ/LangBot) Bridge Plugin |
| Serialization | kotlinx-serialization |
| DI | Koin |

---

## Project Structure

```
lumen/
├── shared/          # KMP shared module — all core logic & UI
├── shared-db/       # ObjectBox database module (pure JVM)
├── android/         # Android app
├── desktop/         # Desktop app
├── server/          # Ktor server (online mode)
├── bridge-plugin/   # LangBot bridge plugin (Python)
└── docs/            # Design documents
```

---

## Modes

### Offline Mode

Install and run. Enter your API key and go. Data is stored locally; migrate between devices via `.lumen` archive files.

### Online Mode

Self-host Lumen Server for multi-device real-time sync. Supports LangBot messaging bridge (QQ/Telegram, etc.) and ntfy push notifications.

---

## Supported LLM Providers

Lumen calls LLMs via your own API key. Supported providers:

- **DeepSeek** — `https://api.deepseek.com`
- **OpenAI** — `https://api.openai.com`
- **Anthropic** — `https://api.anthropic.com`
- **Custom** — Any OpenAI-compatible endpoint (e.g. One-API)

---

## License

[AGPL-3.0](LICENSE)

Copyright (c) 2025 ydzat
