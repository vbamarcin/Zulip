package com.mkras.zulip.presentation.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedAttachment(
    val fileName: String,
    val mimeType: String?,
    val bytes: ByteArray
)

typealias UploadAttachmentMessage = (
    type: String,
    to: String,
    topic: String?,
    attachment: PickedAttachment,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) -> Unit

suspend fun readPickedAttachment(context: Context, uri: Uri): PickedAttachment? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val fileName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        if (bytes.isEmpty()) {
            return@withContext null
        }

        PickedAttachment(
            fileName = fileName,
            mimeType = resolver.getType(uri),
            bytes = bytes
        )
    }.getOrNull()
}