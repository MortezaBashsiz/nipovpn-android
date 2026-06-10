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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import net.sudoer.nipovpn.ui.nd.NdBackButton
import net.sudoer.nipovpn.ui.nd.NdBottomNav
import net.sudoer.nipovpn.ui.nd.NdBoxInput
import net.sudoer.nipovpn.ui.nd.NdButton
import net.sudoer.nipovpn.ui.nd.NdButtonVariant
import net.sudoer.nipovpn.ui.nd.NdChip
import net.sudoer.nipovpn.ui.nd.NdDivider
import net.sudoer.nipovpn.ui.nd.NdDot
import net.sudoer.nipovpn.ui.nd.NdDotGrid
import net.sudoer.nipovpn.ui.nd.NdIconButton
import net.sudoer.nipovpn.ui.nd.NdInput
import net.sudoer.nipovpn.ui.nd.NdLabel
import net.sudoer.nipovpn.ui.nd.NdLabelOn
import net.sudoer.nipovpn.ui.nd.NdNavItem
import net.sudoer.nipovpn.ui.nd.NdSegOption
import net.sudoer.nipovpn.ui.nd.NdSegmented
import net.sudoer.nipovpn.ui.nd.NdStatus
import net.sudoer.nipovpn.ui.nd.NdToggleBox
import net.sudoer.nipovpn.ui.nd.ndClick
import net.sudoer.nipovpn.ui.theme.NdColors
import net.sudoer.nipovpn.ui.theme.NdTheme
import net.sudoer.nipovpn.ui.theme.NothingFonts
import net.sudoer.nipovpn.ui.theme.NothingTheme
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val importUri = intent?.data

        setContent {
            NothingTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = NdTheme.colors.black) {
                    NipoVpnApp(
                        context = this,
                        importUri = importUri,
                        onStart = { startNipoVpnService() },
                        onStop = { stopService(Intent(this, NipoVpnService::class.java)) },
                    )
                }
            }
        }
    }

    private fun startNipoVpnService() {
        ContextCompat.startForegroundService(this, Intent(this, NipoVpnService::class.java))
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = (seconds / 60).toString().padStart(2, '0')
    val s = (seconds % 60).toString().padStart(2, '0')
    return "$m:$s"
}

