package com.example.data.repository

import com.example.data.local.ChatDepDao
import com.example.data.local.ConversationEntity
import com.example.data.local.ImageRequestEntity
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class ChatDepRepository(
    private val dao: ChatDepDao,
    private val prefs: UserPreferencesRepository
) {
    // --- Autenticación Local ---
    fun observeUser(userId: String): Flow<UserEntity?> = dao.observeUser(userId)

    suspend fun registerUser(name: String, email: String, password: String): Result<UserEntity> {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Ingresa un nombre de al menos 2 caracteres."))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Ingresa un correo electrónico válido."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("La contraseña debe tener al menos 6 caracteres."))
        }

        val existing = dao.getUserByEmail(cleanEmail)
        if (existing != null) {
            return Result.failure(IllegalStateException("Ya existe una cuenta registrada con ese correo."))
        }

        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            email = cleanEmail,
            passwordHash = hash,
            salt = salt
        )
        dao.insertUser(user)
        prefs.setActiveUserId(user.id)
        return Result.success(user)
    }

    suspend fun loginUser(email: String, password: String): Result<UserEntity> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Completa tu correo y contraseña."))
        }
        val user = dao.getUserByEmail(cleanEmail)
            ?: return Result.failure(IllegalStateException("No encontramos una cuenta con ese correo."))

        val computed = hashPassword(password, user.salt)
        if (computed != user.passwordHash) {
            return Result.failure(IllegalArgumentException("La contraseña no es correcta."))
        }
        prefs.setActiveUserId(user.id)
        return Result.success(user)
    }

    suspend fun continueWithQuickProfile(): Result<UserEntity> {
        val guestEmail = "invitado@chatdep.local"
        val existing = dao.getUserByEmail(guestEmail)
        if (existing != null) {
            prefs.setActiveUserId(existing.id)
            return Result.success(existing)
        }
        val user = UserEntity(
            id = "local_guest_${UUID.randomUUID()}",
            name = "Explorador ChatDep",
            email = guestEmail,
            passwordHash = "",
            salt = ""
        )
        dao.insertUser(user)
        prefs.setActiveUserId(user.id)
        return Result.success(user)
    }

    suspend fun updateUserName(userId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.length >= 2) {
            dao.updateUserName(userId, trimmed)
        }
    }

    suspend fun signOut() {
        prefs.setActiveUserId(null)
    }

    // --- Conversaciones y Mensajes ---
    fun observeConversations(userId: String, searchQuery: String): Flow<List<ConversationEntity>> {
        return if (searchQuery.isBlank()) {
            dao.observeConversations(userId)
        } else {
            dao.searchConversations(userId, searchQuery.trim())
        }
    }

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(conversationId)

    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity> =
        dao.getMessagesOnce(conversationId)

    suspend fun createConversation(userId: String, initialTitle: String): ConversationEntity {
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = initialTitle.take(48).ifBlank { "Nueva conversación" },
            createdAt = now,
            updatedAt = now
        )
        dao.insertConversation(conversation)
        return conversation
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotEmpty()) {
            dao.renameConversation(conversationId, clean.take(60))
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        dao.deleteConversation(conversationId)
    }

    suspend fun clearAllConversations(userId: String) {
        dao.deleteAllConversations(userId)
    }

    suspend fun insertMessage(message: MessageEntity) {
        dao.insertMessage(message)
        dao.touchConversation(message.conversationId, message.createdAt)
    }

    suspend fun updateMessageContent(messageId: String, newContent: String) {
        dao.updateMessageContent(messageId, newContent)
    }

    suspend fun deleteMessagesFrom(conversationId: String, fromTimestamp: Long) {
        dao.deleteMessagesFrom(conversationId, fromTimestamp)
    }

    suspend fun deleteMessagesAfter(conversationId: String, afterTimestamp: Long) {
        dao.deleteMessagesAfter(conversationId, afterTimestamp)
    }

    // --- Estadísticas e Imágenes ---
    fun observeConversationCount(userId: String): Flow<Int> = dao.observeConversationCount(userId)
    fun observeMessageCount(userId: String): Flow<Int> = dao.observeMessageCount(userId)
    fun observeAttachmentCount(userId: String): Flow<Int> = dao.observeAttachmentCount(userId)

    fun observeImageRequests(userId: String): Flow<List<ImageRequestEntity>> =
        dao.observeImageRequests(userId)

    suspend fun saveImageRequest(request: ImageRequestEntity) {
        dao.insertImageRequest(request)
    }

    suspend fun deleteImageRequest(requestId: String) {
        dao.deleteImageRequest(requestId)
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
