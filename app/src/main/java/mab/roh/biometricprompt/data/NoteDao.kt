package mab.roh.biometricprompt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity)

    @Query(
        """
        UPDATE notes
        SET encryptedTitle = :encryptedTitle,
            encryptedPreview = :encryptedPreview,
            titleIv = :titleIv,
            previewIv = :previewIv
        WHERE id = :id
        """
    )
    suspend fun updateEncrypted(
        id: Long,
        encryptedTitle: String,
        encryptedPreview: String,
        titleIv: String,
        previewIv: String,
    )

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