@Composable
fun NipoVpnApp(context: Context, importUri: Uri?, onStart: () -> Unit, onStop: () -> Unit) {
    val c = NdTheme.colors
    var profiles by remember { mutableStateOf(loadProfiles(context)) }
    var tab by remember { mutableStateOf("profiles") }
    var screen by remember { mutableStateOf("list") }
    var editId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf("nipovpn://") }

    val connectionState by ConnectionStatus.state.collectAsState()
    val activeProfile = profiles.firstOrNull { it.enabled }
    val connected = connectionState.running

    fun persist(updated: List<NipoProfile>) { profiles = updated; saveProfiles(context, updated) }

    LaunchedEffect(importUri) {
        importUri?.toString()?.let { link ->
            try {
                val p = importNipoProfileFromLink(link)
                persist(profiles + p)
                LogManager.append("Imported profile: ${p.name}")
            } catch (e: Exception) {
                LogManager.append("Import failed: ${e.message}")
            }
        }
    }

    // ── connection control ──────────────────────────────────────────
    fun startProfile(target: NipoProfile) {
        persist(profiles.map { if (it.id == target.id) it.copy(enabled = true) else it.copy(enabled = false) })
        generateConfigFile(context, target.config.normalized())
        onStart()
        LogManager.append("Started profile: ${target.name}")
    }
    fun stopAll(name: String?) {
        persist(profiles.map { it.copy(enabled = false) })
        onStop()
        LogManager.append("Stopped profile: ${name ?: "active"}")
    }
    fun toggle(id: String) {
        val target = profiles.firstOrNull { it.id == id } ?: return
        if (connected && activeProfile?.id == id) stopAll(target.name) else startProfile(target)
    }

    fun addProfile() {
        val np = NipoProfile(id = UUID.randomUUID().toString(), name = "New profile", enabled = false, config = NipoConfig())
        persist(profiles + np)
        editId = np.id
        screen = "config"
    }

    // ── live telemetry ──────────────────────────────────────────────
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectionState.running) {
        while (connectionState.running) { nowMillis = System.currentTimeMillis(); delay(1_000) }
    }
    val elapsedSeconds = connectionState.startedAtMillis?.let { ((nowMillis - it) / 1000L).toInt().coerceAtLeast(0) } ?: 0

    var pingMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(activeProfile?.id, connected) {
        if (!connected || activeProfile == null) { pingMs = null; return@LaunchedEffect }
        while (true) { pingMs = pingGoogleDelayMs(activeProfile); delay(5_000) }
    }

    val logs by LogManager.logs.collectAsState()
    val editing = profiles.firstOrNull { it.id == editId }
    val showNav = screen == "list"

    Box(Modifier.fillMaxSize().background(c.black)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                when {
                    screen == "config" && editing != null -> NdConfigScreen(
                        context = context,
                        profile = editing,
                        connected = connected && editing.id == activeProfile?.id,
                        onBack = { screen = "list" },
                        onToggle = { toggle(editing.id) },
                        onSave = { updated ->
                            persist(profiles.map { if (it.id == updated.id) updated else it })
                            screen = "list"
                            LogManager.append("Saved profile: ${updated.name}")
                        },
                        onDelete = { dialog = "delete" },
                        onCopyLink = {
                            val link = exportNipoProfileToLink(editing)
                            copyToClipboard(context, "NipoVPN Profile", link)
                            LogManager.append("Profile copied: ${editing.name}")
                        },
                    )
                    tab == "profiles" && profiles.isEmpty() -> NdEmptyState(
                        onAdd = { addProfile() },
                        onImport = { dialog = "import" },
                    )
                    tab == "profiles" -> NdProfilesScreen(
                        profiles = profiles,
                        activeId = activeProfile?.id,
                        connected = connected,
                        elapsed = formatElapsed(elapsedSeconds),
                        pingMs = pingMs,
                        onToggle = { toggle(it) },
                        onOpen = { editId = it; screen = "config" },
                        onAdd = { addProfile() },
                        onImport = { dialog = "import" },
                    )
                    else -> NdLogsScreen(
                        context = context,
                        profiles = profiles,
                        logs = logs,
                        connected = connected,
                        activeName = activeProfile?.name ?: "",
                        onLevelChange = { level ->
                            persist(profiles.map { it.copy(config = it.config.copy(logLevel = level).normalized()) })
                            LogManager.append("Log level changed to: $level")
                        },
                        onClear = { LogManager.clear() },
                        onCopy = {
                            copyToClipboard(context, "NipoVPN Logs", logs.ifBlank { "No logs yet..." })
                            LogManager.append("Logs copied to clipboard")
                        },
                    )
                }
            }
            if (showNav) {
                NdDivider(c.border)
                NdBottomNav(
                    items = listOf(
                        NdNavItem("profiles", Icons.AutoMirrored.Filled.List, "Profiles"),
                        NdNavItem("logs", Icons.Filled.Terminal, "Logs"),
                    ),
                    active = tab,
                    onChange = { tab = it },
                )
            }
        }

        if (dialog == "import") {
            NdImportDialog(
                value = importText,
                onChange = { importText = it },
                onDismiss = { dialog = null },
                onConfirm = {
                    try {
                        val p = importNipoProfileFromLink(importText)
                        persist(profiles + p)
                        LogManager.append("Imported profile: ${p.name}")
                    } catch (e: Exception) {
                        LogManager.append("Import failed: ${e.message}")
                    }
                    dialog = null
                    importText = "nipovpn://"
                },
            )
        }
        if (dialog == "delete" && editing != null) {
            NdDeleteDialog(
                name = editing.name,
                onDismiss = { dialog = null },
                onConfirm = {
                    val wasActive = editing.id == activeProfile?.id
                    persist(profiles.filter { it.id != editing.id })
                    if (wasActive) onStop()
                    dialog = null
                    screen = "list"
                    LogManager.append("Deleted profile: ${editing.name}")
                },
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText(label, text))
}

// ── Top bar ─────────────────────────────────────────────────────────
@Composable
private fun NdTopBar(title: String, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = NdTheme.colors
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(horizontal = 16.dp, vertical = 14.dp).height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        Text(
            title.uppercase(),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 15.sp, letterSpacing = 0.12.em, color = c.display),
        )
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { trailing() }
    }
}

