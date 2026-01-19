package mab.roh.biometricprompt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mab.roh.biometricprompt.ui.theme.BiometricPromptTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BiometricPromptTheme {
                SecureNotesSampleScreen()
            }
        }
    }
}

data class NoteItem(
    val title: String,
    val preview: String,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SecureNotesSampleScreen(modifier: Modifier = Modifier) {
    val demoNotes = listOf(
        NoteItem("Interview Prep", "BiometricPrompt + Keystore integration notes"),
        NoteItem("Postgres Ideas", "Device-sync strategy for encrypted metadata"),
        NoteItem("DevOps", "Release checklist for internal testing build"),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Secure Notes") },
                actions = {
                    TextButton(onClick = { }) {
                        Text("Unlock")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxSize(),
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
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Fingerprint lock enabled",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Use biometrics or device credential to unlock notes.",
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
}
