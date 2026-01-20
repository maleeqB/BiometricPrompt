package mab.roh.biometricprompt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mab.roh.biometricprompt.crypto.NoteCryptoManager
import mab.roh.biometricprompt.data.AppDatabase
import mab.roh.biometricprompt.data.DecryptedNote
import mab.roh.biometricprompt.data.EncryptedNoteRepository

enum class ScreenMode {
    LIST,
    EDITOR,
}

data class SecureNotesUiState(
    val screenMode: ScreenMode = ScreenMode.LIST,
    val vaultMessage: String = "Vault ready.",
    val notes: List<DecryptedNote> = emptyList(),
    val isLoadingNotes: Boolean = false,
    val editingId: Long? = null,
    val editorTitle: String = "",
    val editorDescription: String = "",
)

class SecureNotesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository by lazy {
        val db = Room.databaseBuilder(application, AppDatabase::class.java, "secure_notes.db").build()
        EncryptedNoteRepository(db.noteDao(), NoteCryptoManager())
    }

    private val _uiState = MutableStateFlow(SecureNotesUiState())
    val uiState: StateFlow<SecureNotesUiState> = _uiState

    fun onUnlocked() {
        refreshNotes()
    }

    fun onLocked() {
        _uiState.update {
            it.copy(screenMode = ScreenMode.LIST, editingId = null, editorTitle = "", editorDescription = "")
        }
    }

    fun refreshNotes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingNotes = true) }
            val result = runCatching { withContext(Dispatchers.IO) { repository.loadNotes() } }
            result.onSuccess { loaded ->
                _uiState.update {
                    it.copy(
                        notes = loaded,
                        isLoadingNotes = false,
                        vaultMessage = if (loaded.isEmpty()) "No notes yet. Tap Add Note." else "${loaded.size} note(s) loaded.",
                    )
                }
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingNotes = false,
                        vaultMessage = "Failed to load notes: ${error.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun startCreateNote() {
        _uiState.update {
            it.copy(screenMode = ScreenMode.EDITOR, editingId = null, editorTitle = "", editorDescription = "")
        }
    }

    fun startEditNote(noteId: Long) {
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repository.loadNoteById(noteId) } }
            result.onSuccess { note ->
                if (note == null) {
                    _uiState.update { it.copy(vaultMessage = "Could not find note.") }
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        screenMode = ScreenMode.EDITOR,
                        editingId = note.id,
                        editorTitle = note.title,
                        editorDescription = note.preview,
                    )
                }
            }
            result.onFailure { error ->
                _uiState.update { it.copy(vaultMessage = "Failed to open note: ${error.message ?: "unknown error"}") }
            }
        }
    }

    fun updateEditorTitle(value: String) {
        _uiState.update { it.copy(editorTitle = value) }
    }

    fun updateEditorDescription(value: String) {
        _uiState.update { it.copy(editorDescription = value) }
    }

    fun cancelEditor() {
        _uiState.update { it.copy(screenMode = ScreenMode.LIST) }
    }

    fun saveEditor() {
        val snapshot = _uiState.value
        val title = snapshot.editorTitle.trim()
        val description = snapshot.editorDescription.trim()
        if (title.isEmpty()) {
            _uiState.update { it.copy(vaultMessage = "Title is required.") }
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (snapshot.editingId == null) {
                        repository.addNote(title = title, preview = description)
                    } else {
                        repository.updateNote(id = snapshot.editingId, title = title, preview = description)
                    }
                }
            }
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        screenMode = ScreenMode.LIST,
                        vaultMessage = if (snapshot.editingId == null) "Note created." else "Note updated.",
                    )
                }
                refreshNotes()
            }
            result.onFailure { error ->
                _uiState.update { it.copy(vaultMessage = "Failed to save note: ${error.message ?: "unknown error"}") }
            }
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repository.deleteNote(id) } }
            result.onSuccess {
                _uiState.update { it.copy(vaultMessage = "Note deleted.") }
                refreshNotes()
            }
            result.onFailure { error ->
                _uiState.update { it.copy(vaultMessage = "Failed to delete note: ${error.message ?: "unknown error"}") }
            }
        }
    }
}
