package mab.roh.biometricprompt.data

import mab.roh.biometricprompt.crypto.EncryptedPayload
import mab.roh.biometricprompt.crypto.NoteCryptoManager

class EncryptedNoteRepository(
    private val noteDao: NoteDao,
    private val cryptoManager: NoteCryptoManager,
) {
    suspend fun loadNotes(): List<DecryptedNote> {
        return noteDao.getAll().map { entity ->
            DecryptedNote(
                id = entity.id,
                title = cryptoManager.decrypt(
                    EncryptedPayload(
                        cipherText = entity.encryptedTitle,
                        iv = entity.titleIv,
                    ),
                ),
                preview = cryptoManager.decrypt(
                    EncryptedPayload(
                        cipherText = entity.encryptedPreview,
                        iv = entity.previewIv,
                    ),
                ),
            )
        }
    }

    suspend fun addNote(title: String, preview: String) {
        val encryptedTitle = cryptoManager.encrypt(title)
        val encryptedPreview = cryptoManager.encrypt(preview)
        noteDao.insert(
            NoteEntity(
                encryptedTitle = encryptedTitle.cipherText,
                encryptedPreview = encryptedPreview.cipherText,
                titleIv = encryptedTitle.iv,
                previewIv = encryptedPreview.iv,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteNote(id: Long) {
        noteDao.deleteById(id)
    }
}

data class DecryptedNote(
    val id: Long,
    val title: String,
    val preview: String,
)
