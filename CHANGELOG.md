# Changelog

All notable changes to this project will be documented in this file.

## [0.1.0] - 2026-03-09

### Bug Fixes

- **ci**: Grant write permission for CHANGELOG commit in release workflow by @ydzat
- **android**: Resolve XML parser and regex incompatibilities by @ydzat
- **memory**: Add BertFullTokenizer fallback for Android compatibility by @ydzat
- **android**: Upgrade AGP to 8.10.1 and add ProGuard/ObjectBox compatibility by @ydzat
- **build**: Move readability4j dependency to commonMain for Android compatibility by @ydzat
- **collector**: Fair per-source budget allocation and pipeline improvements by @ydzat
- **ui**: Align table columns using weight-based layout by @ydzat
- **collector**: Restore tables stripped by Readability4J in content extraction by @ydzat
- **ui**: Use Compose native rendering for all article content by @ydzat
- **ui**: Fix HTML rendering issues in article detail screen by @ydzat
- **analyzer**: Address review feedback by @ydzat
- Ui by @ydzat
- **ui**: Address review feedback for M6 frontend gaps by @ydzat
- **agent**: Fix auto-naming skipped when tools are called by @ydzat
- **agent**: Use explicit toLong() for daysBack multiplication by @ydzat
- **collector**: Address review feedback for retry policy by @ydzat
- **config**: Address review feedback for settings and source display by @ydzat
- **collector**: Add error resilience to processUnembedded and batch persist by @ydzat
- **digest**: Correct article pluralization and improve test robustness by @ydzat
- **archiver**: Prevent integer overflow and cap emergency archive scan by @ydzat
- **spark**: Cap text search scan to prevent loading entire article table by @ydzat
- **collector**: Validate repo format and guard empty repos update by @ydzat
- **research**: Address review feedback for RssDataSource by @ydzat
- **research**: Address review feedback for ScholarDataSource by @ydzat
- **research**: Address review feedback for ArxivApiDataSource by @ydzat
- **research**: Address review feedback — compile Regex patterns once and clean up imports by @ydzat
- **research**: Improve RSS collection error reporting and source seeding by @ydzat
- **agent**: Persist tool call IDs and fix message ordering for DeepSeek by @ydzat
- **ui**: Sort ChatScreen imports alphabetically by @ydzat
- **desktop**: Extract version constant and document DMG version by @ydzat
- **android**: Address review feedback in proguard rules by @ydzat
- **deps**: Resolve ONNX Runtime duplicate class conflict on Android by @ydzat
- **onboarding**: Address review feedback by @ydzat
- **archive**: Add file size limit to import endpoint by @ydzat
- **archive**: Clean up temp directory in createTempDatabase test helper by @ydzat
- **settings**: Address review feedback by @ydzat
- **server**: Fall back to default CORS hosts when all env entries filtered by @ydzat
- **server**: Address review feedback by @ydzat
- **server**: Comment out default env vars in docker-compose by @ydzat
- **server**: Sort imports in ResearchRoutesTest by @ydzat
- **server**: Mask access token in startup log by @ydzat
- **server**: Validate MIME before reading file and explicit size cast by @ydzat
- **server**: Address review feedback for chat API by @ydzat
- **server**: Address review feedback by @ydzat
- **server**: Fix indentation in ApiRoutes.kt by @ydzat
- **server**: Address review feedback by @ydzat
- **ui**: Address review feedback for document upload by @ydzat
- **chat**: Disable persona picker during active loading by @ydzat
- **chat**: Address review feedback for title generation by @ydzat
- **ui**: Use kotlinx.serialization for robust args parsing by @ydzat
- **agent**: Address review feedback for project context by @ydzat
- **ui**: Improve agent lifecycle and typewriter reliability by @ydzat
- **document**: Build query inside transaction in DocumentManager.delete() by @ydzat
- **agent**: Extract shared assembleSystemPrompt to eliminate duplication by @ydzat
- **document**: Address review feedback by @ydzat
- **companion**: Address review feedback by @ydzat
- **companion**: Address review feedback by @ydzat
- **ui**: Address review feedback by @ydzat
- **ui**: Precompute article counts in ProjectsScreen by @ydzat
- **research**: Address M2 bugs and code quality issues by @ydzat
- **agent**: Build system prompt dynamically from registered tools by @ydzat
- **research**: Address review feedback by @ydzat
- **ui**: Address review feedback by @ydzat
- **di**: Sort research module imports alphabetically by @ydzat
- **research**: Use put() return value for article ID in analyze() by @ydzat
- **research**: Add id guard in SourceManager.update() by @ydzat
- **research**: Use thread-safe DateTimeFormatter for RSS date parsing by @ydzat
- **db**: Use imports for generated ObjectBox classes in tests by @ydzat
- **memory**: Address remaining minor review feedback by @ydzat
- **memory**: Close native resources and add Koin onClose handlers by @ydzat
- **agent**: Address review feedback by @ydzat
- **memory**: Address review feedback by @ydzat
- **memory**: Address review feedback for SemanticSynthesizer by @ydzat
- **memory**: Eliminate double DB write and sort imports by @ydzat
- **memory**: Close ObjectBox Query and validate recall limit by @ydzat
- **memory**: Address review feedback by @ydzat
- **settings**: Address review feedback by @ydzat
- **ui**: Extract shared TabContent composable by @ydzat
- **di**: Add onClose for LumenDatabase and simplify test Koin access by @ydzat
- **agent**: Validate custom provider apiBase and improve test formatting by @ydzat
- **config**: Extract shared Json config and add Android mkdirs by @ydzat
- **database**: Use idiomatic assertions in LumenDatabaseTest by @ydzat

