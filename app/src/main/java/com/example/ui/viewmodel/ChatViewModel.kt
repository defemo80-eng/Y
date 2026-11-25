package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AiRepository
import com.example.data.ai.AiStreamEvent
import com.example.data.ai.AttachmentPayload
import com.example.data.local.AttachmentType
import com.example.data.local.ConversationEntity
import com.example.data.local.ImageRequestEntity
import com.example.data.local.MessageEntity
import com.example.data.media.PendingAttachment
import com.example.data.repository.ChatDepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val userId: String,
    private val repository: ChatDepRepository,
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    private val _imageErrorBanner = MutableStateFlow<String?>(null)
    val imageErrorBanner: StateFlow<String?> = _imageErrorBanner.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = _searchQuery
        .flatMapLatest { query -> repository.observeConversations(userId, query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val messages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { convoId ->
            if (convoId == null) flowOf(emptyList())
            else repository.observeMessages(convoId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val imageRequests: StateFlow<List<ImageRequestEntity>> = repository
        .observeImageRequests(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private var generationJob: Job? = null
    private var activeGenerationConversationId: String? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectConversation(conversationId: String) {
        if (_isGenerating.value) stopGeneration()
        _activeConversationId.value = conversationId
        _errorBanner.value = null
    }

    fun startNewChat() {
        if (_isGenerating.value) stopGeneration()
        _activeConversationId.value = null
        _pendingAttachment.value = null
        _errorBanner.value = null
    }

    fun setPendingAttachment(attachment: PendingAttachment) {
        _pendingAttachment.value = attachment
    }

    fun clearPendingAttachment() {
        _pendingAttachment.value = null
    }

    fun dismissError() {
        _errorBanner.value = null
    }

    fun dismissImageError() {
        _imageErrorBanner.value = null
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(conversationId, newTitle)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                if (_isGenerating.value) stopGeneration()
                _activeConversationId.value = null
            }
            repository.deleteConversation(conversationId)
        }
    }

    fun sendMessage(
        rawText: String,
        onAssistantCompleted: ((MessageEntity) -> Unit)? = null
    ) {
        val text = rawText.trim()
        val attachment = _pendingAttachment.value
        if (text.isBlank() && attachment == null) return
        if (_isGenerating.value) return

        viewModelScope.launch {
            _errorBanner.value = null
            val convoId = _activeConversationId.value ?: run {
                val titleSeed = text.ifBlank { attachment?.fileName ?: "Nueva conversación" }
                val created = repository.createConversation(userId, titleSeed)
                _activeConversationId.value = created.id
                created.id
            }

            val priorHistory = repository.getMessagesOnce(convoId)

            val userContent = text.ifBlank {
                when (attachment?.type) {
                    AttachmentType.IMAGE -> "Analiza esta imagen adjunta."
                    AttachmentType.PDF -> "Resume y analiza este documento PDF adjunto."
                    else -> ""
                }
            }

            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convoId,
                role = "user",
                content = userContent,
                attachmentType = (attachment?.type ?: AttachmentType.NONE).name,
                attachmentPath = attachment?.localPath,
                attachmentPreviewPath = attachment?.previewPath,
                attachmentName = attachment?.fileName,
                attachmentMeta = attachment?.metadataSummary,
                createdAt = System.currentTimeMillis()
            )
            _pendingAttachment.value = null
            repository.insertMessage(userMsg)

            if (attachment == null && userContent.startsWith("/imagen", ignoreCase = true)) {
                val imgPrompt = userContent.removePrefix("/imagen").trim()
                if (imgPrompt.isBlank()) {
                    _errorBanner.value = "Escribe una descripción después de /imagen para generarla."
                    return@launch
                }
                launchInlineImageGeneration(convoId, imgPrompt)
                return@launch
            }

            val payload = attachment?.let {
                AttachmentPayload(
                    type = it.type,
                    localPath = it.localPath,
                    fileName = it.fileName,
                    metadataSummary = it.metadataSummary
                )
            }
            launchAiStream(
                conversationId = convoId,
                priorHistory = priorHistory,
                prompt = userContent,
                attachment = payload,
                onAssistantCompleted = onAssistantCompleted
            )
        }
    }

    fun regenerateResponse(
        assistantMessage: MessageEntity,
        onAssistantCompleted: ((MessageEntity) -> Unit)? = null
    ) {
        if (_isGenerating.value) return
        val convoId = _activeConversationId.value ?: return

        viewModelScope.launch {
            _errorBanner.value = null
            val allMessages = repository.getMessagesOnce(convoId)
            val targetIdx = allMessages.indexOfFirst { it.id == assistantMessage.id }
            if (targetIdx < 0) return@launch

            val userIdx = (0 until targetIdx).lastOrNull { allMessages[it].role == "user" }
                ?: return@launch
            val precedingUser = allMessages[userIdx]
            val priorHistory = allMessages.subList(0, userIdx)

            repository.deleteMessagesFrom(convoId, assistantMessage.createdAt)

            if (precedingUser.attachmentType == AttachmentType.NONE.name &&
                precedingUser.content.startsWith("/imagen", ignoreCase = true)
            ) {
                val imgPrompt = precedingUser.content.removePrefix("/imagen").trim()
                if (imgPrompt.isNotBlank()) {
                    launchInlineImageGeneration(convoId, imgPrompt)
                    return@launch
                }
            }

            val attachmentPayload = buildAttachmentPayload(precedingUser)
            launchAiStream(
                conversationId = convoId,
                priorHistory = priorHistory,
                prompt = precedingUser.content,
                attachment = attachmentPayload,
                onAssistantCompleted = onAssistantCompleted
            )
        }
    }

    fun editUserMessage(
        userMessage: MessageEntity,
        newContent: String,
        onAssistantCompleted: ((MessageEntity) -> Unit)? = null
    ) {
        val clean = newContent.trim()
        if (clean.isBlank() || _isGenerating.value) return
        val convoId = _activeConversationId.value ?: return

        viewModelScope.launch {
            _errorBanner.value = null
            val allMessages = repository.getMessagesOnce(convoId)
            val userIdx = allMessages.indexOfFirst { it.id == userMessage.id }
            val priorHistory = if (userIdx > 0) allMessages.subList(0, userIdx) else emptyList()

            repository.updateMessageContent(userMessage.id, clean)
            repository.deleteMessagesAfter(convoId, userMessage.createdAt)

            val attachmentPayload = buildAttachmentPayload(userMessage)
            launchAiStream(
                conversationId = convoId,
                priorHistory = priorHistory,
                prompt = clean,
                attachment = attachmentPayload,
                onAssistantCompleted = onAssistantCompleted
            )
        }
    }

    fun stopGeneration() {
        val partial = _streamingText.value.trim()
        val convoId = activeGenerationConversationId
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        _streamingText.value = ""

        if (partial.isNotBlank() && convoId != null) {
            viewModelScope.launch {
                val stoppedMsg = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = convoId,
                    role = "assistant",
                    content = "$partial\n\n*(Generación detenida)*",
                    isPlaceholderNotice = false,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertMessage(stoppedMsg)
            }
        }
        activeGenerationConversationId = null
    }

    private fun launchInlineImageGeneration(conversationId: String, prompt: String) {
        generationJob?.cancel()
        activeGenerationConversationId = conversationId
        _isGenerating.value = true
        _streamingText.value = "Generando imagen con Gemini…"

        generationJob = viewModelScope.launch {
            try {
                val result = aiRepository.generateImage(prompt, "1:1", "Fotorrealista")
                val assistantMsg = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = "assistant",
                    content = result.message,
                    attachmentType = AttachmentType.IMAGE.name,
                    attachmentPath = result.imagePath,
                    attachmentPreviewPath = result.imagePath,
                    attachmentName = "Imagen Gemini",
                    attachmentMeta = prompt.take(80),
                    isPlaceholderNotice = false,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertMessage(assistantMsg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Inline image generation failed", e)
                _errorBanner.value = e.localizedMessage ?: "No se pudo generar la imagen."
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
                activeGenerationConversationId = null
                generationJob = null
            }
        }
    }

    private fun launchAiStream(
        conversationId: String,
        priorHistory: List<MessageEntity>,
        prompt: String,
        attachment: AttachmentPayload?,
        onAssistantCompleted: ((MessageEntity) -> Unit)?
    ) {
        generationJob?.cancel()
        activeGenerationConversationId = conversationId
        _isGenerating.value = true
        _streamingText.value = ""

        generationJob = viewModelScope.launch {
            try {
                var finalContent = ""

                aiRepository.streamChatResponse(priorHistory, prompt, attachment).collect { event ->
                    when (event) {
                        is AiStreamEvent.Token -> {
                            _streamingText.value = event.accumulatedText
                        }
                        is AiStreamEvent.Completed -> {
                            finalContent = event.finalText
                        }
                    }
                }

                if (finalContent.isNotBlank()) {
                    val assistantMsg = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "assistant",
                        content = finalContent,
                        isPlaceholderNotice = false,
                        createdAt = System.currentTimeMillis()
                    )
                    repository.insertMessage(assistantMsg)
                    onAssistantCompleted?.invoke(assistantMsg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error streaming AI response", e)
                _errorBanner.value = e.localizedMessage ?: "No se pudo completar la respuesta de Gemini."
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
                activeGenerationConversationId = null
                generationJob = null
            }
        }
    }

    fun generateImageRequest(prompt: String, aspectRatio: String, style: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _isGeneratingImage.value) return
        viewModelScope.launch {
            _isGeneratingImage.value = true
            _imageErrorBanner.value = null
            try {
                val result = aiRepository.generateImage(clean, aspectRatio, style)
                val entity = ImageRequestEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    prompt = clean,
                    aspectRatio = aspectRatio,
                    style = style,
                    statusNotice = result.message,
                    imagePath = result.imagePath,
                    createdAt = System.currentTimeMillis()
                )
                repository.saveImageRequest(entity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image studio generation failed", e)
                _imageErrorBanner.value = e.localizedMessage ?: "Error al generar la imagen con Gemini."
            } finally {
                _isGeneratingImage.value = false
            }
        }
    }

    fun deleteImageRequest(requestId: String) {
        viewModelScope.launch {
            repository.deleteImageRequest(requestId)
        }
    }

    private fun buildAttachmentPayload(message: MessageEntity): AttachmentPayload? {
        val path = message.attachmentPath ?: return null
        return when (message.attachmentType) {
            AttachmentType.IMAGE.name -> AttachmentPayload(
                type = AttachmentType.IMAGE,
                localPath = path,
                fileName = message.attachmentName ?: "imagen.jpg",
                metadataSummary = message.attachmentMeta ?: ""
            )
            AttachmentType.PDF.name -> AttachmentPayload(
                type = AttachmentType.PDF,
                localPath = path,
                fileName = message.attachmentName ?: "documento.pdf",
                metadataSummary = message.attachmentMeta ?: ""
            )
            else -> null
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"

        fun provideFactory(
            userId: String,
            repository: ChatDepRepository,
            aiRepository: AiRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(userId, repository, aiRepository) }
        }
    }
}
