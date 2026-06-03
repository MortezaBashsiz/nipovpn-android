package net.sudoer.nipovpn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import net.sudoer.nipovpn.ui.theme.NipoVPNTheme
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NipoVPNTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NipoVpnScreen(
                        context = this,
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

data class NipoConfig(
    var token: String = "af445adb-2434-4975-9445-2c1b2231",
    var fakeUrls: String = "nipo.ciron.net\nwww.google.com\nsudoer.net\nsudoer.ir",
    var methods: String = "GET\nPOST\nPUT\nDELETE",
    var endPoints: String = "api\nlogin\nuser\nupdate",
    var timeout: String = "10",
    var pullTimeout: String = "50",
    var tunnelEnable: Boolean = true,
    var connectionReuse: Boolean = false,
    var tlsEnable: Boolean = true,
    var tlsVerifyPeer: Boolean = false,
    var logLevel: String = "DEBUG",
    var threads: String = "8",
    var listenIp: String = "0.0.0.0",
    var listenPort: String = "8080",
    var serverIp: String = "46.225.50.122",
    var serverPort: String = "443",
    var httpVersion: String = "1.1",
    var userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"
)

@Composable
fun NipoVpnScreen(
    context: Context,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var profiles by remember { mutableStateOf(loadProfiles(context)) }

    if (profiles.isEmpty()) {
        profiles = mutableListOf(
            NipoProfile(
                name = "Default",
                enabled = true,
                config = NipoConfig()
            )
        )
        saveProfiles(context, profiles)
    }

    var selectedProfileId by remember {
        mutableStateOf(
            profiles.firstOrNull { it.enabled }?.id ?: profiles.first().id
        )
    }

    var importDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    val selectedProfile = profiles.first { it.id == selectedProfileId }
    var cfg by remember(selectedProfileId) {
        mutableStateOf(selectedProfile.config)
    }

    val logs by LogManager.logs.collectAsState()
    val logScroll = rememberScrollState()

    LaunchedEffect(logs) {
        logScroll.animateScrollTo(logScroll.maxValue)
    }

    fun persistSelectedConfig() {
        profiles = profiles.map {
            if (it.id == selectedProfileId) it.copy(config = cfg) else it
        }.toMutableList()

        saveProfiles(context, profiles)
    }

    if (importDialog) {
        AlertDialog(
            onDismissRequest = { importDialog = false },
            title = { Text("Import NipoVPN Profile") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("nipovpn://...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val profile = importProfile(importText)

                            profiles = (profiles + profile).toMutableList()
                            saveProfiles(context, profiles)

                            selectedProfileId = profile.id
                            cfg = profile.config

                            LogManager.append("Imported profile: ${profile.name}")
                            importDialog = false
                            importText = ""
                        } catch (e: Exception) {
                            LogManager.append("Import failed: ${e.message}")
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { importDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AppHeader()

        Spacer(Modifier.height(16.dp))

        SectionCard("Control") {
            Text("Active Profile: ${profiles.firstOrNull { it.enabled }?.name ?: "None"}")

            Spacer(Modifier.height(12.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    persistSelectedConfig()

                    profiles = profiles.map {
                        it.copy(enabled = it.id == selectedProfileId)
                    }.toMutableList()

                    saveProfiles(context, profiles)
                    generateConfigFile(context, cfg)

                    onStart()
                }
            ) {
                Text("▶ Save Profile and Start")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStop
            ) {
                Text("■ Stop")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { LogManager.clear() }
            ) {
                Text("🗑 Clear Logs")
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Profiles") {
            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    selected = profile.id == selectedProfileId,
                    onSelect = {
                        persistSelectedConfig()
                        selectedProfileId = profile.id
                    },
                    onEnable = {
                        persistSelectedConfig()

                        profiles = profiles.map {
                            it.copy(enabled = it.id == profile.id)
                        }.toMutableList()

                        selectedProfileId = profile.id
                        cfg = profile.config

                        saveProfiles(context, profiles)
                    },
                    onDelete = {
                        if (profiles.size > 1) {
                            profiles = profiles
                                .filter { it.id != profile.id }
                                .toMutableList()

                            if (selectedProfileId == profile.id) {
                                selectedProfileId = profiles.first().id
                                cfg = profiles.first().config
                            }

                            if (profiles.none { it.enabled }) {
                                profiles = profiles.mapIndexed { index, item ->
                                    item.copy(enabled = index == 0)
                                }.toMutableList()
                            }

                            saveProfiles(context, profiles)
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    persistSelectedConfig()

                    val newProfile = NipoProfile(
                        id = UUID.randomUUID().toString(),
                        name = "Profile ${profiles.size + 1}",
                        enabled = false,
                        config = NipoConfig()
                    )

                    profiles = (profiles + newProfile).toMutableList()
                    selectedProfileId = newProfile.id
                    cfg = newProfile.config

                    saveProfiles(context, profiles)
                }
            ) {
                Text("+ Add Profile")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    importDialog = true
                }
            ) {
                Text("Import nipovpn://")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    persistSelectedConfig()
                    val link = exportProfile(selectedProfile.copy(config = cfg))
                    LogManager.append("Export:")
                    LogManager.append(link)
                }
            ) {
                Text("Export Selected Profile to Logs")
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Selected Profile") {
            ConfigTextField("Profile Name", selectedProfile.name) { name ->
                profiles = profiles.map {
                    if (it.id == selectedProfileId) it.copy(name = name) else it
                }.toMutableList()
                saveProfiles(context, profiles)
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("General") {
            ConfigTextField("Token", cfg.token) { cfg = cfg.copy(token = it) }
            ConfigTextField("Timeout", cfg.timeout) { cfg = cfg.copy(timeout = it) }
            ConfigTextField("Pull Timeout", cfg.pullTimeout) { cfg = cfg.copy(pullTimeout = it) }

            ConfigSwitch("Tunnel Enable", cfg.tunnelEnable) { cfg = cfg.copy(tunnelEnable = it) }
            ConfigSwitch("Connection Reuse", cfg.connectionReuse) { cfg = cfg.copy(connectionReuse = it) }
            ConfigSwitch("TLS Enable", cfg.tlsEnable) { cfg = cfg.copy(tlsEnable = it) }
            ConfigSwitch("TLS Verify Peer", cfg.tlsVerifyPeer) { cfg = cfg.copy(tlsVerifyPeer = it) }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Agent") {
            ConfigTextField("Threads", cfg.threads) { cfg = cfg.copy(threads = it) }
            ConfigTextField("Listen IP", cfg.listenIp) { cfg = cfg.copy(listenIp = it) }
            ConfigTextField("Listen Port", cfg.listenPort) { cfg = cfg.copy(listenPort = it) }
            ConfigTextField("Server IP", cfg.serverIp) { cfg = cfg.copy(serverIp = it) }
            ConfigTextField("Server Port", cfg.serverPort) { cfg = cfg.copy(serverPort = it) }
            ConfigTextField("HTTP Version", cfg.httpVersion) { cfg = cfg.copy(httpVersion = it) }
            MultiTextField("User Agent", cfg.userAgent) { cfg = cfg.copy(userAgent = it) }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Rotation Lists") {
            MultiTextField("Fake URLs", cfg.fakeUrls) { cfg = cfg.copy(fakeUrls = it) }
            MultiTextField("Methods", cfg.methods) { cfg = cfg.copy(methods = it) }
            MultiTextField("End Points", cfg.endPoints) { cfg = cfg.copy(endPoints = it) }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Logs") {
            ConfigTextField("Log Level", cfg.logLevel) { cfg = cfg.copy(logLevel = it) }

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
fun ProfileRow(
    profile: NipoProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEnable: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor =
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (profile.enabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEnable) {
                    Text("Enable")
                }

                OutlinedButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
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
            Text("🔐", style = MaterialTheme.typography.headlineMedium)

            Column {
                Text("NipoVPN", style = MaterialTheme.typography.headlineMedium)
                Text("Android agent controller", style = MaterialTheme.typography.bodyMedium)
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

fun yamlList(text: String): String {
    return text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n") { "    - $it" }
}

fun generateConfigFile(context: Context, cfg: NipoConfig): File {
    val logDir = File(context.filesDir, "logs")
    logDir.mkdirs()

    val logFile = File(logDir, "nipovpn.log")
    val configFile = File(context.filesDir, "config.yaml")

    val yaml = """
---
general:
  token: "${cfg.token}"
  fakeUrls:
${yamlList(cfg.fakeUrls)}
  methods:
${yamlList(cfg.methods)}
  endPoints:
${yamlList(cfg.endPoints)}
  timeout: ${cfg.timeout}
  pullTimeout: ${cfg.pullTimeout}
  tunnelEnable: ${cfg.tunnelEnable}
  connectionReuse: ${cfg.connectionReuse}
  tlsEnable: ${cfg.tlsEnable}
  tlsVerifyPeer: ${cfg.tlsVerifyPeer}
  tlsCertFile: ""
  tlsKeyFile: ""
  tlsCaFile: ""

log:
  logLevel: "${cfg.logLevel}"
  logFile: "${logFile.absolutePath}"

server:
  threads: 8
  listenIp: "0.0.0.0"
  listenPort: 80

agent:
  threads: ${cfg.threads}
  listenIp: "${cfg.listenIp}"
  listenPort: ${cfg.listenPort}
  serverIp: "${cfg.serverIp}"
  serverPort: ${cfg.serverPort}
  httpVersion: "${cfg.httpVersion}"
  userAgent: "${cfg.userAgent}"
""".trimIndent()

    configFile.writeText(yaml)
    return configFile
}

fun exportProfile(profile: NipoProfile): String {
    val cfg = profile.config

    val cfgObj = JSONObject()
        .put("token", cfg.token)
        .put("fakeUrls", cfg.fakeUrls)
        .put("methods", cfg.methods)
        .put("endPoints", cfg.endPoints)
        .put("timeout", cfg.timeout)
        .put("pullTimeout", cfg.pullTimeout)
        .put("tunnelEnable", cfg.tunnelEnable)
        .put("connectionReuse", cfg.connectionReuse)
        .put("tlsEnable", cfg.tlsEnable)
        .put("tlsVerifyPeer", cfg.tlsVerifyPeer)
        .put("logLevel", cfg.logLevel)
        .put("threads", cfg.threads)
        .put("listenIp", cfg.listenIp)
        .put("listenPort", cfg.listenPort)
        .put("serverIp", cfg.serverIp)
        .put("serverPort", cfg.serverPort)
        .put("httpVersion", cfg.httpVersion)
        .put("userAgent", cfg.userAgent)

    val obj = JSONObject()
        .put("name", profile.name)
        .put("config", cfgObj)

    val encoded = Base64.encodeToString(
        obj.toString().toByteArray(),
        Base64.NO_WRAP
    )

    return "nipovpn://$encoded"
}