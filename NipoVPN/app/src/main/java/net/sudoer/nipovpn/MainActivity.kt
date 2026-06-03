package net.sudoer.nipovpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import net.sudoer.nipovpn.ui.theme.NipoVPNTheme
import java.util.UUID

private val NipoDarkOrange = Color(0xFFE65100)
private val NipoDarkOrangeTrack = Color(0xFFFFB74D)
private val NipoDarkOrangeLight = Color(0xFFFFE0B2)
private val NipoOnOrange = Color.White
private val NipoGreen = Color(0xFF2E7D32)
private val NipoDarkGreen = Color(0xFF1B5E20)
private val NipoRed = Color(0xFFC62828)
private val NipoDarkRed = Color(0xFFB71C1C)

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
            context = context,
            profiles = profiles,
            onOpenProfile = { profile -> openedProfileId = profile.id },
            onProfilesChanged = { updatedProfiles ->
                profiles = updatedProfiles
                saveProfiles(context, profiles)
            },
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
            onImportProfile = { importDialog = true },
            onStart = onStart,
            onStop = onStop
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
    context: Context,
    profiles: List<NipoProfile>,
    onOpenProfile: (NipoProfile) -> Unit,
    onProfilesChanged: (List<NipoProfile>) -> Unit,
    onAddProfile: () -> Unit,
    onImportProfile: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val logs by LogManager.logs.collectAsState()
    val logScroll = rememberScrollState()

    LaunchedEffect(logs) {
        logScroll.animateScrollTo(logScroll.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SectionCardActions(
            title = "Profiles",
            actions = {
                SmallMaterialIconButton(
                    imageVector = Icons.Filled.Add,
                    contentColor = NipoDarkOrange,
                    borderColor = NipoDarkOrange,
                    onClick = onAddProfile
                )

                SmallMaterialIconButton(
                    imageVector = Icons.Filled.FileDownload,
                    contentColor = NipoDarkOrange,
                    borderColor = NipoDarkOrange,
                    onClick = onImportProfile
                )
            }
        ) {
            profiles.forEach { profile ->
                ProfileListItem(
                    profile = profile,
                    onClick = { onOpenProfile(profile) },
                    onStartStopClick = {
                        if (profile.enabled) {
                            val updatedProfiles = profiles.map { item ->
                                item.copy(enabled = false)
                            }

                            onProfilesChanged(updatedProfiles)
                            onStop()
                            LogManager.append("Stopped profile: ${profile.name}")
                        } else {
                            val updatedProfiles = profiles.map { item ->
                                if (item.id == profile.id) {
                                    item.copy(enabled = true)
                                } else {
                                    item.copy(enabled = false)
                                }
                            }

                            onProfilesChanged(updatedProfiles)
                            generateConfigFile(context, profile.config.normalized())
                            onStart()
                            LogManager.append("Started profile: ${profile.name}")
                        }
                    }
                )
            }

        }

        Spacer(Modifier.height(12.dp))

        SectionCardActions(
            title = "Logs",
            actions = {
                SmallMaterialIconButton(
                    imageVector = Icons.Filled.CleaningServices,
                    contentColor = NipoDarkOrange,
                    borderColor = NipoDarkOrange,
                    onClick = { LogManager.clear() }
                )

                SmallMaterialIconButton(
                    imageVector = Icons.Filled.ContentCopy,
                    contentColor = NipoDarkOrange,
                    borderColor = NipoDarkOrange,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "NipoVPN Logs",
                                logs.ifBlank { "No logs yet..." }
                            )
                        )

                        LogManager.append("Logs copied to clipboard")
                    }
                )
            }
        ) {
            val currentLogLevel = profiles.firstOrNull { it.enabled }
                ?.config
                ?.logLevel
                ?: profiles.firstOrNull()?.config?.logLevel
                ?: "INFO"

            LogLevelSelector(
                logLevel = currentLogLevel,
                onLogLevelChange = { value ->
                    val updatedProfiles = profiles.map { item ->
                        item.copy(
                            config = item.config.copy(logLevel = value).normalized()
                        )
                    }

                    onProfilesChanged(updatedProfiles)
                    LogManager.append("Log level changed to: $value")
                }
            )


            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
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
    var cfg by remember(profile.id) { mutableStateOf(profile.config.normalized()) }

    fun saveProfile() {
        val updatedProfiles = profiles.map { item ->
            if (item.id == profile.id) {
                item.copy(name = name, config = cfg.normalized())
            } else {
                item
            }
        }

        onProfilesChanged(updatedProfiles)
        LogManager.append("Saved profile: $name")
    }

    fun startThisProfile() {
        val finalCfg = cfg.normalized()

        val updatedProfiles = profiles.map { item ->
            if (item.id == profile.id) {
                item.copy(name = name, enabled = true, config = finalCfg)
            } else {
                item.copy(enabled = false)
            }
        }

        onProfilesChanged(updatedProfiles)
        generateConfigFile(context, finalCfg)
        onStart()
    }

    fun stopThisProfile() {
        val updatedProfiles = profiles.map { item ->
            if (item.id == profile.id) {
                item.copy(name = name, enabled = false, config = cfg.normalized())
            } else {
                item
            }
        }

        onProfilesChanged(updatedProfiles)
        onStop()
        LogManager.append("Stopped profile: $name")
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

    fun copyExportToClipboard() {
        val link = exportNipoProfileToLink(
            NipoProfile(
                id = profile.id,
                name = name,
                enabled = profile.enabled,
                config = cfg.normalized()
            )
        )

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("NipoVPN Profile", link)
        )

        LogManager.append("Profile copied to clipboard: $name")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallOutlinedButton(
                text = "← Back",
                onClick = onBack
            )

            GreenSaveButton(
                modifier = Modifier.weight(1f),
                onClick = { saveProfile() }
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Profile") {
            ConfigTextField("Profile Name", name) { value -> name = value }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfilePlayPauseButton(
                    running = profile.enabled,
                    onClick = {
                        if (profile.enabled) {
                            stopThisProfile()
                        } else {
                            startThisProfile()
                        }
                    }
                )

                SmallMaterialIconButton(
                    imageVector = Icons.Filled.ContentCopy,
                    contentColor = NipoDarkOrange,
                    borderColor = NipoDarkOrange,
                    onClick = { copyExportToClipboard() }
                )

                DeleteProfileIconButton(
                    onDeleteConfirmed = { deleteThisProfile() }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("Config") {
            Text("Connection", style = MaterialTheme.typography.titleMedium)

            ConfigTextField("Token", cfg.token) { value -> cfg = cfg.copy(token = value) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactConfigTextField(
                    label = "Timeout",
                    value = cfg.timeout,
                    modifier = Modifier.weight(1f)
                ) { value -> cfg = cfg.copy(timeout = value) }

                CompactConfigTextField(
                    label = "Pull",
                    value = cfg.pullTimeout,
                    modifier = Modifier.weight(1f)
                ) { value -> cfg = cfg.copy(pullTimeout = value) }
            }

            CompactSwitchLine {
                MiniSwitch("Tunnel", cfg.tunnelEnable) { value -> cfg = cfg.copy(tunnelEnable = value) }
                MiniSwitch("Reuse", cfg.connectionReuse) { value -> cfg = cfg.copy(connectionReuse = value) }
                MiniSwitch("TLS", cfg.tlsEnable) { value -> cfg = cfg.copy(tlsEnable = value) }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactConfigTextField(
                    label = "Listen IP",
                    value = cfg.listenIp,
                    modifier = Modifier.weight(1f)
                ) { value -> cfg = cfg.copy(listenIp = value) }

                CompactConfigTextField(
                    label = "Port",
                    value = cfg.listenPort,
                    modifier = Modifier.weight(1f)
                ) { value -> cfg = cfg.copy(listenPort = value) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactConfigTextField(
                    label = "Server IP",
                    value = cfg.serverIp,
                    modifier = Modifier.weight(1f)
                ) { value -> cfg = cfg.copy(serverIp = value) }

                CompactConfigTextField(
                    label = "Port",
                    value = cfg.serverPort,
                    modifier = Modifier.weight(1f)
                ) { value -> cfg = cfg.copy(serverPort = value) }
            }

            MultiTextField("User Agent", cfg.userAgent) { value -> cfg = cfg.copy(userAgent = value) }

            Spacer(Modifier.height(12.dp))

            MultiTextField("Fake URLs", cfg.fakeUrls) { value -> cfg = cfg.copy(fakeUrls = value) }
            MultiTextField("End Points", cfg.endPoints) { value -> cfg = cfg.copy(endPoints = value) }

            Spacer(Modifier.height(6.dp))

            MethodCheckboxes(
                methods = cfg.methods,
                onMethodsChange = { value -> cfg = cfg.copy(methods = value) }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MethodCheckboxes(
    methods: String,
    onMethodsChange: (String) -> Unit
) {
    val selected = methods.lines()
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .toSet()

    val options = listOf("GET", "POST", "PUT", "DELETE")

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { method ->
            SelectableOrangePill(
                text = method,
                selected = selected.contains(method),
                onClick = {
                    val newSet = if (selected.contains(method)) {
                        selected - method
                    } else {
                        selected + method
                    }

                    onMethodsChange(
                        options
                            .filter { newSet.contains(it) }
                            .joinToString("\n")
                    )
                }
            )
        }
    }
}

@Composable
fun LogLevelSelector(
    logLevel: String,
    onLogLevelChange: (String) -> Unit
) {
    val options = listOf("INFO", "TRACE", "DEBUG")
    val selected = logLevel.trim().uppercase().ifBlank { "INFO" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { level ->
            SelectableOrangePill(
                text = level,
                selected = selected == level,
                onClick = { onLogLevelChange(level) }
            )
        }
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
    onClick: () -> Unit,
    onStartStopClick: () -> Unit
) {
    val activeGreenContainer = NipoGreen
    val activeText = Color.White
    val inactiveText = MaterialTheme.colorScheme.onSurface
    val inactiveSubText = MaterialTheme.colorScheme.onSurfaceVariant
    val inactiveContainer = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(vertical = 3.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (profile.enabled) activeGreenContainer else inactiveContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (profile.enabled) activeText else inactiveText
                )

                Text(
                    text = "127.0.0.1:${profile.config.listenPort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profile.enabled) activeText else inactiveSubText
                )
            }

            ProfilePlayPauseButton(
                running = profile.enabled,
                onClick = onStartStopClick
            )

            Text(
                text = "›",
                style = MaterialTheme.typography.headlineSmall,
                color = if (profile.enabled) activeText else inactiveText
            )
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
fun SectionCardActions(
    title: String,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }

            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun CompactConfigTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(62.dp)
            .padding(vertical = 2.dp)
    )
}

@Composable
fun CompactSwitchLine(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
fun MiniSwitch(
    label: String,
    value: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )

        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NipoGreen,
                checkedBorderColor = NipoGreen,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun SelectableOrangePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) NipoGreen else MaterialTheme.colorScheme.surface
    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) NipoGreen else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .height(36.dp)
            .clickable { onClick() }
            .background(background, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodySmall
        )
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
            .height(62.dp)
            .padding(vertical = 2.dp)
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
            .height(108.dp)
            .padding(vertical = 3.dp)
    )
}

@Composable
fun ConfigSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
fun DeleteProfileIconButton(
    onDeleteConfirmed: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete profile?") },
            text = { Text("This profile will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        onDeleteConfirmed()
                    }
                ) {
                    Text("Delete", color = NipoRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SmallMaterialIconButton(
        imageVector = Icons.Filled.Delete,
        contentColor = NipoRed,
        borderColor = NipoRed,
        onClick = { showConfirm = true }
    )
}

@Composable
fun ProfilePlayPauseButton(
    running: Boolean,
    onClick: () -> Unit
) {
    SmallMaterialIconButton(
        imageVector = if (running) {
            Icons.Filled.Pause
        } else {
            Icons.Filled.PlayArrow
        },
        contentColor = if (running) NipoRed else NipoGreen,
        borderColor = if (running) NipoRed else NipoGreen,
        onClick = onClick
    )
}

@Composable
fun SmallMaterialIconButton(
    imageVector: ImageVector,
    contentColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .width(42.dp)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = contentColor
        )
    }
}

@Composable
fun GreenSaveButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clickable { onClick() }
            .background(NipoGreen, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Save", color = Color.White)
    }
}

@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clickable { onClick() }
            .background(NipoDarkOrange, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White)
    }
}

@Composable
fun SmallOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        modifier = modifier.height(40.dp),
        onClick = onClick
    ) {
        Text(text)
    }
}

@Composable
fun SmallTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        modifier = modifier.height(40.dp),
        onClick = onClick
    ) {
        Text(text)
    }
}

@Composable
fun SmallFullIconButton(
    text: String,
    imageVector: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onClick() }
            .background(NipoDarkOrange, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = Color.White
            )

            Text(text, color = Color.White)
        }
    }
}

@Composable
fun SmallFullButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onClick() }
            .background(NipoDarkOrange, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White)
    }
}

@Composable
fun SmallFullOutlinedButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        border = BorderStroke(1.dp, NipoDarkOrange),
        onClick = onClick
    ) {
        Text(text, color = NipoDarkOrange)
    }
}
