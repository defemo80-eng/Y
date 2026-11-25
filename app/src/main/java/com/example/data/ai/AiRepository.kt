package com.example.data.ai

import android.content.Context
import android.util.Log
import com.example.data.local.AttachmentType
import com.example.data.local.MessageEntity
import com.example.data.media.AttachmentHelper
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

data class AttachmentPayload(
    val type: AttachmentType,
    val localPath: String,
    val fileName: String,
    val metadataSummary: String
)

sealed interface AiStreamEvent {
    data class Token(val accumulatedText: String) : AiStreamEvent
    data class Completed(val finalText: String) : AiStreamEvent
}

data class AiImageResult(
    val imagePath: String,
    val message: String
)

interface AiRepository {
    fun streamChatResponse(
        priorHistory: List<MessageEntity>,
        userPrompt: String,
        attachment: AttachmentPayload?
    ): Flow<AiStreamEvent>

    suspend fun generateImage(
        prompt: String,
        aspectRatio: String,
        style: String
    ): AiImageResult
}

/**
 * Implementación real de IA conectada a Google Gemini mediante Firebase AI Logic (`Firebase.ai`).
 * No almacena claves privadas en el cliente: utiliza la atestación de Firebase App Check.
 */
class FirebaseAiRepository(
    private val context: Context,
    private val rateLimiter: RateLimiter = RateLimiter()
) : AiRepository {

    private val appContext = context.applicationContext

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_CHAT,
            systemInstruction = content {
                text(
                    "Eres ChatDep («Tu IA Inteligente, Siempre Contigo»), un asistente conversacional " +
                        "amable, preciso y natural. Responde siempre en español salvo que el usuario " +
                        "pida explícitamente otro idioma. Usa Markdown claro y bloques de código cuando " +
                        "ayuden. Cuando el usuario adjunte imágenes o documentos PDF, analiza su " +
                        "contenido real con rigor y sin inventar detalles que no aparezcan en el archivo."
                )
            }
        )
    }

    private val imageModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
        Firebase.ai.generativeModel(
            modelName = MODEL_IMAGE,
            generationConfig = config
        )
    }

    override fun streamChatResponse(
        priorHistory: List<MessageEntity>,
        userPrompt: String,
        attachment: AttachmentPayload?
    ): Flow<AiStreamEvent> = flow {
        val cleanPrompt = userPrompt.trim()
        require(cleanPrompt.length <= MAX_PROMPT_CHARS) {
            "Tu mensaje supera el límite de $MAX_PROMPT_CHARS caracteres."
        }
        if (!NetworkChecker.isOnline(appContext)) {
            throw IllegalStateException(
                "Sin conexión a internet. Conéctate a Wi-Fi o datos móviles para consultar a Gemini."
            )
        }
        rateLimiter.checkAndRecord().getOrThrow()

        val incomingContent = buildOutgoingContent(cleanPrompt, attachment)
        val sdkHistory = buildSanitizedHistory(priorHistory)

        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                val chat = chatModel.startChat(history = sdkHistory)
                chat.sendMessageStream(incomingContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiStreamEvent.Token(accumulated.toString()))
                    }
                }
            }
            val finalOutput = accumulated.toString().trim()
            if (finalOutput.isBlank()) {
                throw IllegalStateException("Gemini no devolvió contenido para esta consulta.")
            }
            emit(AiStreamEvent.Completed(finalOutput))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out", e)
            throw IllegalStateException("La respuesta de Gemini superó el tiempo límite (60 s). Intenta nuevamente.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Gemini quota exceeded", e)
            throw IllegalStateException("Se alcanzó el límite de cuota de Gemini. Intenta de nuevo más tarde.")
        } catch (e: Exception) {
            Log.e(TAG, "Gemini streamChatResponse failed", e)
            throw IllegalStateException(formatReadableError(e))
        }
    }

    override suspend fun generateImage(
        prompt: String,
        aspectRatio: String,
        style: String
    ): AiImageResult {
        val cleanPrompt = prompt.trim()
        require(cleanPrompt.isNotEmpty()) { "Escribe una descripción para generar la imagen." }
        require(cleanPrompt.length <= 2_000) { "La descripción de la imagen es demasiado larga." }
        if (!NetworkChecker.isOnline(appContext)) {
            throw IllegalStateException(
                "Sin conexión a internet. Conéctate para generar imágenes con Gemini."
            )
        }
        rateLimiter.checkAndRecord().getOrThrow()

        val enrichedPrompt = "$cleanPrompt. Estilo visual: $style. Relación de aspecto sugerida: $aspectRatio."
        try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                imageModel.generateContent(enrichedPrompt)
            }
            val bitmap = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
                ?: throw IllegalStateException(
                    "El modelo no devolvió ninguna imagen para esa descripción."
                )

            val savedPath = AttachmentHelper.saveGeneratedBitmap(appContext, bitmap).getOrThrow()
            return AiImageResult(
                imagePath = savedPath,
                message = "Imagen generada con Gemini ($style • $aspectRatio)."
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            throw IllegalStateException("La generación de imagen superó los 60 segundos. Intenta nuevamente.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Image generation quota exceeded", e)
            throw IllegalStateException(
                "Cuota de generación de imágenes agotada o plan Firebase Blaze requerido para este modelo."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            throw IllegalStateException(formatReadableError(e))
        }
    }

    private suspend fun buildOutgoingContent(
        prompt: String,
        attachment: AttachmentPayload?
    ): Content {
        if (attachment == null) {
            return content { text(prompt) }
        }
        return when (attachment.type) {
            AttachmentType.IMAGE -> {
                val bitmap = AttachmentHelper.loadScaledBitmap(attachment.localPath)
                    ?: throw IllegalStateException("No se pudo cargar la fotografía adjunta para enviarla a Gemini.")
                content {
                    image(bitmap)
                    text(prompt.ifBlank { "Analiza detalladamente esta imagen en español." })
                }
            }
            AttachmentType.PDF -> {
                val pdfBytes = AttachmentHelper.readPdfBytes(attachment.localPath).getOrThrow()
                content {
                    inlineData(pdfBytes, "application/pdf")
                    text(prompt.ifBlank { "Resume y analiza el contenido principal de este documento PDF en español." })
                }
            }
            AttachmentType.NONE -> content { text(prompt) }
        }
    }

    /**
     * Construye un historial válido alternando turnos `user` y `model`
     * para que Gemini mantenga el contexto completo de la conversación.
     */
    private fun buildSanitizedHistory(priorHistory: List<MessageEntity>): List<Content> {
        val recent = priorHistory
            .filter { it.content.isNotBlank() }
            .takeLast(MAX_HISTORY_TURNS)

        val result = mutableListOf<Content>()
        for (msg in recent) {
            val sdkRole = if (msg.role == "user") "user" else "model"
            if (result.isEmpty() && sdkRole != "user") continue
            val lastRole = result.lastOrNull()?.role
            if (lastRole == sdkRole) continue
            result.add(
                content(role = sdkRole) {
                    text(msg.content)
                }
            )
        }
        // El historial previo pasado a startChat debe terminar en turno "model"
        if (result.lastOrNull()?.role == "user") {
            result.removeAt(result.lastIndex)
        }
        return result
    }

    private fun formatReadableError(e: Exception): String {
        val raw = e.localizedMessage.orEmpty()
        return when {
            raw.contains("403") || raw.contains("AppCheck", ignoreCase = true) ->
                "Error de verificación App Check (403). Verifica la configuración de Firebase App Check del proyecto."
            raw.contains("network", ignoreCase = true) || raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Error de conexión con los servidores de Gemini. Revisa tu acceso a internet."
            raw.contains("billing", ignoreCase = true) || raw.contains("Blaze", ignoreCase = true) ->
                "Esta función requiere el plan Blaze en Firebase para su uso en producción ($raw)."
            raw.isNotBlank() -> "Error de Gemini: $raw"
            else -> "No se pudo obtener respuesta de Gemini. Inténtalo de nuevo."
        }
    }

    companion object {
        private const val TAG = "FirebaseAiRepository"
        private const val MODEL_CHAT = "gemini-3.8-flash"
        private const val MODEL_IMAGE = "gemini-nano-banana-2.1"
        private const val AI_TIMEOUT_MS = 60_000L
        private const val MAX_PROMPT_CHARS = 12_000
        private const val MAX_HISTORY_TURNS = 24
    }
}
