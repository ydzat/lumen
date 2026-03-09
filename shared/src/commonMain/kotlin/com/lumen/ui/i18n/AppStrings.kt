package com.lumen.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.lumen.core.config.ConfigStore
import org.koin.compose.koinInject

val LocalStrings = staticCompositionLocalOf { EnStrings }

object LanguageState {
    var language by mutableStateOf("en")
}

@Composable
fun ProvideStrings(content: @Composable () -> Unit) {
    val configStore = koinInject<ConfigStore>()
    val initialLang = configStore.load().preferences.language
    if (LanguageState.language != initialLang && LanguageState.language == "en") {
        LanguageState.language = initialLang
    }
    val strings = AppStrings.forLanguage(LanguageState.language)
    CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}

@Composable
fun strings(): AppStrings = LocalStrings.current

data class AppStrings(
    // Navigation
    val home: String,
    val articles: String,
    val chat: String,
    val settings: String,

    // Common
    val save: String,
    val cancel: String,
    val delete: String,
    val close: String,
    val back: String,
    val edit: String,
    val create: String,
    val skip: String,
    val next: String,
    val name: String,
    val description: String,
    val active: String,

    // HomeScreen
    val welcomeToLumen: String,
    val sources: String,
    val todaysDigest: String,
    val refreshDigest: String,
    val viewAll: String,
    val noDigestAvailable: String,
    val digestGeneratedAfterCollection: String,
    val sparkInsights: String,
    val projects: String,
    val tapToExpand: String,
    val tapToCollapse: String,
    val highlights: String,
    val trends: String,
    val autoCategory: String,
    val noSparkInsights: String,
    val sourceDistribution: String,
    val other: String,

    // SettingsScreen
    val llmSettings: String,
    val provider: String,
    val model: String,
    val apiKey: String,
    val apiBaseUrlOptional: String,
    val egDeepseekChat: String,
    val leaveEmptyForDefault: String,
    val hideApiKey: String,
    val showApiKey: String,
    val settingsSaved: String,
    val testConnection: String,
    val connectionSuccessful: String,
    val preferences: String,
    val theme: String,
    val language: String,
    val memoryAutoRecall: String,
    val dataManagement: String,
    val manageSources: String,
    val manageResearchProjects: String,
    val backup: String,
    val exportData: String,
    val importData: String,
    val exportAvailableViaApi: String,
    val importAvailableViaApi: String,

    // OnboardingScreen
    val onboardingIntro: String,
    val step1ConfigureLlm: String,
    val step2RssSources: String,
    val step2Intro: String,
    val step3CommunicationStyle: String,
    val step3Intro: String,
    val complete: String,

    // SourcesScreen
    val addSource: String,
    val editSource: String,
    val deleteSource: String,
    val feedUrl: String,
    val category: String,
    val categoryPlaceholder: String,
    val descriptionOptional: String,

    // ArticlesScreen
    val refreshFeeds: String,
    val allProjects: String,
    val project: String,
    val allSources: String,
    val source: String,
    val status: String,
    val noArticlesYet: String,
    val tapRefreshToFetch: String,
    val articleNotAnalyzed: String,
    val analyzePrompt: String,
    val analysisComplete: String,
    val analyzing: String,
    val analyze: String,
    val viewWithoutAnalysis: String,
    val viewDetails: String,
    val archive: String,
    val restore: String,
    val aiSummary: String,
    val keywords: String,
    val analyzed: String,
    val archived: String,
    val starred: String,
    val unanalyzed: String,

    // Pipeline stages
    val fetchingArticles: String,
    val removingDuplicates: String,
    val enrichingContent: String,
    val embeddingArticles: String,
    val scoringRelevance: String,
    val analyzingArticles: String,
    val generatingSparks: String,
    val generatingDigest: String,

    // ArticleDetailScreen
    val tableOfContents: String,
    val star: String,
    val unstar: String,
    val abstract_: String,
    val translation: String,
    val articleContent: String,
    val sourceText: String,
    val aiCommentary: String,
    val aiAnalysis: String,
    val translating: String,
    val translate: String,
    val collapse: String,
    val expand: String,
    val menu: String,

    // ChatScreen
    val newConversation: String,
    val deleteConversation: String,
    val title: String,
    val titlePlaceholder: String,
    val persona: String,
    val defaultPersona: String,
    val projectOptional: String,
    val none: String,
    val documents: String,
    val changePersona: String,
    val selectPersona: String,
    val attachDocument: String,
    val typeAMessage: String,
    val send: String,
    val deleteDocument: String,

    // DigestHistoryScreen
    val digestHistory: String,
    val filterByProject: String,
    val noDigestsYet: String,

    // ProjectsScreen
    val createProject: String,
    val editProject: String,
    val deleteProject: String,
    val researchProjects: String,
    val keywordsLabel: String,
    val keywordsPlaceholder: String,
    val setActive: String,

    // Sort modes
    val sortByDate: String,
    val sortByRelevance: String,
    val showAll: String,
    val showStarredOnly: String,
    val hideArchived: String,
    val showArchived: String,
) {
    // Dynamic strings with parameters
    fun connectionFailed(message: String) = "$connectionFailedPrefix$message"
    fun memoryExtractionInterval(count: Int) = "$memoryExtractionIntervalPrefix$count$memoryExtractionIntervalSuffix"
    fun dailyArticleBudget(count: Int) = "$dailyArticleBudgetPrefix$count"
    fun analysisMaxPerCycle(count: Int) = "$analysisMaxPerCyclePrefix$count"
    fun deleteSourceConfirm(name: String) = "$deleteConfirmPrefix\"$name\"$deleteSourceConfirmSuffix"
    fun deleteProjectConfirm(name: String) = "$deleteConfirmPrefix\"$name\"$deleteProjectConfirmSuffix"
    fun deleteConversationConfirm(title: String) = "\"$title\"$deleteConversationConfirmSuffix"
    fun deleteDocumentConfirm(filename: String) = "\"$filename\"$deleteDocumentConfirmSuffix"
    fun consecutiveFailures(count: Int) = "$count$consecutiveFailuresSuffix"
    fun articlesCount(count: Long) = "$count $articleLabel"
    fun citedCount(count: Int) = "$count $citedLabel"
    fun relevancePercent(score: Double) = "$relevancePrefix${"%.0f".format(score * 100)}%"
    fun citationsCount(count: Int) = "$citationsPrefix$count"
    fun influentialCount(count: Int) = "$influentialPrefix$count"
    fun stepLabel(current: Int, total: Int) = "$stepPrefix$current/$total"

    // Internal prefixes/suffixes for dynamic strings
    internal val connectionFailedPrefix: String get() = if (this === ZhStrings) "连接失败: " else "Connection failed: "
    internal val memoryExtractionIntervalPrefix: String get() = if (this === ZhStrings) "记忆提取间隔: " else "Memory Extraction Interval: "
    internal val memoryExtractionIntervalSuffix: String get() = if (this === ZhStrings) " 条消息" else " messages"
    internal val dailyArticleBudgetPrefix: String get() = if (this === ZhStrings) "每日文章预算: " else "Daily Article Budget: "
    internal val analysisMaxPerCyclePrefix: String get() = if (this === ZhStrings) "每周期最大分析数: " else "Analysis Max Per Cycle: "
    internal val deleteConfirmPrefix: String get() = if (this === ZhStrings) "删除 " else "Delete "
    internal val deleteSourceConfirmSuffix: String get() = if (this === ZhStrings) "？此操作不可撤销。" else "? This cannot be undone."
    internal val deleteProjectConfirmSuffix: String get() = if (this === ZhStrings) "？文章将被取消关联但不会被删除。" else "? Articles will be unassigned but not deleted."
    internal val deleteConversationConfirmSuffix: String get() = if (this === ZhStrings) " 及其所有消息将被永久删除。" else " and all its messages will be permanently deleted."
    internal val deleteDocumentConfirmSuffix: String get() = if (this === ZhStrings) " 及其所有分块将被永久删除。" else " and all its chunks will be permanently deleted."
    internal val consecutiveFailuresSuffix: String get() = if (this === ZhStrings) " 次连续失败" else " consecutive failure(s)"
    internal val articleLabel: String get() = if (this === ZhStrings) "篇文章" else "articles"
    internal val citedLabel: String get() = if (this === ZhStrings) "次引用" else "cited"
    internal val relevancePrefix: String get() = if (this === ZhStrings) "相关度: " else "Relevance: "
    internal val citationsPrefix: String get() = if (this === ZhStrings) "引用数: " else "Citations: "
    internal val influentialPrefix: String get() = if (this === ZhStrings) "高影响力: " else "Influential: "
    internal val stepPrefix: String get() = if (this === ZhStrings) "第 " else "Step "

    companion object {
        fun forLanguage(language: String): AppStrings = when (language) {
            "zh" -> ZhStrings
            else -> EnStrings
        }
    }
}

