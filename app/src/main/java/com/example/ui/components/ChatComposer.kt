package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.AttachmentType
import com.example.data.media.PendingAttachment
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SkyBlue
import java.io.File

@Composable
fun ChatComposer(
    inputText: String,
    onInputChange: (String) -> Unit,
    pendingAttachment: PendingAttachment?,
    onClearAttachment: () -> Unit,
    onOpenAttachmentSheet: () -> Unit,
    isListening: Boolean,
    partialSpeechText: String,
    rmsLevel: Float,
    onToggleDictation: () -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val micScale by animateFloatAsState(
        targetValue = if (isListening) 1.08f + (rmsLevel * 0.18f) else 1f,
        label = "micScale"
    )
    val canSend = inputText.isNotBlank() || pendingAttachment != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Banner de dictado por voz en vivo
        AnimatedVisibility(visible = isListening) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ElectricBlue.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, SkyBlue.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Escuchando voz",
                        tint = SkyBlue,
                        modifier = Modifier.scale(micScale)
                    )
                    Text(
                        text = partialSpeechText.ifBlank { "Escuchando tu voz en español…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = onToggleDictation,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Detener dictado",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Vista previa de adjunto pendiente (Imagen o PDF)
        AnimatedVisibility(visible = pendingAttachment != null) {
            pendingAttachment?.let { attachment ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (attachment.type == AttachmentType.IMAGE) {
                            AsyncImage(
                                model = File(attachment.localPath),
                                contentDescription = attachment.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(ElectricBlue.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!attachment.previewPath.isNullOrBlank()) {
                                    AsyncImage(
                                        model = File(attachment.previewPath),
                                        contentDescription = attachment.fileName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.PictureAsPdf,
                                        contentDescription = "PDF adjunto",
                                        tint = SkyBlue
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = attachment.metadataSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = onClearAttachment,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Quitar adjunto",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Cápsula principal de entrada
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = if (isListening) SkyBlue else MaterialTheme.colorScheme.outline
            ),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = onOpenAttachmentSheet,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Adjuntar foto, PDF o generar imagen",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp, max = 140.dp)
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.message_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(SkyBlue),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { if (canSend && !isGenerating) onSend() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_input")
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(
                    onClick = onToggleDictation,
                    modifier = Modifier
                        .size(44.dp)
                        .scale(micScale)
                        .testTag("mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Dictar mensaje por micrófono",
                        tint = if (isListening) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                if (isGenerating) {
                    IconButton(
                        onClick = onStopGeneration,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ErrorRed.copy(alpha = 0.16f))
                            .testTag("stop_generation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Detener generación",
                            tint = ErrorRed
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) ElectricBlue
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Enviar mensaje",
                            tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
