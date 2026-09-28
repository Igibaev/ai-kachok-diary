package com.fitcoach.app.ai

import com.fitcoach.app.domain.model.ChatMessage

/** Одна реплика в формате, который принимают и прокси, и Anthropic API. */
data class AiTurn(val role: String, val content: String)

/**
 * Приводит историю чата к требованиям Messages API:
 *  - сообщения об ошибках (isError) и пустые реплики не отправляются;
 *  - первая реплика — всегда user (ведущие ответы ассистента отбрасываются);
 *  - роли строго чередуются: подряд идущие реплики одной роли склеиваются через пустую строку;
 *  - в конец добавляется текущее сообщение пользователя (склеивается с предыдущим user,
 *    если модель ещё не успела ответить);
 *  - берётся не более [maxTurns] последних реплик ДО текущего сообщения.
 */
object HistorySanitizer {

    const val ROLE_USER = "user"
    const val ROLE_ASSISTANT = "assistant"

    fun build(history: List<ChatMessage>, userMessage: String, maxTurns: Int = 20): List<AiTurn> {
        val cleaned = history
            .asSequence()
            .filter { !it.isError }
            .filter { it.role == ROLE_USER || it.role == ROLE_ASSISTANT }
            .filter { it.content.isNotBlank() }
            .toList()
            .takeLast(maxTurns)

        val merged = ArrayList<AiTurn>(cleaned.size + 1)
        for (m in cleaned) {
            append(merged, AiTurn(m.role, m.content.trim()))
        }
        append(merged, AiTurn(ROLE_USER, userMessage.trim()))
        return merged
    }

    private fun append(list: MutableList<AiTurn>, turn: AiTurn) {
        // Первое сообщение должно быть от пользователя.
        if (list.isEmpty() && turn.role != ROLE_USER) return
        val last = list.lastOrNull()
        if (last != null && last.role == turn.role) {
            list[list.lastIndex] = last.copy(content = (last.content + "\n\n" + turn.content).trim())
        } else {
            list.add(turn)
        }
    }
}
