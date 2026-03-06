package com.lumen.companion.agent

sealed interface ChatResult {
    data class Success(val response: String) : ChatResult
    data class Error(val message: String, val cause: Throwable? = null) : ChatResult
}