val EnStrings = AppStrings(
    // Navigation
    home = "Home",
    articles = "Articles",
    chat = "Chat",
    settings = "Settings",

    // Common
    save = "Save",
    cancel = "Cancel",
    delete = "Delete",
    close = "Close",
    back = "Back",
    edit = "Edit",
    create = "Create",
    skip = "Skip",
    next = "Next",
    name = "Name",
    description = "Description",
    active = "Active",

    // HomeScreen
    welcomeToLumen = "Welcome to Lumen",
    sources = "Sources",
    todaysDigest = "Today's Digest",
    refreshDigest = "Refresh digest",
    viewAll = "View All",
    noDigestAvailable = "No digest available for today",
    digestGeneratedAfterCollection = "Digests are generated after articles are collected and analyzed.",
    sparkInsights = "Spark Insights",
    projects = "Projects",
    tapToExpand = "Tap to expand",
    tapToCollapse = "Tap to collapse",
    highlights = "Key Findings",
    trends = "Trends",
    autoCategory = "Auto-categorized",
    noSparkInsights = "No cross-project insights yet",
    sourceDistribution = "Source Distribution",
    other = "Other",

    // SettingsScreen
    llmSettings = "LLM Settings",
    provider = "Provider",
    model = "Model",
    apiKey = "API Key",
    apiBaseUrlOptional = "API Base URL (optional)",
    egDeepseekChat = "e.g. deepseek-chat",
    leaveEmptyForDefault = "Leave empty for default",
    hideApiKey = "Hide API key",
    showApiKey = "Show API key",
    settingsSaved = "Settings saved",
    testConnection = "Test Connection",
    connectionSuccessful = "Connection successful",
    preferences = "Preferences",
    theme = "Theme",
    language = "Language",
    memoryAutoRecall = "Memory Auto-Recall",
    dataManagement = "Data Management",
    manageSources = "Manage Sources",
    manageResearchProjects = "Manage Research Projects",
    backup = "Backup",
    exportData = "Export Data",
    importData = "Import Data",
    exportAvailableViaApi = "Export is available via server API (POST /api/archive/export)",
    importAvailableViaApi = "Import is available via server API (POST /api/archive/import)",

    // OnboardingScreen
    onboardingIntro = "Lumen is your personal AI assistant for research and companionship. Let's get you set up in a few quick steps.",
    step1ConfigureLlm = "Configure LLM Provider",
    step2RssSources = "RSS Sources",
    step2Intro = "Lumen can collect articles from these RSS feeds. Toggle the sources you want to follow.",
    step3CommunicationStyle = "Communication Style",
    step3Intro = "Choose Lumen's default communication style. You can switch anytime in chat.",
    complete = "Complete",

    // SourcesScreen
    addSource = "Add source",
    editSource = "Edit Source",
    deleteSource = "Delete Source",
    feedUrl = "Feed URL",
    category = "Category",
    categoryPlaceholder = "e.g. academic, tech, news",
    descriptionOptional = "Description (optional)",

    // ArticlesScreen
    refreshFeeds = "Refresh feeds",
    allProjects = "All Projects",
    project = "Project",
    allSources = "All Sources",
    source = "Source",
    status = "Status",
    noArticlesYet = "No articles yet",
    tapRefreshToFetch = "Tap the refresh button to fetch feeds.",
    articleNotAnalyzed = "Article Not Analyzed",
    analyzePrompt = "This article has not been analyzed by AI yet. Would you like to analyze it now? This will generate an AI summary and extract keywords.",
    analysisComplete = "Analysis complete",
    analyzing = "Analyzing...",
    analyze = "Analyze",
    viewWithoutAnalysis = "View Without Analysis",
    viewDetails = "View Details",
    archive = "Archive",
    restore = "Restore",
    aiSummary = "AI Summary",
    keywords = "Keywords",
    analyzed = "Analyzed",
    archived = "Archived",
    starred = "Starred",
    unanalyzed = "Unanalyzed",

    // Pipeline stages
    fetchingArticles = "Fetching articles...",
    removingDuplicates = "Removing duplicates...",
    enrichingContent = "Enriching content...",
    embeddingArticles = "Embedding articles...",
    scoringRelevance = "Scoring relevance...",
    analyzingArticles = "Analyzing articles...",
    generatingSparks = "Generating sparks...",
    generatingDigest = "Generating digest...",

    // ArticleDetailScreen
    tableOfContents = "Table of Contents",
    star = "Star",
    unstar = "Unstar",
    abstract_ = "Abstract",
    translation = "Translation",
    articleContent = "Article Content",
    sourceText = "Source Text",
    aiCommentary = "AI Commentary",
    aiAnalysis = "AI Analysis",
    translating = "Translating...",
    translate = "Translate",
    collapse = "Collapse",
    expand = "Expand",
    menu = "Menu",

    // ChatScreen
    newConversation = "New conversation",
    deleteConversation = "Delete conversation?",
    title = "Title",
    titlePlaceholder = "e.g. Research discussion",
    persona = "Persona",
    defaultPersona = "Default",
    projectOptional = "Project (optional)",
    none = "None",
    documents = "Documents",
    changePersona = "Change persona",
    selectPersona = "Select Persona",
    attachDocument = "Attach document",
    typeAMessage = "Type a message...",
    send = "Send",
    deleteDocument = "Delete document?",

    // DigestHistoryScreen
    digestHistory = "Digest History",
    filterByProject = "Filter by Project",
    noDigestsYet = "No digests yet",

    // ProjectsScreen
    createProject = "Create project",
    editProject = "Edit Project",
    deleteProject = "Delete Project",
    researchProjects = "Research Projects",
    keywordsLabel = "Keywords",
    keywordsPlaceholder = "comma-separated, e.g. LLM, RAG, agents",
    setActive = "Set active",

    // Sort modes
    sortByDate = "Date",
    sortByRelevance = "Relevance",
    showAll = "Show all",
    showStarredOnly = "Show starred only",
    hideArchived = "Hide archived",
    showArchived = "Show archived",
)

