package com.runtime.android.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = TextStyle.Default
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val context = LocalContext.current

    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is Block.Code -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        // Copy button in top-right corner
                        IconButton(
                            onClick = { copyToClipboard(context, block.code) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "复制代码",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .padding(end = 24.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                block.code,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is Block.Para -> Text(parseInline(block.text), color = color, style = style, modifier = Modifier.padding(vertical = 2.dp))
                is Block.Li -> Row(Modifier.padding(vertical = 1.dp)) {
                    Text("•  ", color = color, style = style)
                    Text(parseInline(block.text), color = color, style = style)
                }
            }
        }
    }
}

private sealed interface Block {
    data class Para(val text: String) : Block
    data class Code(val code: String) : Block
    data class Li(val text: String) : Block
}

private fun parseBlocks(md: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = md.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.add(lines[i]); i++ }
                blocks.add(Block.Code(code.joinToString("\n")))
                i++
            }
            line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") } -> {
                blocks.add(Block.Li(line.trimStart().removePrefix("- ").removePrefix("* ")))
                i++
            }
            line.isBlank() -> i++
            else -> {
                val para = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank() && !lines[i].trimStart().startsWith("```") && !lines[i].trimStart().let { it.startsWith("- ") || it.startsWith("* ") }) {
                    para.add(lines[i]); i++
                }
                blocks.add(Block.Para(para.joinToString(" ")))
            }
        }
    }
    return blocks
}

private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }; i = end + 2 }
                else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)) { append(text.substring(i + 1, end)) }; i = end + 1 }
                else { append(text[i]); i++ }
            }
            text[i] == '*' && i + 1 < text.length && text[i + 1] != '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }; i = end + 1 }
                else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("code", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}
