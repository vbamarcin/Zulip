package com.mkras.zulip.presentation.chat

object EmojiHelper {
    val COMMON_EMOJIS = listOf(
        "👍" to "thumbs_up",
        "👎" to "thumbs_down",
        "❤️" to "heart",
        "😂" to "joy",
        "😮" to "open_mouth",
        "😢" to "cry",
        "🎉" to "tada",
        "🔥" to "fire",
        "👀" to "eyes",
        "✨" to "sparkles",
        "🙌" to "raised_hands",
        "💯" to "100",
        "🚀" to "rocket"
    )

    data class ReactionToken(
        val name: String,
        val code: String?,
        val type: String?,
        val userId: Long?
    )

    data class ReactionAggregate(
        val token: ReactionToken,
        val count: Int,
        val reactedByCurrentUser: Boolean
    )

    data class ReactionDisplay(
        val emojiText: String?,
        val imageUrl: String?
    )

    data class ReactionSelection(
        val name: String,
        val code: String? = null,
        val type: String? = null
    )

    data class CustomEmojiItem(
        val id: String,
        val name: String,
        val url: String
    )

    fun parseReactionSummary(summary: String?): Map<ReactionToken, Int> {
        if (summary.isNullOrBlank()) return emptyMap()
        val tokens = summary.split("|")
            .mapNotNull { raw ->
                val token = raw.trim()
                if (token.isBlank()) return@mapNotNull null
                val parts = token.split("::")
                if (parts.size >= 3) {
                    ReactionToken(
                        name = parts[0].trim(),
                        code = parts[1].trim().ifBlank { null },
                        type = parts[2].trim().ifBlank { null },
                        userId = parts.getOrNull(3)?.trim()?.toLongOrNull()
                    )
                } else {
                    ReactionToken(name = token.trim(':'), code = null, type = null, userId = null)
                }
            }
            .filter { it.name.isNotBlank() }

        if (tokens.isEmpty()) return emptyMap()
        return tokens.groupingBy { it }.eachCount()
    }

    fun summarizeReactionSummary(summary: String?, currentUserId: Long?): List<ReactionAggregate> {
        val entries = parseReactionSummary(summary).entries
        if (entries.isEmpty()) return emptyList()

        val ordered = LinkedHashMap<String, MutableList<Map.Entry<ReactionToken, Int>>>()
        entries.forEach { entry ->
            val key = "${entry.key.name}::${entry.key.code.orEmpty()}::${entry.key.type.orEmpty()}"
            ordered.getOrPut(key) { mutableListOf() }.add(entry)
        }

        return ordered.values.map { group ->
            val representative = group.first().key
            val totalCount = group.sumOf { it.value }
            val reactedByCurrentUser = currentUserId != null && group.any { it.key.userId == currentUserId }
            ReactionAggregate(
                token = representative,
                count = totalCount,
                reactedByCurrentUser = reactedByCurrentUser
            )
        }
    }

    fun toReactionSelection(token: ReactionToken): ReactionSelection {
        return ReactionSelection(
            name = token.name,
            code = token.code,
            type = token.type
        )
    }

    fun resolveReactionDisplay(
        token: ReactionToken,
        customEmojiById: Map<String, String>,
        customEmojiByName: Map<String, String>
    ): ReactionDisplay {
        val normalizedName = token.name.trim(':')
        if (token.type == "realm_emoji") {
            val url = token.code?.let { customEmojiById[it] }
                ?: customEmojiByName[normalizedName.lowercase()]
            if (!url.isNullOrBlank()) {
                return ReactionDisplay(emojiText = null, imageUrl = url)
            }
        }

        if (token.type == "unicode_emoji" && !token.code.isNullOrBlank()) {
            val decoded = decodeUnicodeEmoji(token.code)
            if (!decoded.isNullOrBlank()) {
                return ReactionDisplay(emojiText = decoded, imageUrl = null)
            }
        }

        val mapped = COMMON_EMOJIS.firstOrNull { it.second == normalizedName }?.first
        return ReactionDisplay(emojiText = mapped ?: ":$normalizedName:", imageUrl = null)
    }

    fun decodeUnicodeEmoji(code: String): String? {
        val cleaned = code.trim().lowercase()
        if (cleaned.isBlank()) return null
        return cleaned.split("-")
            .mapNotNull { part -> part.toIntOrNull(16) }
            .takeIf { it.isNotEmpty() }
            ?.flatMap { cp -> Character.toChars(cp).asList() }
            ?.joinToString("")
    }

    fun encodeUnicodeEmoji(emoji: String): String? {
        val cleaned = emoji.trim()
        if (cleaned.isBlank()) return null
        val codePoints = cleaned.codePoints().toArray()
        if (codePoints.isEmpty()) return null
        return codePoints.joinToString("-") { cp -> cp.toString(16) }
    }
}
