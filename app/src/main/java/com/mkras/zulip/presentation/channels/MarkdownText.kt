package com.mkras.zulip.presentation.channels

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color,
    compactMode: Boolean
) {
    val context = LocalContext.current
    val markwon = remember(context, compactMode) {
        val density = context.resources.displayMetrics.density
        val blockMarginPx = ((if (compactMode) 8f else 12f) * density).toInt()
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(ImagesPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .blockMargin(blockMarginPx)
                        .blockQuoteWidth((2f * density).toInt())
                        .blockQuoteColor(0x668CD9FF.toInt())
                }
            })
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor.toArgb())
                textSize = if (compactMode) 12f else 14f
                setLineSpacing(2f, 1f)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgb())
            textView.textSize = if (compactMode) 12f else 14f
            markwon.setMarkdown(textView, markdown)
        }
    )
}
