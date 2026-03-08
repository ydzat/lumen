package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Source(
    @Id var id: Long = 0,
    @Index var name: String = "",
    var url: String = "",
    var type: String = "",
    var category: String = "",
    var description: String = "",
    var icon: String = "",
    var refreshIntervalMin: Int = 60,
    var enabled: Boolean = true,
    var lastFetchedAt: Long = 0,
    var createdAt: Long = 0,
    var config: String = "",
    var lastError: String = "",
    var consecutiveFailures: Int = 0,
    var nextRetryAt: Long = 0,
)