// ── Connection hero (instrument panel) ──────────────────────────────
@Composable
private fun NdConnectionHero(
    hc: NdColors,
    profile: NipoProfile?,
    connected: Boolean,
    elapsed: String,
    pingMs: Long?,
    onToggle: () -> Unit,
) {
    val statusColor = if (connected) hc.success else hc.secondary
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape).background(hc.surface).border(1.dp, hc.borderVisible, shape),
    ) {
        NdDotGrid(Modifier.matchParentSize(), subtle = true)
        Column(Modifier.padding(22.dp)) {
            // status row: status + ping (ping only when connected)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                NdStatusOn(hc, if (connected) "Connected" else "Offline", statusColor, dot = true)
                if (connected) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(pingMs?.toString() ?: "—", style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 14.sp, color = hc.primary))
                        NdLabelOn(hc, "ms", hc.secondary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // compact timer (clean Space Mono, tabular) + profile name on one line
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (connected) elapsed else "--:--",
                    modifier = Modifier.alignByBaseline(),
                    style = TextStyle(fontFamily = NothingFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 42.sp, letterSpacing = 0.01.em, fontFeatureSettings = "tnum", color = if (connected) hc.display else hc.disabled),
                )
                Text(
                    if (connected) (profile?.name ?: "") else "No active tunnel",
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = NothingFonts.Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = hc.primary),
                )
            }
            Spacer(Modifier.height(22.dp))
            if (connected) {
                NdButton("Disconnect", onToggle, variant = NdButtonVariant.DESTRUCTIVE, icon = Icons.Filled.Pause, full = true)
            } else {
                NdButton("Connect", onToggle, variant = NdButtonVariant.PRIMARY, icon = Icons.Filled.PlayArrow, full = true)
            }
        }
    }
}

// Status with explicit token set (for inverted hero).
@Composable
private fun NdStatusOn(hc: NdColors, label: String, color: androidx.compose.ui.graphics.Color, dot: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dot) Box(Modifier.size(7.dp).background(color))
        Text("[ ${label.uppercase()} ]", style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 11.sp, letterSpacing = 0.1.em, color = color))
    }
}

// ── Profile row ─────────────────────────────────────────────────────
@Composable
private fun NdProfileRow(profile: NipoProfile, active: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = NdTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .background(if (active) c.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().width(2.dp).background(if (active) c.success else androidx.compose.ui.graphics.Color.Transparent))
        Row(Modifier.weight(1f).fillMaxHeight().ndClick(onClick = onOpen).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(8.dp).background(if (active) c.success else androidx.compose.ui.graphics.Color.Transparent).then(if (active) Modifier else Modifier.border(1.dp, c.borderVisible)))
            Column(Modifier.weight(1f)) {
                Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = NothingFonts.Body, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = c.primary))
                Spacer(Modifier.height(3.dp))
                Text(profile.config.protocol.uppercase(), style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 12.sp, letterSpacing = 0.02.em, color = c.secondary))
            }
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).border(1.dp, c.borderVisible, RoundedCornerShape(999.dp)).ndClick(onClick = onToggle), contentAlignment = Alignment.Center) {
            Icon(if (active) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = c.primary, modifier = Modifier.size(18.dp))
        }
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.disabled, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Profiles screen ─────────────────────────────────────────────────
@Composable
private fun NdProfilesScreen(
    profiles: List<NipoProfile>,
    activeId: String?,
    connected: Boolean,
    elapsed: String,
    pingMs: Long?,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
) {
    val c = NdTheme.colors
    val heroColors = c // hero follows the ambient theme (no inverted panel)
    val displayProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        NdTopBar(
            title = "NipoVPN",
            leading = {
                Box(Modifier.size(30.dp).border(1.dp, c.display), contentAlignment = Alignment.Center) {
                    Text("N", style = TextStyle(fontFamily = NothingFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.display))
                }
            },
            trailing = {
                NdIconButton(Icons.Filled.FileDownload, onImport)
                NdIconButton(Icons.Filled.Add, onAdd)
            },
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            NdConnectionHero(
                hc = heroColors,
                profile = displayProfile,
                connected = connected,
                elapsed = elapsed,
                pingMs = pingMs,
                onToggle = { displayProfile?.id?.let(onToggle) },
            )
            Spacer(Modifier.height(28.dp))
            NdLabel("Profiles · ${profiles.size}")
            Spacer(Modifier.height(4.dp))
            NdDivider(c.borderVisible)
            profiles.forEachIndexed { i, p ->
                NdProfileRow(p, p.id == activeId, onToggle = { onToggle(p.id) }, onOpen = { onOpen(p.id) })
                if (i < profiles.size - 1) NdDivider(c.border)
            }
            NdDivider(c.border)
        }
    }
}

