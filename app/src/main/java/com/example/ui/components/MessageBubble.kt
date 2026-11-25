package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.AttachmentType
import com.example.data.local.MessageEntity
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SkyBlue
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun MessageBubble(
    message: MessageEntity,
    isSpeakingThis: Boolean,
    onEditClick: (MessageEntity) -> Unit,
    onRegenerateClick: (MessageEntity) -> Unit,
    onSpeakClick: (MessageEntity) -> Unit,
    onImageClick: (String) -> Unit,
    onPdfClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"

    if (isUser) {
        UserMessageBubble(
            message = message,
            onEditClick = { onEditClick(message) },
            onImageClick = onImageClick,
            onPdfClick = onPdfClick,
            modifier = modifier
        )
    } else {
        AssistantMessageBubble(
            message = message,
            isSpeakingThis = isSpeakingThis,
            isStreaming = false,
            onRegenerateClick = { onRegenerateClick(message) },
            onSpeakClick = { onSpeakClick(message) },
            onImageClick = onImageClick,
            modifier = modifier
        )
    }
}

@Composable
fun StreamingAssistantBubble(
    streamingText: String,
    modifier: Modifier = Modifier
) {
    val tempMsg = remember(streamingText) {
        MessageEntity(
            id = "streaming_temp",
            conversationId = "",
            role = "assistant",
            content = streamingText.ifBlank { "Pensando…" },
            isPlaceholderNotice = false
        )
    }
    AssistantMessageBubble(
        message = tempMsg,
        isSpeakingThis = false,
        isStreaming = true,
        onRegenerateClick = {},
        onSpeakClick = {},
        onImageClick = {},
        modifier = modifier
    )
}

@Composable
private fun UserMessageBubble(
    message: MessageEntity,
    onEditClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onPdfClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End
    ) {
        if (message.attachmentType == AttachmentType.IMAGE.name && !message.attachmentPath.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SkyBlue.copy(alpha = 0.5f)),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = 240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onImageClick(message.attachmentPath) }
            ) {
                Column {
                    AsyncImage(
                        model = File(message.attachmentPath),
                        contentDescription = message.attachmentName ?: "Imagen adjunta",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    )
                    if (!message.attachmentMeta.isNullOrBlank()) {
                        Text(
                            text = message.attachmentMeta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else if (message.attachmentType == AttachmentType.PDF.name && !message.attachmentPath.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPdfClick(message.attachmentPath) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!message.attachmentPreviewPath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(message.attachmentPreviewPath),
                            contentDescription = "Vista previa PDF",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp, 56.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = "Documento PDF",
                                tint = SkyBlue
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.attachmentName ?: "Documento.pdf",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!message.attachmentMeta.isNullOrBlank()) {
                            Text(
                                text = message.attachmentMeta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("edit_message_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Editar mensaje",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 6.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                ),
                color = ElectricBlue,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(ElectricBlue, Color(0xFF1554D1))
                            )
                        )
                        .padding(horizontal = 15.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: MessageEntity,
    isSpeakingThis: Boolean,
    isStreaming: Boolean,
    onRegenerateClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copiedRecently by remember { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(1800)
            copiedRecently = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            border = BorderStroke(1.dp, SkyBlue.copy(alpha = 0.7f)),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.chatdep_logo),
                contentDescription = "Avatar ChatDep",
                contentScale = ContentScale.Crop,
                modifier = Modifier.clip(CircleShape)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ChatDep",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (isStreaming) {
                    Text(
                        text = "escribiendo…",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (message.attachmentType == AttachmentType.IMAGE.name && !message.attachmentPath.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SkyBlue.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onImageClick(message.attachmentPath) }
                ) {
                    AsyncImage(
                        model = File(message.attachmentPath),
                        contentDescription = message.attachmentMeta ?: "Imagen generada por ChatDep",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    )
                }
            }

            FormattedResponseContent(content = message.content)

            AnimatedVisibility(visible = !isStreaming) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MessageActionChip(
                        icon = if (copiedRecently) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        label = if (copiedRecently) "Copiado" else "Copiar",
                        onClick = {
                            clipboard.setText(AnnotatedString(message.content))
                            copiedRecently = true
                        },
                        testTag = "copy_response_button"
                    )
                    MessageActionChip(
                        icon = if (isSpeakingThis) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp,
                        label = if (isSpeakingThis) "Detener voz" else "Escuchar",
                        onClick = onSpeakClick,
                        testTag = "speak_response_button"
                    )
                    MessageActionChip(
                        icon = Icons.Rounded.Refresh,
                        label = "Regenerar",
                        onClick = onRegenerateClick,
                        testTag = "regenerate_response_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FormattedResponseContent(content: String) {
    val clipboard = LocalClipboardManager.current
    val segments = remember(content) { splitCodeBlocks(content) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            if (segment.isCode) {
                var codeCopied by remember { mutableStateOf(false) }
                LaunchedEffect(codeCopied) {
                    if (codeCopied) {
                        delay(1500)
                        codeCopied = false
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0A0F1D),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF10192E))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = segment.language.ifBlank { "código" },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = SkyBlue
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        clipboard.setText(AnnotatedString(segment.text))
                                        codeCopied = true
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (codeCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    contentDescription = "Copiar código",
                                    tint = SkyBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (codeCopied) "Copiado" else "Copiar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkyBlue
                                )
                            }
                        }
                        Text(
                            text = segment.text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = parseInlineBold(segment.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

private data class TextSegment(
    val text: String,
    val isCode: Boolean,
    val language: String = ""
)

private fun splitCodeBlocks(raw: String): List<TextSegment> {
    val regex = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")
    val result = mutableListOf<TextSegment>()
    var lastIdx = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > lastIdx) {
            val plain = raw.substring(lastIdx, match.range.first).trim()
            if (plain.isNotEmpty()) result.add(TextSegment(plain, isCode = false))
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        result.add(TextSegment(code, isCode = true, language = lang))
        lastIdx = match.range.last + 1
    }
    if (lastIdx < raw.length) {
        val tail = raw.substring(lastIdx).trim()
        if (tail.isNotEmpty()) result.add(TextSegment(tail, isCode = false))
    }
    if (result.isEmpty()) result.add(TextSegment(raw, isCode = false))
    return result
}

private fun parseInlineBold(text: String): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        val regex = Regex("\\*\\*(.+?)\\*\\*")
        for (match in regex.findAll(text)) {
            if (match.range.first > idx) {
                append(text.substring(idx, match.range.first))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            idx = match.range.last + 1
        }
        if (idx < text.length) {
            append(text.substring(idx))
        }
    }
}
