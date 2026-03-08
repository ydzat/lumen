package com.lumen.research.collector

import com.lumen.core.database.entities.ResearchProject

data class FetchContext(
    val activeProjects: List<ResearchProject>,
    val keywords: Set<String>,
    val categories: Set<String>,
    val remainingBudget: Int,
    val sparkKeywords: Set<String> = emptySet(),
)