// ── Logs screen ─────────────────────────────────────────────────────
@Composable
private fun NdLogsScreen(
    context: Context,
    profiles: List<NipoProfile>,
    logs: String,
    connected: Boolean,
    activeName: String,
    onLevelChange: (String) -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
) {
    val c = NdTheme.colors
    val level = (profiles.firstOrNull { it.enabled }?.config?.logLevel ?: profiles.firstOrNull()?.config?.logLevel ?: "INFO").uppercase()
    val scroll = rememberScrollState()
    LaunchedEffect(logs) { scroll.animateScrollTo(scroll.maxValue) }

    fun lineColor(ln: String) = when {
        ln.contains("[ERROR", true) || ln.contains("Failed", true) || ln.contains("error", false) -> c.accent
        ln.contains("[INFO", true) -> c.primary
        ln.contains("[TRACE", true) -> c.secondary
        ln.contains("[DEBUG", true) -> c.disabled
        else -> c.secondary
    }

    Column(Modifier.fillMaxSize()) {
        NdTopBar(
            title = "Logs",
            trailing = {
                NdIconButton(Icons.Filled.CleaningServices, onClear)
                NdIconButton(Icons.Filled.ContentCopy, onCopy)
            },
        )
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            NdSegmented(
                options = listOf(NdSegOption("INFO", "Info"), NdSegOption("TRACE", "Trace"), NdSegOption("DEBUG", "Debug")),
                value = level,
                onChange = onLevelChange,
                full = true,
            )
        }
        val shape = RoundedCornerShape(12.dp)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).clip(shape).background(c.surface).border(1.dp, c.borderVisible, shape)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                NdStatus(if (connected) "Streaming · $activeName" else "Idle", color = if (connected) c.success else c.secondary, dot = NdDot.SOLID)
                NdLabel(level)
            }
            NdDivider(c.border)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 12.dp)) {
                logs.ifBlank { "No logs yet..." }.split("\n").forEach { ln ->
                    Text(ln, style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 12.sp, lineHeight = 20.sp, color = lineColor(ln)))
                }
            }
        }
    }
}

// ── Config screen ───────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NdConfigScreen(
    context: Context,
    profile: NipoProfile,
    connected: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSave: (NipoProfile) -> Unit,
    onDelete: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val c = NdTheme.colors
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var cfg by remember(profile.id) { mutableStateOf(profile.config.normalized()) }
    fun save() = onSave(profile.copy(name = name, config = cfg.normalized()))

    val selectedMethods = cfg.methods.lines().map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
    fun toggleMethod(m: String) {
        val set = if (selectedMethods.contains(m)) selectedMethods - m else selectedMethods + m
        cfg = cfg.copy(methods = listOf("GET", "POST", "PUT", "DELETE").filter { set.contains(it) }.joinToString("\n"))
    }

    Column(Modifier.fillMaxSize()) {
        NdTopBar(
            title = "Edit Profile",
            leading = { NdBackButton(Icons.AutoMirrored.Filled.ArrowBack, onBack) },
            trailing = { NdButton("Save", { save() }, variant = NdButtonVariant.PRIMARY) },
        )
        Column(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {

            NdSection("Profile") {
                NdBoxInput("Profile name", name, { name = it }, mono = false)
                NdBoxInput("Token", cfg.token, { cfg = cfg.copy(token = it) }, trailing = {
                    NdIconButton(Icons.Filled.ContentCopy, { copyToClipboard(context, "Token", cfg.token) }, size = 28.dp)
                })
            }

            NdSection("Connection") {
                NdSegmented(
                    options = listOf(NdSegOption("http", "HTTP"), NdSegOption("socks5", "SOCKS5")),
                    value = cfg.protocol,
                    onChange = { cfg = cfg.copy(protocol = it).normalized() },
                    full = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NdBoxInput("Timeout · s", cfg.timeout, { cfg = cfg.copy(timeout = it) }, numeric = true, modifier = Modifier.weight(1f))
                    NdBoxInput("Pull", cfg.pullTimeout, { cfg = cfg.copy(pullTimeout = it) }, numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NdToggleBox("Tunnel", cfg.tunnelEnable, {
                        cfg = cfg.copy(tunnelEnable = it, connectionReuse = if (it) false else cfg.connectionReuse).normalized()
                    }, Modifier.weight(1f))
                    NdToggleBox("Reuse", cfg.connectionReuse, {
                        cfg = cfg.copy(connectionReuse = it, tunnelEnable = if (it) false else cfg.tunnelEnable).normalized()
                    }, Modifier.weight(1f))
                    NdToggleBox("TLS", cfg.tlsEnable, { cfg = cfg.copy(tlsEnable = it).normalized() }, Modifier.weight(1f))
                }
            }

            NdSection("Endpoints") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NdBoxInput("Listen IP", cfg.listenIp, { cfg = cfg.copy(listenIp = it) }, modifier = Modifier.weight(2f))
                    NdBoxInput("Port", cfg.listenPort, { cfg = cfg.copy(listenPort = it) }, numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NdBoxInput("Server IP", cfg.serverIp, { cfg = cfg.copy(serverIp = it) }, modifier = Modifier.weight(2f))
                    NdBoxInput("Port", cfg.serverPort, { cfg = cfg.copy(serverPort = it) }, numeric = true, modifier = Modifier.weight(1f))
                }
            }

            NdSection("Advanced") {
                NdBoxInput("User agent", cfg.userAgent, { cfg = cfg.copy(userAgent = it) }, mono = false, multiline = true, rows = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NdBoxInput("Fake URLs", cfg.fakeUrls, { cfg = cfg.copy(fakeUrls = it) }, multiline = true, rows = 3, modifier = Modifier.weight(1f))
                    NdBoxInput("Endpoints", cfg.endPoints, { cfg = cfg.copy(endPoints = it) }, multiline = true, rows = 3, modifier = Modifier.weight(1f))
                }
                Column {
                    NdLabel("HTTP methods")
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("GET", "POST", "PUT", "DELETE").forEach { m ->
                            NdChip(m, selectedMethods.contains(m), { toggleMethod(m) }, technical = true)
                        }
                    }
                }
            }

            NdSection("Actions") {
                if (connected) NdButton("Disconnect", onToggle, variant = NdButtonVariant.DESTRUCTIVE, icon = Icons.Filled.Pause, full = true)
                else NdButton("Connect", onToggle, variant = NdButtonVariant.PRIMARY, icon = Icons.Filled.PlayArrow, full = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NdButton("Share", onCopyLink, variant = NdButtonVariant.SECONDARY, icon = Icons.Filled.Link, full = true, modifier = Modifier.weight(1f))
                    NdButton("Delete", onDelete, variant = NdButtonVariant.GHOST, icon = Icons.Filled.Delete)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NdSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        NdLabel(title)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────
@Composable
private fun NdDialogScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = NdTheme.colors
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xCC000000)).ndClick(onClick = onDismiss).imePadding(), contentAlignment = Alignment.Center) {
        Box(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.borderVisible, RoundedCornerShape(16.dp)).ndClick {}.padding(24.dp)) {
            content()
        }
    }
}

