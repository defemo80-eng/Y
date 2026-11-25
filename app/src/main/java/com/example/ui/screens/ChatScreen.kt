package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import com.example.data.media.AttachmentHelper
import com.example.data.preferences.UserPreferences
import com.example.data.voice.SpeechDictationHelper
import com.example.data.voice.TtsHelper
import com.example.ui.components.ChatComposer
import com.example.ui.components.ChatDepDrawer
import com.example.ui.components.MessageBubble
import com.example.ui.components.StreamingAssistantBubble
import com.example.ui.components.WelcomeHero
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    user: UserEntity?,
    preferences: UserPreferences,
    chatViewModel: ChatViewModel,
    onToggleTheme: (ThemeMode) -> Unit,
    onOpenImageStudio: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val conversations by chatViewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by chatViewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val searchQuery by chatViewModel.searchQuery.collectAsStateWithLifecycle()
    val pendingAttachment by chatViewModel.pendingAttachment.collectAsStateWithLifecycle()
    val isGenerating by chatViewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingText by chatViewModel.streamingText.collectAsStateWithLifecycle()
    val errorBanner by chatViewModel.errorBanner.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var topMenuExpanded by remember { mutableStateOf(false) }

    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var editTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var editText by remember { mutableStateOf("") }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    // Text-to-Speech en español
    val ttsHelper = remember {
        TtsHelper(context) { err ->
            scope.launch { snackbarHostState.showSnackbar(err) }
        }
    }
    val speakingMessageId by ttsHelper.speakingMessageId.collectAsStateWithLifecycle()

    // Dictado por micrófono
    val speechHelper = remember {
        SpeechDictationHelper(
            context = context,
            onResultText = { dictated ->
                inputText = if (inputText.isBlank()) dictated else "$inputText $dictated"
            },
            onErrorMessage = { err ->
                scope.launch { snackbarHostState.showSnackbar(err) }
            }
        )
    }
    val isListening by speechHelper.isListening.collectAsStateWithLifecycle()
    val partialSpeechText by speechHelper.partialText.collectAsStateWithLifecycle()
    val rmsLevel by speechHelper.rmsLevel.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
            speechHelper.stopListening()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechHelper.startListening()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permiso de micrófono denegado para dictar por voz.")
            }
        }
    }

    // Selector de fotos sin permisos (Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                AttachmentHelper.importImageFromUri(context, uri)
                    .onSuccess { chatViewModel.setPendingAttachment(it) }
                    .onFailure {
                        snackbarHostState.showSnackbar(it.message ?: "No se pudo adjuntar la foto.")
                    }
            }
        }
    }

    // Cámara
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null) {
            scope.launch {
                AttachmentHelper.importImageFromFile(context, file)
                    .onSuccess { chatViewModel.setPendingAttachment(it) }
                    .onFailure {
                        snackbarHostState.showSnackbar(it.message ?: "No se pudo guardar la fotografía.")
                    }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, file) = AttachmentHelper.createCameraUri(context)
            pendingCameraFile = file
            cameraLauncher.launch(uri)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permiso de cámara denegado.")
            }
        }
    }

    // Selector de PDF
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                AttachmentHelper.importPdfFromUri(context, uri)
                    .onSuccess { chatViewModel.setPendingAttachment(it) }
                    .onFailure {
                        snackbarHostState.showSnackbar(it.message ?: "No se pudo leer el PDF.")
                    }
            }
        }
    }

    val activeConversation = remember(conversations, activeConversationId) {
        conversations.firstOrNull { it.id == activeConversationId }
    }

    val handleAssistantComplete: (MessageEntity) -> Unit = { msg ->
        if (preferences.autoSpeakResponses) {
            ttsHelper.speak(msg.id, msg.content, preferences.speechRate)
        }
    }

    LaunchedEffect(messages.size, streamingText.length) {
        val totalItems = messages.size + if (isGenerating) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    LaunchedEffect(errorBanner) {
        errorBanner?.let {
            snackbarHostState.showSnackbar(it)
            chatViewModel.dismissError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDepDrawer(
                user = user,
                conversations = conversations,
                activeConversationId = activeConversationId,
                searchQuery = searchQuery,
                onSearchQueryChange = chatViewModel::updateSearchQuery,
                onNewChat = {
                    chatViewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    chatViewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onRenameConversation = { convo ->
                    renameTarget = convo
                    renameText = convo.title
                },
                onDeleteConversation = { convo ->
                    deleteTarget = convo
                },
                onOpenImageStudio = {
                    scope.launch { drawerState.close() }
                    onOpenImageStudio()
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = activeConversation?.title ?: stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Gemini • Firebase AI Logic",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyBlue
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Abrir menú lateral")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val nextMode = if (preferences.themeMode == ThemeMode.DARK) {
                                    ThemeMode.LIGHT
                                } else {
                                    ThemeMode.DARK
                                }
                                onToggleTheme(nextMode)
                            },
                            modifier = Modifier.testTag("toggle_theme_button")
                        ) {
                            Icon(
                                imageVector = if (preferences.themeMode == ThemeMode.LIGHT) {
                                    Icons.Rounded.DarkMode
                                } else {
                                    Icons.Rounded.LightMode
                                },
                                contentDescription = "Cambiar modo claro u oscuro"
                            )
                        }

                        IconButton(
                            onClick = { chatViewModel.startNewChat() },
                            modifier = Modifier.testTag("new_chat_top_button")
                        ) {
                            Icon(Icons.Rounded.AddComment, contentDescription = "Nuevo chat")
                        }

                        if (activeConversation != null) {
                            Box {
                                IconButton(onClick = { topMenuExpanded = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones del chat")
                                }
                                DropdownMenu(
                                    expanded = topMenuExpanded,
                                    onDismissRequest = { topMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Renombrar chat") },
                                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                        onClick = {
                                            topMenuExpanded = false
                                            renameTarget = activeConversation
                                            renameText = activeConversation.title
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Eliminar chat", color = ErrorRed) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = ErrorRed)
                                        },
                                        onClick = {
                                            topMenuExpanded = false
                                            deleteTarget = activeConversation
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                ChatComposer(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    pendingAttachment = pendingAttachment,
                    onClearAttachment = chatViewModel::clearPendingAttachment,
                    onOpenAttachmentSheet = { showAttachmentSheet = true },
                    isListening = isListening,
                    partialSpeechText = partialSpeechText,
                    rmsLevel = rmsLevel,
                    onToggleDictation = {
                        if (isListening) {
                            speechHelper.stopListening()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                speechHelper.startListening()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    isGenerating = isGenerating,
                    onSend = {
                        val outgoing = inputText
                        inputText = ""
                        chatViewModel.sendMessage(outgoing, handleAssistantComplete)
                    },
                    onStopGeneration = chatViewModel::stopGeneration
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    WelcomeHero(
                        onPromptSelected = { prompt ->
                            chatViewModel.sendMessage(prompt, handleAssistantComplete)
                        },
                        onOpenImageStudio = onOpenImageStudio,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                isSpeakingThis = speakingMessageId == msg.id,
                                onEditClick = { target ->
                                    editTarget = target
                                    editText = target.content
                                },
                                onRegenerateClick = { target ->
                                    ttsHelper.stop()
                                    chatViewModel.regenerateResponse(target, handleAssistantComplete)
                                },
                                onSpeakClick = { target ->
                                    ttsHelper.speak(target.id, target.content, preferences.speechRate)
                                },
                                onImageClick = { path -> previewImagePath = path },
                                onPdfClick = { path ->
                                    if (!AttachmentHelper.openPdfExternally(context, path)) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("No hay un lector de PDF instalado para abrir el archivo.")
                                        }
                                    }
                                }
                            )
                        }
                        if (isGenerating) {
                            item(key = "streaming_bubble") {
                                StreamingAssistantBubble(streamingText = streamingText)
                            }
                        }
                    }
                }
            }
        }
    }

    // Hoja inferior de adjuntos y funciones IA
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Adjuntar o crear con ChatDep",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))

                AttachmentOptionRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Enviar fotografía de la galería",
                    subtitle = "Selector seguro de Android para analizar imágenes con Gemini",
                    onClick = {
                        showAttachmentSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                AttachmentOptionRow(
                    icon = Icons.Rounded.CameraAlt,
                    title = "Tomar fotografía con la cámara",
                    subtitle = "Captura directa para adjuntar y analizar",
                    onClick = {
                        showAttachmentSheet = false
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val (uri, file) = AttachmentHelper.createCameraUri(context)
                            pendingCameraFile = file
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
                AttachmentOptionRow(
                    icon = Icons.Rounded.PictureAsPdf,
                    title = "Adjuntar documento PDF",
                    subtitle = "Analiza y responde preguntas sobre el PDF con Gemini",
                    onClick = {
                        showAttachmentSheet = false
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    }
                )
                AttachmentOptionRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Generar imágenes mediante IA",
                    subtitle = "Abrir Estudio de Imágenes ChatDep",
                    onClick = {
                        showAttachmentSheet = false
                        onOpenImageStudio()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Diálogo Renombrar chat
    renameTarget?.let { convo ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar conversación") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Título del chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.renameConversation(convo.id, renameText)
                        renameTarget = null
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo Eliminar chat
    deleteTarget?.let { convo ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar conversación") },
            text = { Text("¿Seguro que deseas eliminar «${convo.title}» de forma permanente?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.deleteConversation(convo.id)
                        deleteTarget = null
                    }
                ) { Text("Eliminar", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo Editar mensaje de usuario
    editTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Editar mensaje") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Tu mensaje") },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.editUserMessage(msg, editText, handleAssistantComplete)
                        editTarget = null
                    }
                ) { Text("Actualizar y responder") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("Cancelar") }
            }
        )
    }

    // Vista ampliada de fotografía o imagen generada
    previewImagePath?.let { path ->
        Dialog(onDismissRequest = { previewImagePath = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Imagen ampliada",
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

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(ElectricBlue.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = SkyBlue)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
