package com.lumen.companion.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Document_
import com.lumen.research.parseCsvSet
import kotlinx.serialization.Serializable

@Serializable
data class GetProjectInfoArgs(
    val projectId: Long,
)

class GetProjectInfoTool(
    private val db: LumenDatabase,
) : SimpleTool<GetProjectInfoArgs>(
    GetProjectInfoArgs.serializer(),
    "get_project_info",
    "Get detailed information about a research project including description, keywords, and counts of associated articles and documents.",
) {
    override suspend fun execute(args: GetProjectInfoArgs): String {
        val project = db.researchProjectBox.get(args.projectId)
        if (project == null || project.id == 0L) {
            return "Project not found."
        }

        val documentCount = db.documentBox.query()
            .equal(Document_.projectId, args.projectId)
            .build()
            .use { it.count() }

        val articleCount = db.articleBox.all.count {
            args.projectId.toString() in parseCsvSet(it.projectIds)
        }

        return buildString {
            appendLine("Project: ${project.name}")
            if (project.description.isNotBlank()) {
                appendLine("Description: ${project.description}")
            }
            if (project.keywords.isNotBlank()) {
                appendLine("Keywords: ${project.keywords}")
            }
            appendLine("Documents: $documentCount")
            append("Articles: $articleCount")
        }
    }
}
