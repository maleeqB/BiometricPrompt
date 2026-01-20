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
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import mab.roh.biometricprompt.ui.theme.BiometricPromptTheme

private const val RELOCK_TIMEOUT_MS = 30_000L

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

private data class NoteItem(
    val title: String,
    val preview: String,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SecureNotesScreen(activity: FragmentActivity, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val biometricManager = remember(activity) { BiometricManager.from(activity) }

    var lockState by rememberSaveable { mutableStateOf(LockState.LOCKED) }
    var authMessage by rememberSaveable { mutableStateOf("Unlock to view your notes.") }
    var wasBackgroundedAt by remember { mutableLongStateOf(-1L) }

    val onAuthenticationSuccess by rememberUpdatedState {
        lockState = LockState.UNLOCKED
        authMessage = "Unlocked successfully."
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
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                authMessage = "No biometric hardware found on this device."

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                authMessage = "Biometric hardware is currently unavailable."

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                authMessage = "No biometric credential enrolled. Use device PIN/pattern."

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
                        val wasAwayLongEnough =
                            SystemClock.elapsedRealtime() - wasBackgroundedAt >= RELOCK_TIMEOUT_MS
                        if (wasAwayLongEnough) {
                            lockState = LockState.LOCKED
                            authMessage = "Session locked after inactivity."
                        }
                        wasBackgroundedAt = -1L
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
                title = { Text("Secure Notes") },
                actions = {
                    if (lockState == LockState.UNLOCKED) {
                        TextButton(onClick = { lockState = LockState.LOCKED }) {
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
        } else {
            UnlockedNotesContent(
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
private fun UnlockedNotesContent(modifier: Modifier = Modifier) {
    val demoNotes = listOf(
        NoteItem("Interview Prep", "BiometricPrompt + Keystore integration notes"),
        NoteItem("Postgres Ideas", "Device-sync strategy for encrypted metadata"),
        NoteItem("DevOps", "Release checklist for internal testing build"),
    )

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        text = "Relocks after 30 seconds in the background.",
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
            Button(onClick = { }) {
                Text("Add Note")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(demoNotes) { note ->
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(note.title, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(note.preview, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
