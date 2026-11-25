package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AttachmentType {
    NONE,
    IMAGE,
    PDF
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val salt: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId"), Index("updatedAt")]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "user" o "assistant"
    val content: String,
    val attachmentType: String = AttachmentType.NONE.name,
    val attachmentPath: String? = null,
    val attachmentPreviewPath: String? = null,
    val attachmentName: String? = null,
    val attachmentMeta: String? = null,
    val isPlaceholderNotice: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "image_requests",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class ImageRequestEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val prompt: String,
    val aspectRatio: String,
    val style: String,
    val statusNotice: String,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