val ZhStrings = AppStrings(
    // Navigation
    home = "首页",
    articles = "文章",
    chat = "对话",
    settings = "设置",

    // Common
    save = "保存",
    cancel = "取消",
    delete = "删除",
    close = "关闭",
    back = "返回",
    edit = "编辑",
    create = "创建",
    skip = "跳过",
    next = "下一步",
    name = "名称",
    description = "描述",
    active = "活跃",

    // HomeScreen
    welcomeToLumen = "欢迎使用 Lumen",
    sources = "来源",
    todaysDigest = "今日摘要",
    refreshDigest = "刷新摘要",
    viewAll = "查看全部",
    noDigestAvailable = "今天暂无摘要",
    digestGeneratedAfterCollection = "摘要将在文章采集和分析完成后自动生成。",
    sparkInsights = "灵感洞察",
    projects = "项目",
    tapToExpand = "点击展开",
    tapToCollapse = "点击收起",
    highlights = "核心发现",
    trends = "趋势分析",
    autoCategory = "自动分类",
    noSparkInsights = "暂无跨项目洞察",
    sourceDistribution = "来源分布",
    other = "其他",

    // SettingsScreen
    llmSettings = "LLM 设置",
    provider = "提供商",
    model = "模型",
    apiKey = "API 密钥",
    apiBaseUrlOptional = "API 地址（可选）",
    egDeepseekChat = "例如 deepseek-chat",
    leaveEmptyForDefault = "留空使用默认值",
    hideApiKey = "隐藏密钥",
    showApiKey = "显示密钥",
    settingsSaved = "设置已保存",
    testConnection = "测试连接",
    connectionSuccessful = "连接成功",
    preferences = "偏好设置",
    theme = "主题",
    language = "语言",
    memoryAutoRecall = "记忆自动回忆",
    dataManagement = "数据管理",
    manageSources = "管理来源",
    manageResearchProjects = "管理研究项目",
    backup = "备份",
    exportData = "导出数据",
    importData = "导入数据",
    exportAvailableViaApi = "导出功能可通过服务器 API 使用 (POST /api/archive/export)",
    importAvailableViaApi = "导入功能可通过服务器 API 使用 (POST /api/archive/import)",

    // OnboardingScreen
    onboardingIntro = "Lumen 是你的个人 AI 研究助手和伙伴。让我们通过几个简单的步骤完成设置。",
    step1ConfigureLlm = "配置 LLM 提供商",
    step2RssSources = "RSS 来源",
    step2Intro = "Lumen 可以从这些 RSS 源收集文章。选择你想关注的来源。",
    step3CommunicationStyle = "交流风格",
    step3Intro = "选择 Lumen 的默认交流风格。你可以随时在对话中切换。",
    complete = "完成",

    // SourcesScreen
    addSource = "添加来源",
    editSource = "编辑来源",
    deleteSource = "删除来源",
    feedUrl = "订阅地址",
    category = "分类",
    categoryPlaceholder = "例如 学术, 技术, 新闻",
    descriptionOptional = "描述（可选）",

    // ArticlesScreen
    refreshFeeds = "刷新订阅",
    allProjects = "所有项目",
    project = "项目",
    allSources = "所有来源",
    source = "来源",
    status = "状态",
    noArticlesYet = "暂无文章",
    tapRefreshToFetch = "点击刷新按钮获取订阅内容。",
    articleNotAnalyzed = "文章未分析",
    analyzePrompt = "这篇文章尚未经过 AI 分析。是否现在进行分析？分析将生成 AI 摘要并提取关键词。",
    analysisComplete = "分析完成",
    analyzing = "分析中...",
    analyze = "分析",
    viewWithoutAnalysis = "直接查看",
    viewDetails = "查看详情",
    archive = "归档",
    restore = "恢复",
    aiSummary = "AI 摘要",
    keywords = "关键词",
    analyzed = "已分析",
    archived = "已归档",
    starred = "已收藏",
    unanalyzed = "未分析",

    // Pipeline stages
    fetchingArticles = "正在获取文章...",
    removingDuplicates = "正在去重...",
    enrichingContent = "正在丰富内容...",
    embeddingArticles = "正在生成向量...",
    scoringRelevance = "正在评估相关性...",
    analyzingArticles = "正在分析文章...",
    generatingSparks = "正在生成灵感...",
    generatingDigest = "正在生成摘要...",

    // ArticleDetailScreen
    tableOfContents = "目录",
    star = "收藏",
    unstar = "取消收藏",
    abstract_ = "摘要",
    translation = "翻译",
    articleContent = "文章内容",
    sourceText = "原文",
    aiCommentary = "AI 评论",
    aiAnalysis = "AI 分析",
    translating = "翻译中...",
    translate = "翻译",
    collapse = "收起",
    expand = "展开",
    menu = "菜单",

    // ChatScreen
    newConversation = "新对话",
    deleteConversation = "删除对话？",
    title = "标题",
    titlePlaceholder = "例如 研究讨论",
    persona = "人设",
    defaultPersona = "默认",
    projectOptional = "项目（可选）",
    none = "无",
    documents = "文档",
    changePersona = "切换人设",
    selectPersona = "选择人设",
    attachDocument = "添加文档",
    typeAMessage = "输入消息...",
    send = "发送",
    deleteDocument = "删除文档？",

    // DigestHistoryScreen
    digestHistory = "摘要历史",
    filterByProject = "按项目筛选",
    noDigestsYet = "暂无摘要",

    // ProjectsScreen
    createProject = "创建项目",
    editProject = "编辑项目",
    deleteProject = "删除项目",
    researchProjects = "研究项目",
    keywordsLabel = "关键词",
    keywordsPlaceholder = "逗号分隔，例如 LLM, RAG, agents",
    setActive = "设为活跃",

    // Sort modes
    sortByDate = "日期",
    sortByRelevance = "相关度",
    showAll = "显示全部",
    showStarredOnly = "仅显示收藏",
    hideArchived = "隐藏已归档",
    showArchived = "显示已归档",
)
