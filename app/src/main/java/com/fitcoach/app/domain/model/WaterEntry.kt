package com.fitcoach.app.domain.model

data class WaterEntry(
    val id: String,
    val date: Long,
    val amountMl: Int,
    val createdAt: Long
)