### CI/CD

- Optimize workflows — remove redundant cache, add Android compile check by @ydzat
- Add CI/CD workflows with git-cliff changelog generation by @ydzat

### Documentation

- Add project logo, screenshots, and rewrite README by @ydzat
- Add bilingual README (Chinese + English) by @ydzat

### Features

- **android**: Add app icon from designed logo.svg by @ydzat
- **ui**: Add i18n support, dark mode fix, and digest UI redesign by @ydzat
- **collector**: Add content enrichment, arXiv HTML support, and HTML rendering by @ydzat
- **collector**: Add RSS content enrichment via web page extraction by @ydzat
- **ui**: Redesign ArticleDetailScreen with section-based content display by @ydzat
- **analyzer**: Add per-section on-demand analysis and fix nullable field by @ydzat
- **analyzer**: Add per-section deep analysis with on-demand translation by @ydzat
- **ui**: Add Spark insights section to home screen by @ydzat
- **ui**: Add pipeline progress indicator with stage display by @ydzat
- **ui**: Add citation counts and source type display to articles by @ydzat
- **ui**: Group consecutive tool calls into compact expandable card by @ydzat
- **ui**: Redesign light/dark theme with VSCode-inspired color palette by @ydzat
- **agent**: Add daysBack filter and enhanced formatting to SearchArticlesTool by @ydzat
- **collector**: Implement exponential backoff retry and source health tracking by @ydzat
- **collector**: Update default sources and add user config settings UI by @ydzat
- **collector**: Implement tiered processing pipeline with analysisStatus state machine by @ydzat
- **digest**: Overhaul digest for multi-project sections and Spark insights by @ydzat
- **archiver**: Add ArticleArchiver for smart article lifecycle management by @ydzat
- **spark**: Add SparkEngine for cross-project knowledge discovery by @ydzat
- **collector**: Add GitHubReleasesDataSource for software release tracking by @ydzat
- **research**: Implement ScholarDataSource for Semantic Scholar API by @ydzat
- **research**: Implement ArxivApiDataSource for arXiv API queries by @ydzat
- **ui**: Add streaming chat responses and markdown rendering by @ydzat
- **companion**: Add built-in persona seeding and fix LLM client factory by @ydzat
- **shared**: Add error handling polish and offline fallback by @ydzat
- **desktop**: Configure native distribution installers by @ydzat
- **android**: Add release build config with signing and R8 by @ydzat
- **onboarding**: Add first-run onboarding wizard by @ydzat
- **archive**: Add archive REST API endpoints and UI buttons by @ydzat
- **archive**: Add ArchiveManager for data export/import by @ydzat
- **settings**: Add theme, language, and memory preference controls by @ydzat
- **server**: Start scheduler, add trends endpoint, configurable CORS by @ydzat
- **server**: Add Docker Compose deployment configuration by @ydzat
- **server**: Add ntfy push notification integration by @ydzat
- **server**: Add document upload REST API with multipart support by @ydzat
- **server**: Add settings REST API with API key masking by @ydzat
- **server**: Add chat REST API with SSE streaming and persona management by @ydzat
- **server**: Add Research REST API endpoints by @ydzat
- **server**: Add token-based authentication middleware by @ydzat
- **server**: Add Ktor server foundation with plugins and DI by @ydzat
- **ui**: Add document upload UI with file picker and progress by @ydzat
- **chat**: Add persona selector accessible from chat conversation by @ydzat
- **chat**: Auto-generate conversation title after first exchange by @ydzat
- **ui**: Enhance tool call card with arguments display by @ydzat
- **agent**: Add project-aware context to agent and tools by @ydzat
- **ui**: Implement Chat UI with conversation management by @ydzat
- **document**: Add DocumentManager and SearchDocumentsTool for RAG by @ydzat
- **memory**: Add automatic memory extraction and recall by @ydzat
- **document**: Add document parsing and chunking pipeline by @ydzat
- **companion**: Add persona system with prompt templates by @ydzat
- **companion**: Add multi-turn conversation engine by @ydzat
- **db**: Add Conversation, Message, Persona, Document, DocumentChunk entities by @ydzat
- **ui**: Add Source, Project, and Digest management screens by @ydzat
- **agent**: Add research tools for Koog Agent by @ydzat
- **research**: Add CollectorManager and PlatformScheduler for scheduled collection by @ydzat
- **ui**: Implement Articles and Home screens with full functionality by @ydzat
- **research**: Add daily digest generation with memory integration by @ydzat
- **research**: Add ArticleAnalyzer and RelevanceScorer for LLM-driven analysis by @ydzat
- **research**: Add ProjectManager for research project management by @ydzat
- **research**: Add SourceManager for RSS source subscription management by @ydzat
- **research**: Add RSS/Atom feed collector with RssParser by @ydzat
- **db**: Add Article, Digest, ResearchProject entities and enhance Source by @ydzat
- **memory**: Replace remote embedding with local ONNX model by @ydzat
- **agent**: Register memory tools for Koog Agent by @ydzat
- **memory**: Add Intent-Aware Retrieval Planning by @ydzat
- **memory**: Add Online Semantic Synthesis (SimpleMem Stage 2) by @ydzat
- **memory**: Add Semantic Structured Compression (SimpleMem Stage 1) by @ydzat
- **memory**: Add MemoryManager with ObjectBox vector search by @ydzat
- **memory**: Add Embedding API client for vector operations by @ydzat
- **settings**: Implement Settings screen with LLM configuration by @ydzat
- **ui**: Add Compose Multiplatform navigation shell by @ydzat
- **di**: Set up Koin dependency injection across all modules by @ydzat
- **agent**: Add basic Koog agent with DeepSeek LLM integration by @ydzat
- **config**: Implement config system with LLM provider settings by @ydzat
- **database**: Integrate ObjectBox with basic entity definitions by @ydzat
- Set up Gradle build system for KMP project by @ydzat

### Miscellaneous

- Add CLAUDE.md to gitignore by @ydzat
- Exclude design docs from version control and add AGPL-3.0 info by @ydzat

### Performance

- **embedding**: Add GPU support, batch processing, and session optimization by @ydzat
- **ui**: Memoize groupToolCalls with derivedStateOf by @ydzat

### Refactoring

- **research**: Replace RssCollector with RssDataSource by @ydzat
- **research**: Add DataSource interface, entity restructuring, and CollectorManager refactor by @ydzat
- **research**: Extract parseCsvSet to shared utility by @ydzat
- **agent**: Use Koog LLMClient abstraction with provider factory by @ydzat

### Testing

- Skip network integration tests in CI environment by @ydzat
- **archive**: Add route tests for export, import, and auth by @ydzat
- **archive**: Add round-trip, dedup, and manifest validation tests by @ydzat

### Init

- Lumen project structure and design documents by @ydzat

