package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDepDao {

    // Usuarios locales
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeUser(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("UPDATE users SET name = :name WHERE id = :userId")
    suspend fun updateUserName(userId: String, name: String)

    // Conversaciones
    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeConversations(userId: String): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT DISTINCT c.* FROM conversations c
        LEFT JOIN messages m ON c.id = m.conversationId
        WHERE c.userId = :userId
          AND (c.title LIKE '%' || :query || '%' OR m.content LIKE '%' || :query || '%')
        ORDER BY c.updatedAt DESC
        """
    )
    fun searchConversations(userId: String, query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun renameConversation(conversationId: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun touchConversation(conversationId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM conversations WHERE userId = :userId")
    suspend fun deleteAllConversations(userId: String)

    // Mensajes
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :newContent WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: String, newContent: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :fromTimestamp")
    suspend fun deleteMessagesFrom(conversationId: String, fromTimestamp: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt > :afterTimestamp")
    suspend fun deleteMessagesAfter(conversationId: String, afterTimestamp: Long)

    // Estadísticas
    @Query("SELECT COUNT(*) FROM conversations WHERE userId = :userId")
    fun observeConversationCount(userId: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM messages m
        INNER JOIN conversations c ON m.conversationId = c.id
        WHERE c.userId = :userId
        """
    )
    fun observeMessageCount(userId: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM messages m
        INNER JOIN conversations c ON m.conversationId = c.id
        WHERE c.userId = :userId AND m.attachmentType != 'NONE'
        """
    )
    fun observeAttachmentCount(userId: String): Flow<Int>

    // Solicitudes de imagen
    @Query("SELECT * FROM image_requests WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeImageRequests(userId: String): Flow<List<ImageRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImageRequest(request: ImageRequestEntity)

    @Query("DELETE FROM image_requests WHERE id = :requestId")
    suspend fun deleteImageRequest(requestId: String)
}
