package mab.roh.biometricprompt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val encryptedTitle: String,
    val encryptedPreview: String,
    val titleIv: String,
    val previewIv: String,
    val createdAt: Long,
)
