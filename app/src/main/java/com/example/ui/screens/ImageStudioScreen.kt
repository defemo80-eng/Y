package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.media.AttachmentHelper
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SkyBlue
import com.example.ui.viewmodel.ChatViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageStudioScreen(
    chatViewModel: ChatViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val imageRequests by chatViewModel.imageRequests.collectAsStateWithLifecycle()
    val isGeneratingImage by chatViewModel.isGeneratingImage.collectAsStateWithLifecycle()
    val imageErrorBanner by chatViewModel.imageErrorBanner.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("") }
    var selectedAspect by remember { mutableStateOf("1:1") }
    var selectedStyle by remember { mutableStateOf("Fotorrealista") }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    val aspects = listOf("1:1", "16:9", "9:16")
    val styles = listOf("Fotorrealista", "Cyberpunk Azul", "Minimalista", "Ilustración 3D")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estudio de Imágenes IA") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("image_studio_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver al chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AnimatedVisibility(visible = imageErrorBanner != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ErrorRed.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = ErrorRed)
                            Text(
                                text = imageErrorBanner.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = chatViewModel::dismissImageError) {
                                Text("Cerrar", color = ErrorRed)
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("Describe la imagen que deseas crear") },
                            placeholder = { Text("Ej. Robot azul eléctrico con corona dorada en ciudad neón") },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SkyBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("image_prompt_input")
                        )

                        Text("Proporción", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            aspects.forEach { ratio ->
                                FilterChip(
                                    selected = selectedAspect == ratio,
                                    onClick = { selectedAspect = ratio },
                                    label = { Text(ratio) }
                                )
                            }
                        }

                        Text("Estilo visual", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            styles.forEach { style ->
                                FilterChip(
                                    selected = selectedStyle == style,
                                    onClick = { selectedStyle = style },
                                    label = { Text(style) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val submitted = prompt
                                prompt = ""
                                chatViewModel.generateImageRequest(submitted, selectedAspect, selectedStyle)
                            },
                            enabled = prompt.isNotBlank() && !isGeneratingImage,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("generate_image_button")
                        ) {
                            if (isGeneratingImage) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Generando con Gemini…")
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generar imagen con Gemini")
                            }
                        }
                    }
                }
            }

            if (imageRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Imágenes generadas",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(imageRequests, key = { it.id }) { req ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${req.style} • ${req.aspectRatio}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SkyBlue
                                )
                                Row {
                                    if (!req.imagePath.isNullOrBlank()) {
                                        IconButton(
                                            onClick = { AttachmentHelper.shareImageFile(context, req.imagePath) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Share,
                                                contentDescription = "Compartir imagen",
                                                tint = SkyBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { chatViewModel.deleteImageRequest(req.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Eliminar imagen",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            if (!req.imagePath.isNullOrBlank()) {
                                AsyncImage(
                                    model = File(req.imagePath),
                                    contentDescription = req.prompt,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { previewImagePath = req.imagePath }
                                )
                            }
                            Text(
                                text = req.prompt,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = req.statusNotice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    previewImagePath?.let { path ->
        Dialog(onDismissRequest = { previewImagePath = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Imagen generada ampliada",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { AttachmentHelper.shareImageFile(context, path) }) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir")
                        }
                        TextButton(onClick = { previewImagePath = null }) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}
