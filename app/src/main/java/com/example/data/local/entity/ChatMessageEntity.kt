package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId", "timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "DONE", // "SENDING", "DONE", "ERROR"
    val errorMessage: String? = null,
    /**
     * Lampiran pesan dalam bentuk JSON (lihat AttachmentCodec).
     * HARUS nullable tanpa default SQL, agar cocok dengan ALTER TABLE di MIGRATION_1_2.
     * Pesan assistant tidak pernah punya lampiran.
     */
    val attachmentsJson: String? = null,
    /** Nama profil yang benar-benar menjawab pesan ini (diisi bila lewat fallback). */
    val servedBy: String? = null
)
