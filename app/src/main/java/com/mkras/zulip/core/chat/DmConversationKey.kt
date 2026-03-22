package com.mkras.zulip.core.chat

object DmConversationKey {

    fun fromRecipientMaps(recipients: List<Map<*, *>>, fallbackEmail: String, selfEmail: String): String {
        val recipientEmails = recipients
            .mapNotNull { it["email"] as? String }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val participants = if (recipientEmails.isEmpty()) {
            listOf(fallbackEmail)
        } else {
            recipientEmails
        }

        return normalizeParticipants(participants, selfEmail).joinToString(",")
    }

    fun fromRawTo(rawTo: String, selfEmail: String): String {
        val participants = rawTo
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return normalizeParticipants(participants, selfEmail)
            .joinToString(",")
            .ifBlank { rawTo.trim().lowercase() }
    }

    fun fromStoredKey(rawKey: String, fallbackEmail: String, selfEmail: String): String {
        val participants = rawKey
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                listOf(fallbackEmail.trim()).filter { it.isNotBlank() }
            }

        return normalizeParticipants(participants, selfEmail).joinToString(",")
    }

    private fun normalizeParticipants(participants: List<String>, selfEmail: String): List<String> {
        val withoutSelf = participants.filterNot { it.equals(selfEmail, ignoreCase = true) }
        return (if (withoutSelf.isNotEmpty()) withoutSelf else participants)
            .map { it.lowercase() }
            .distinct()
            .sorted()
    }
}
