package com.fitcoach.app.domain.repository

import com.fitcoach.app.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllMessages(): Flow<List<ChatMessage>>
    suspend fun getRecentMessages(limit: Int = 20): List<ChatMessage>
    suspend fun saveMessage(message: ChatMessage)
    suspend fun clearAll()
}
