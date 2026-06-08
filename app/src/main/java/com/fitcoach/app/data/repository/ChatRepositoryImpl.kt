package com.fitcoach.app.data.repository

import com.fitcoach.app.data.local.db.dao.ChatMessageDao
import com.fitcoach.app.data.local.db.entity.ChatMessageEntity
import com.fitcoach.app.domain.model.ChatMessage
import com.fitcoach.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatMessageDao
) : ChatRepository {

    override fun getAllMessages(): Flow<List<ChatMessage>> =
        dao.getAllMessages().map { it.map { e -> e.toDomain() } }

    override suspend fun getRecentMessages(limit: Int): List<ChatMessage> =
        dao.getRecentMessages(limit).reversed().map { it.toDomain() }

    override suspend fun saveMessage(message: ChatMessage) =
        dao.insertMessage(message.toEntity())

    override suspend fun clearAll() = dao.clearAll()
}

private fun ChatMessageEntity.toDomain() = ChatMessage(id, role, content, timestamp, contextDate)
private fun ChatMessage.toEntity() = ChatMessageEntity(id, role, content, timestamp, contextDate)
