package com.fitcoach.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val contextDate: Long? = null,
    /** Сообщение об ошибке AI (не отправляется модели). Схема изменена — требуется bump версии БД. */
    @ColumnInfo(defaultValue = "0") val isError: Boolean = false
)
