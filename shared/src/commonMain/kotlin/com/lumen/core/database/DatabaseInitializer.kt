package com.lumen.core.database

expect class PlatformDatabaseConfig

expect fun createLumenDatabase(config: PlatformDatabaseConfig): LumenDatabase
