package mab.roh.biometricprompt

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import mab.roh.biometricprompt.data.DecryptedNote
import mab.roh.biometricprompt.ui.theme.BiometricPromptTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BiometricPromptTheme {
                SecureNotesScreen(activity = this)
            }
        }
    }
}

private enum class LockState {
    LOCKED,
    UNLOCKED,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SecureNotesScreen(
    activity: FragmentActivity,
    viewModel: SecureNotesViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val biometricManager = remember(activity) { BiometricManager.from(activity) }
    val uiState by viewModel.uiState.collectAsState()

    var lockState by rememberSaveable { mutableStateOf(LockState.LOCKED) }
    var authMessage by rememberSaveable { mutableStateOf("Unlock to view your notes.") }
    var wasBackgroundedAt by remember { mutableLongStateOf(-1L) }

    val onAuthenticationSuccess by rememberUpdatedState {
        lockState = LockState.UNLOCKED
        authMessage = "Unlocked successfully."
        viewModel.onUnlocked()
    }
    val onAuthenticationError by rememberUpdatedState { error: String ->
        authMessage = error
    }

    val biometricPrompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthenticationSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onAuthenticationError(errString.toString())
                }
            },
        )
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Secure Notes")
            .setSubtitle("Authenticate with fingerprint or device credential")
            .setAllowedAuthenticators(authenticators)
            .build()
    }

    val requestUnlock: () -> Unit = {
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> authMessage = "No biometric hardware found on this device."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> authMessage = "Biometric hardware is currently unavailable."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> authMessage = "No biometric credential enrolled."
            else -> authMessage = "Biometric authentication is not available right now."
        }
    }

    LaunchedEffect(Unit) {
        requestUnlock()
    }

    DisposableEffect(lifecycleOwner, lockState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (lockState == LockState.UNLOCKED) {
                        wasBackgroundedAt = SystemClock.elapsedRealtime()
                    }
                }

                Lifecycle.Event.ON_START -> {
                    if (lockState == LockState.UNLOCKED && wasBackgroundedAt > 0L) {
                        lockState = LockState.LOCKED
                        authMessage = "Session locked on app return."
                        wasBackgroundedAt = -1L
                        viewModel.onLocked()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.screenMode == ScreenMode.EDITOR) "Edit Note" else "Secure Notes") },
                navigationIcon = {
                    if (lockState == LockState.UNLOCKED && uiState.screenMode == ScreenMode.EDITOR) {
                        IconButton(onClick = viewModel::cancelEditor) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (lockState == LockState.UNLOCKED) {
                        TextButton(onClick = {
                            lockState = LockState.LOCKED
                            viewModel.onLocked()
                        }) {
                            Text("Lock")
                        }
                    } else {
                        TextButton(onClick = requestUnlock) {
                            Text("Unlock")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (lockState == LockState.LOCKED) {
            LockedContent(
                authMessage = authMessage,
                onUnlockClick = requestUnlock,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
            return@Scaffold
        }

        if (uiState.screenMode == ScreenMode.EDITOR) {
            NoteEditorContent(
                title = uiState.editorTitle,
                description = uiState.editorDescription,
                onTitleChange = viewModel::updateEditorTitle,
                onDescriptionChange = viewModel::updateEditorDescription,
                onCancel = viewModel::cancelEditor,
                onSave = viewModel::saveEditor,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        } else {
            UnlockedNotesContent(
                notes = uiState.notes,
                vaultMessage = uiState.vaultMessage,
                isLoading = uiState.isLoadingNotes,
                onAddNote = viewModel::startCreateNote,
                onEditNote = viewModel::startEditNote,
                onDeleteNote = viewModel::deleteNote,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LockedContent(
    authMessage: String,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your notes are locked",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = authMessage,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onUnlockClick) {
            Icon(imageVector = Icons.Rounded.Fingerprint, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Authenticate")
        }
    }
}

@Composable
private fun NoteEditorContent(
    title: String,
    description: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Simple and clean note editor", style = MaterialTheme.typography.titleSmall)
                Text("Enter title and description, then save.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSave) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun UnlockedNotesContent(
    notes: List<DecryptedNote>,
    vaultMessage: String,
    isLoading: Boolean,
    onAddNote: () -> Unit,
    onEditNote: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Vault unlocked",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = vaultMessage,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent Notes",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onAddNote) {
                Text("Add Note")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading -> {
                Text(
                    text = "Loading encrypted notes...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            notes.isEmpty() -> {
                Text(
                    text = "No notes yet. Tap Add Note to create your first encrypted note.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Card {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(note.title, style = MaterialTheme.typography.titleSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { onEditNote(note.id) }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit")
                                        }
                                        TextButton(onClick = { onDeleteNote(note.id) }) {
                                            Text("Delete")
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(note.preview, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
