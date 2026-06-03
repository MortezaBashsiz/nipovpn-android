package net.sudoer.nipovpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import net.sudoer.nipovpn.ui.theme.NipoVPNTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val importUri = intent?.data

        setContent {
            NipoVPNTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NipoVpnApp(
                        context = this,
                        importUri = importUri,
                        onStart = {
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, NipoVpnService::class.java)
                            )
                        },
                        onStop = {
                            stopService(Intent(this, NipoVpnService::class.java))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NipoVpnApp(
    context: Context,
    importUri: Uri?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var profiles by remember {
        mutableStateOf<List<NipoProfile>>(loadProfiles(context))
    }

    if (profiles.isEmpty()) {
        profiles = listOf(
            NipoProfile(
                name = "Default",
                enabled = true,
                config = NipoConfig()
            )
        )
        saveProfiles(context, profiles)
    }

    var openedProfileId by remember { mutableStateOf<String?>(null) }
    var importDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    LaunchedEffect(importUri) {
        importUri?.toString()?.let { link ->
            try {
                val profile = importNipoProfileFromLink(link)
                profiles = profiles + profile
                saveProfiles(context, profiles)
                openedProfileId = profile.id
                LogManager.append("Imported profile: ${profile.name}")
            } catch (e: Exception) {
                LogManager.append("Import failed: ${e.message}")
            }
        }
    }

    if (importDialog) {
        ImportProfileDialog(
            importText = importText,
            onImportTextChange = { importText = it },
            onDismiss = { importDialog = false },
            onImport = {
                try {
                    val profile = importNipoProfileFromLink(importText)
                    profiles = profiles + profile
                    saveProfiles(context, profiles)
                    openedProfileId = profile.id
                    LogManager.append("Imported profile: ${profile.name}")
                    importDialog = false
                    importText = ""
                } catch (e: Exception) {
                    LogManager.append("Import failed: ${e.message}")
                }
            }
        )
    }

    val openedProfile = openedProfileId?.let { id ->
        profiles.firstOrNull { profile -> profile.id == id }
    }

    if (openedProfile == null) {
        ProfileListPage(
            profiles = profiles,
            onOpenProfile = { profile -> openedProfileId = profile.id },
            onAddProfile = {
                val newProfile = NipoProfile(
                    id = UUID.randomUUID().toString(),
                    name = "Profile ${profiles.size + 1}",
                    enabled = false,
                    config = NipoConfig()
                )

                profiles = profiles + newProfile
                saveProfiles(context, profiles)
                openedProfileId = newProfile.id
            },
            onImportProfile = { importDialog = true }
        )
    } else {
        ProfileDetailPage(
            context = context,
            profile = openedProfile,
            profiles = profiles,
            onBack = { openedProfileId = null },
            onProfilesChanged = { updatedProfiles ->
                profiles = updatedProfiles
                saveProfiles(context, profiles)

                if (profiles.none { profile -> profile.id == openedProfile.id }) {
                    openedProfileId = null
                }
            },
            onStart = onStart,
            onStop = onStop
        )
    }
}

@Composable
fun ProfileListPage(
    profiles: List<NipoProfile>,
    onOpenProfile: (NipoProfile) -> Unit,
    onAddProfile: () -> Unit,
    onImportProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AppHeader()

        Spacer(Modifier.height(16.dp))

        SectionCard("Profiles") {
            profiles.forEach { profile ->
                ProfileListItem(
                    profile = profile,
                    onClick = { onOpenProfile(profile) }
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddProfile
            ) {
                Text("+ Add Profile")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onImportProfile
            ) {
                Text("Import nipovpn://")
            }
        }
    }
}

@Composable
fun ProfileDetailPage(
    context: Context,
    profile: NipoProfile,
    profiles: List<NipoProfile>,
    onBack: () -> Unit,
    onProfilesChanged: (List<NipoProfile>) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var cfg by remember(profile.id) { mutableStateOf(profile.config) }

    val logs by LogManager.logs.collectAsState()
    val logScroll = rememberScrollState()

    LaunchedEffect(logs) {
        logScroll.animateScrollTo(logScroll.maxValue)
    }

    fun saveProfile() {
        val updatedProfiles = profiles.map { item ->
            if (item.id == profile.id) {
                item.copy(name = name, config = cfg)
            } else {
                item
            }
        }

        onProfilesChanged(updatedProfiles)
        LogManager.append("Saved profile: $name")
    }

    fun startThisProfile() {
        val updatedProfiles = profiles.map { item ->
            if (item.id == profile.id) {
                item.copy(name = name, enabled = true, config = cfg)
            } else {
                item.copy(enabled = false)
            }
        }

        onProfilesChanged(updatedProfiles)
        generateConfigFile(context, cfg)
        onStart()
    }

    fun deleteThisProfile() {
        if (profiles.size <= 1) {
            LogManager.append("You need at least one profile")
            return
        }

        val updatedProfiles = profiles
            .filter { item -> item.id != profile.id }
            .toMutableList()

        if (updatedProfiles.none { item -> item.enabled }) {
            updatedProfiles[0] = updatedProfiles[0].copy(enabled = true)
        }

        onProfilesChanged(updatedProfiles)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Text("← Back")
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = { saveProfile() }
            ) {
                Text("Save")
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Profile") {
            ConfigTextField("Profile Name", name) { value -> name = value }

            Text(
                if (profile.enabled) "Status: Active" else "Status: Disabled",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { startThisProfile() }
            ) {
                Text("▶ Save and Start")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStop
            ) {
                Text("■ Stop")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val link = exportNipoProfileToLink(
                        NipoProfile(
                            id = profile.id,
                            name = name,
                            enabled = profile.enabled,
                            config = cfg
                        )
                    )

                    LogManager.append("Export:")
                    LogManager.append(link)
                }
            ) {
                Text("Export nipovpn:// to Logs")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { deleteThisProfile() }
            ) {
                Text("Delete Profile")
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("General") {
            ConfigTextField("Token", cfg.token) { value -> cfg = cfg.copy(token = value) }
            ConfigTextField("Timeout", cfg.timeout) { value -> cfg = cfg.copy(timeout = value) }
            ConfigTextField("Pull Timeout", cfg.pullTimeout) { value -> cfg = cfg.copy(pullTimeout = value) }
            ConfigSwitch("Tunnel Enable", cfg.tunnelEnable) { value -> cfg = cfg.copy(tunnelEnable = value) }
            ConfigSwitch("Connection Reuse", cfg.connectionReuse) { value -> cfg = cfg.copy(connectionReuse = value) }
            ConfigSwitch("TLS Enable", cfg.tlsEnable) { value -> cfg = cfg.copy(tlsEnable = value) }
            ConfigSwitch("TLS Verify Peer", cfg.tlsVerifyPeer) { value -> cfg = cfg.copy(tlsVerifyPeer = value) }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Agent") {
            ConfigTextField("Threads", cfg.threads) { value -> cfg = cfg.copy(threads = value) }
            ConfigTextField("Listen IP", cfg.listenIp) { value -> cfg = cfg.copy(listenIp = value) }
            ConfigTextField("Listen Port", cfg.listenPort) { value -> cfg = cfg.copy(listenPort = value) }
            ConfigTextField("Server IP", cfg.serverIp) { value -> cfg = cfg.copy(serverIp = value) }
            ConfigTextField("Server Port", cfg.serverPort) { value -> cfg = cfg.copy(serverPort = value) }
            ConfigTextField("HTTP Version", cfg.httpVersion) { value -> cfg = cfg.copy(httpVersion = value) }
            MultiTextField("User Agent", cfg.userAgent) { value -> cfg = cfg.copy(userAgent = value) }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Rotation Lists") {
            MultiTextField("Fake URLs", cfg.fakeUrls) { value -> cfg = cfg.copy(fakeUrls = value) }
            MultiTextField("Methods", cfg.methods) { value -> cfg = cfg.copy(methods = value) }
            MultiTextField("End Points", cfg.endPoints) { value -> cfg = cfg.copy(endPoints = value) }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Logs") {
            ConfigTextField("Log Level", cfg.logLevel) { value -> cfg = cfg.copy(logLevel = value) }

            OutlinedButton(onClick = { LogManager.clear() }) {
                Text("Clear Logs")
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp)
                    .verticalScroll(logScroll)
            ) {
                Text(
                    text = logs.ifBlank { "No logs yet..." },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ImportProfileDialog(
    importText: String,
    onImportTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import NipoVPN Profile") },
        text = {
            OutlinedTextField(
                value = importText,
                onValueChange = onImportTextChange,
                label = { Text("nipovpn://...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        },
        confirmButton = {
            Button(onClick = onImport) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ProfileListItem(
    profile: NipoProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (profile.enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (profile.enabled) "🟢" else "⚪",
                style = MaterialTheme.typography.titleLarge
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (profile.enabled) "Active profile" else "Tap to configure",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun AppHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🛡️", style = MaterialTheme.typography.headlineMedium)

            Column {
                Text("NipoVPN", style = MaterialTheme.typography.headlineMedium)
                Text("Profiles", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ConfigTextField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    )
}

@Composable
fun MultiTextField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(vertical = 5.dp)
    )
}

@Composable
fun ConfigSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = value, onCheckedChange = onChange)
    }
}
