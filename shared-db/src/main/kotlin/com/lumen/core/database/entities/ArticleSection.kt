package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class ArticleSection(
    @Id var id: Long = 0,
    @Index var articleId: Long = 0,
    var sectionIndex: Int = 0,
    var heading: String = "",
    var content: String = "",
    var level: Int = 1,
    var isKeySection: Boolean = false,
    var aiCommentary: String = "",
    var aiTranslation: String = "",
)