@Composable
private fun NdImportDialog(value: String, onChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = NdTheme.colors
    NdDialogScrim(onDismiss) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                NdLabel("Import Profile", color = c.display, size = 13.sp)
                NdButton("[ X ]", onDismiss, variant = NdButtonVariant.GHOST)
            }
            Spacer(Modifier.height(20.dp))
            NdInput("Share link", value, onChange)
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                NdButton("Cancel", onDismiss, variant = NdButtonVariant.SECONDARY)
                NdButton("Import", onConfirm, variant = NdButtonVariant.PRIMARY)
            }
        }
    }
}

@Composable
private fun NdDeleteDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = NdTheme.colors
    NdDialogScrim(onDismiss) {
        Column {
            NdStatus("Confirm Delete", color = c.accent, dot = NdDot.NONE)
            Spacer(Modifier.height(12.dp))
            Text("Profile “$name” will be permanently removed. This cannot be undone.", style = TextStyle(fontFamily = NothingFonts.Body, fontSize = 15.sp, lineHeight = 22.sp, color = c.primary))
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                NdButton("Cancel", onDismiss, variant = NdButtonVariant.SECONDARY)
                NdButton("Delete", onConfirm, variant = NdButtonVariant.DESTRUCTIVE)
            }
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────
@Composable
private fun NdEmptyState(onAdd: () -> Unit, onImport: () -> Unit) {
    val c = NdTheme.colors
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(120.dp).border(1.dp, c.borderVisible)) { NdDotGrid(Modifier.matchParentSize()) }
        Spacer(Modifier.height(32.dp))
        NdLabel("No Profiles", color = c.secondary, size = 13.sp)
        Spacer(Modifier.height(12.dp))
        Text("Create a profile or import a share link to start tunneling traffic.", style = TextStyle(fontFamily = NothingFonts.Body, fontSize = 14.sp, lineHeight = 21.sp, color = c.disabled, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NdButton("Import", onImport, variant = NdButtonVariant.SECONDARY, icon = Icons.Filled.FileDownload)
            NdButton("New", onAdd, variant = NdButtonVariant.PRIMARY, icon = Icons.Filled.Add)
        }
    }
}

