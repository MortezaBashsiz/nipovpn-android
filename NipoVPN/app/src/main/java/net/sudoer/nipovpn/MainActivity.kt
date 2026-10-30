package net.sudoer.nipo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subscriptions
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sudoer.nipo.ui.nd.NdBottomNav
import net.sudoer.nipo.ui.nd.NdBoxInput
import net.sudoer.nipo.ui.nd.NdButton
import net.sudoer.nipo.ui.nd.NdButtonVariant
import net.sudoer.nipo.ui.nd.NdChip
import net.sudoer.nipo.ui.nd.NdDivider
import net.sudoer.nipo.ui.nd.NdDot
import net.sudoer.nipo.ui.nd.NdDotGrid
import net.sudoer.nipo.ui.nd.NdGrid
import net.sudoer.nipo.ui.nd.NdIconButton
import net.sudoer.nipo.ui.nd.NdInput
import net.sudoer.nipo.ui.nd.NdLabel
import net.sudoer.nipo.ui.nd.NdLabelOn
import net.sudoer.nipo.ui.nd.NdNavItem
import net.sudoer.nipo.ui.nd.NdSegOption
import net.sudoer.nipo.ui.nd.NdSegmented
import net.sudoer.nipo.ui.nd.NdStatus
import net.sudoer.nipo.ui.nd.NdToggleBox
import net.sudoer.nipo.ui.nd.ndClick
import net.sudoer.nipo.ui.nd.ndSelect
import androidx.compose.ui.semantics.Role
import net.sudoer.nipo.ui.theme.NdColors
import net.sudoer.nipo.ui.theme.NdTheme
import net.sudoer.nipo.ui.theme.NothingFonts
import net.sudoer.nipo.ui.theme.NothingTheme
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

// A new profile starts with the server-specific identity fields blank, so the
// user is forced to enter their own values (and we never seed a real server's
// token/IP). Technical defaults (ports, timeouts, methods, UA) are kept.
private fun newProfileConfig() = NipoConfig(token = "", serverIp = "", fakeUrls = "")

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
    var importText by remember { mutableStateOf("") }
    // A freshly-added profile that hasn't been saved yet. Held here (not in the
    // persisted list) so backing out of the editor discards it instead of
    // leaving an empty "New profile" on disk.
    var draft by remember { mutableStateOf<NipoProfile?>(null) }
    // The profile the user has picked to act on (left indicator on the list).
    // The hero Connect button connects this one. Null → fall back to the
    // running profile, else the first profile.
    var selectedId by remember { mutableStateOf(loadSelectedId(context)) }
    var subscriptions by remember { mutableStateOf(loadSubscriptions(context)) }
    var editSubId by remember { mutableStateOf<String?>(null) }
    var subName by remember { mutableStateOf("") }
    var subLink by remember { mutableStateOf("") }
    var subFormError by remember { mutableStateOf<String?>(null) }
    var subImportText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val connectionState by ConnectionStatus.state.collectAsState()
    val activeProfile = profiles.firstOrNull { it.enabled }
    val connected = connectionState.running
    val resolvedSelectedId = selectedId?.takeIf { id -> profiles.any { it.id == id } }
        ?: activeProfile?.id ?: profiles.firstOrNull()?.id

    fun persist(updated: List<NipoProfile>) { profiles = updated; saveProfiles(context, updated) }
    // Update + persist the selection so it survives app restarts.
    fun select(id: String) { selectedId = id; saveSelectedId(context, id) }
    fun persistSubs(updated: List<NipoSubscription>) { subscriptions = updated; saveSubscriptions(context, updated) }

    fun duplicateSubError(name: String, link: String, excludingId: String?): String? {
        val others = subscriptions.filter { it.id != excludingId }
        return when {
            others.any { it.name.equals(name, ignoreCase = true) } -> "A subscription with that name already exists"
            others.any { it.link == link } -> "A subscription with that link already exists"
            else -> null
        }
    }

    // A profile is the "same" as another if its name matches case-insensitively,
    // regardless of which subscription (or none) it came from. Merging a fetch
    // updates the matching profile in place (keeping its id and user settings
    // like enabled) instead of duplicating it; only truly new names are added.
    fun mergeFetchedProfiles(current: List<NipoProfile>, fetched: List<NipoProfile>, subId: String): Triple<List<NipoProfile>, Int, Int> {
        val result = current.toMutableList()
        var added = 0
        var updated = 0
        fetched.forEach { incoming ->
            val idx = result.indexOfFirst { it.name.equals(incoming.name, ignoreCase = true) }
            if (idx >= 0) {
                result[idx] = result[idx].copy(config = incoming.config, subscriptionId = subId)
                updated++
            } else {
                result.add(incoming.copy(subscriptionId = subId))
                added++
            }
        }
        return Triple(result, added, updated)
    }

    // Fetches profiles from a subscription's link and merges them by name into
    // the full profile list. Used both when a subscription is first
    // added/imported and on manual refresh.
    fun refreshSubscription(sub: NipoSubscription) {
        scope.launch {
            val (updatedSub, fetchedProfiles) = try {
                val fetched = withContext(Dispatchers.IO) { fetchSubscriptionProfiles(sub.link) }
                sub.copy(lastFetchAtMillis = System.currentTimeMillis(), lastError = null) to fetched
            } catch (e: Exception) {
                sub.copy(lastFetchAtMillis = System.currentTimeMillis(), lastError = e.message ?: "Fetch failed") to null
            }
            persistSubs(subscriptions.map { if (it.id == sub.id) updatedSub else it })
            if (fetchedProfiles != null) {
                val (merged, added, updated) = mergeFetchedProfiles(profiles, fetchedProfiles, sub.id)
                persist(merged)
                Toast.makeText(context, "$added new profile${if (added == 1) "" else "s"} added, $updated updated", Toast.LENGTH_LONG).show()
                LogManager.append("Refreshed subscription: ${sub.name} ($added added, $updated updated)")
            } else {
                Toast.makeText(context, "Failed to refresh \"${sub.name}\": ${updatedSub.lastError}", Toast.LENGTH_LONG).show()
                LogManager.append("Failed to refresh subscription: ${sub.name} — ${updatedSub.lastError}")
            }
        }
    }

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
        val np = NipoProfile(id = UUID.randomUUID().toString(), name = "New profile", enabled = false, config = newProfileConfig())
        draft = np            // not persisted until the user taps Save
        editId = np.id
        screen = "config"
    }

    fun openSubForm(existing: NipoSubscription?) {
        editSubId = existing?.id
        subName = existing?.name ?: ""
        subLink = existing?.link ?: ""
        subFormError = null
        dialog = "sub_form"
    }

    fun saveSub() {
        val name = subName.trim()
        val link = subLink.trim()
        if (name.isBlank() || link.isBlank()) { subFormError = "Name and link are required"; return }
        val error = duplicateSubError(name, link, excludingId = editSubId)
        if (error != null) { subFormError = error; return }

        val sub = subscriptions.firstOrNull { it.id == editSubId }?.copy(name = name, link = link)
            ?: NipoSubscription(name = name, link = link)
        persistSubs(if (editSubId == null) subscriptions + sub else subscriptions.map { if (it.id == sub.id) sub else it })
        dialog = null
        refreshSubscription(sub)
    }

    fun deleteSub(sub: NipoSubscription, alsoDeleteProfiles: Boolean) {
        persistSubs(subscriptions.filter { it.id != sub.id })
        persist(
            if (alsoDeleteProfiles) profiles.filter { it.subscriptionId != sub.id }
            else profiles.map { if (it.subscriptionId == sub.id) it.copy(subscriptionId = null) else it }
        )
        dialog = null
        LogManager.append("Deleted subscription: ${sub.name}")
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
    val editing = draft?.takeIf { it.id == editId } ?: profiles.firstOrNull { it.id == editId }
    val showNav = screen == "list"

    // System back / back-gesture handling (issue #17 ②): navigate within the
    // app instead of exiting. Order matters — close an open dialog first, then
    // step back out of the editor, then off the Logs tab; only the Profiles
    // root falls through to a double-press-to-exit.
    val activity = context as? android.app.Activity
    var lastBackMs by remember { mutableStateOf(0L) }
    BackHandler {
        when {
            dialog != null -> dialog = null
            screen == "config" -> { draft = null; screen = "list" }
            tab == "logs" || tab == "subs" -> tab = "profiles"
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackMs < 2000) activity?.finish()
                else {
                    lastBackMs = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.black)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                when {
                    screen == "config" && editing != null -> NdConfigScreen(
                        context = context,
                        profile = editing,
                        connected = connected && editing.id == activeProfile?.id,
                        onBack = { draft = null; screen = "list" },
                        onToggle = { select(editing.id); toggle(editing.id) },
                        isNameTaken = { candidate -> profiles.any { it.id != editing.id && it.name.equals(candidate, ignoreCase = true) } },
                        onSave = { updated ->
                            if (draft?.id == updated.id) persist(profiles + updated)
                            else persist(profiles.map { if (it.id == updated.id) updated else it })
                            draft = null
                            screen = "list"
                            Toast.makeText(context, "\"${updated.name}\" saved", Toast.LENGTH_SHORT).show()
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
                        title = "No Profiles",
                        message = "Create a profile or import a share link to start tunneling traffic.",
                        onAdd = { addProfile() },
                        onImport = { importText = ""; dialog = "import" },
                    )
                    tab == "profiles" -> NdProfilesScreen(
                        profiles = profiles,
                        subscriptions = subscriptions,
                        activeId = activeProfile?.id,
                        selectedId = resolvedSelectedId,
                        connected = connected,
                        elapsed = formatElapsed(elapsedSeconds),
                        pingMs = pingMs,
                        onSelect = { select(it) },
                        onConnect = { resolvedSelectedId?.let { id -> toggle(id) } },
                        onOpen = { draft = null; editId = it; screen = "config" },
                        onAdd = { addProfile() },
                        onImport = { importText = ""; dialog = "import" },
                    )
                    tab == "subs" && subscriptions.isEmpty() -> NdEmptyState(
                        title = "No Subscriptions",
                        message = "Add a subscription or import one to fetch profiles automatically.",
                        onAdd = { openSubForm(null) },
                        onImport = { subImportText = ""; dialog = "import_sub" },
                    )
                    tab == "subs" -> NdSubsScreen(
                        subscriptions = subscriptions,
                        onAdd = { openSubForm(null) },
                        onImport = { subImportText = ""; dialog = "import_sub" },
                        onEdit = { openSubForm(it) },
                        onRefresh = { refreshSubscription(it) },
                        onDelete = { editSubId = it.id; dialog = "delete_sub" },
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
                        NdNavItem("subs", Icons.Filled.Subscriptions, "Subs"),
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
                    importText = ""
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
                    draft = null
                    dialog = null
                    screen = "list"
                    LogManager.append("Deleted profile: ${editing.name}")
                },
            )
        }
        if (dialog == "sub_form") {
            NdSubFormDialog(
                editing = editSubId != null,
                name = subName,
                link = subLink,
                error = subFormError,
                onNameChange = { subName = it; subFormError = null },
                onLinkChange = { subLink = it; subFormError = null },
                onDismiss = { dialog = null },
                onConfirm = { saveSub() },
            )
        }
        if (dialog == "import_sub") {
            NdImportDialog(
                title = "Import Subscription",
                label = "Base64 string",
                placeholder = "eyJuYW1lIjoi…",
                multiline = true,
                value = subImportText,
                onChange = { subImportText = it },
                onDismiss = { dialog = null },
                onConfirm = {
                    try {
                        val decoded = String(android.util.Base64.decode(subImportText.trim(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE), Charsets.UTF_8)
                        val obj = org.json.JSONObject(decoded)
                        val name = obj.getString("name").trim()
                        val link = obj.getString("link").trim()
                        val error = duplicateSubError(name, link, excludingId = null)
                        if (error != null) throw IllegalArgumentException(error)
                        val sub = NipoSubscription(name = name, link = link)
                        persistSubs(subscriptions + sub)
                        dialog = null
                        refreshSubscription(sub)
                        LogManager.append("Imported subscription: $name")
                    } catch (e: Exception) {
                        LogManager.append("Subscription import failed: ${e.message}")
                    }
                },
            )
        }
        if (dialog == "delete_sub") {
            subscriptions.firstOrNull { it.id == editSubId }?.let { sub ->
                NdDeleteSubDialog(
                    name = sub.name,
                    onDismiss = { dialog = null },
                    onKeepProfiles = { deleteSub(sub, alsoDeleteProfiles = false) },
                    onDeleteProfiles = { deleteSub(sub, alsoDeleteProfiles = true) },
                )
            }
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
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { trailing() }
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
                    maxLines = 2,
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
private fun NdProfileRow(
    profile: NipoProfile,
    selected: Boolean,
    running: Boolean,
    subscriptionName: String?,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = NdTheme.colors
    // Left indicator: green when running, white/primary when selected (but not
    // yet running), nothing otherwise.
    val indicator = when {
        running -> c.success
        selected -> c.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val marked = running || selected
    Row(
        Modifier.fillMaxWidth()
            .background(if (marked) c.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().width(2.dp).background(indicator))
        // Tapping the row body selects this profile (the hero Connect then
        // acts on it). Editing is via the chevron on the right.
        Row(Modifier.weight(1f).fillMaxHeight().ndSelect(selected, Role.RadioButton, onSelect).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(8.dp).background(indicator).then(if (marked) Modifier else Modifier.border(1.dp, c.borderVisible)))
            Column(Modifier.weight(1f)) {
                Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = NothingFonts.Body, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = c.primary))
                Spacer(Modifier.height(3.dp))
                Text(
                    if (subscriptionName != null) "${profile.config.protocol.uppercase()} · $subscriptionName" else profile.config.protocol.uppercase(),
                    style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 12.sp, letterSpacing = 0.02.em, color = c.secondary),
                )
            }
        }
        NdIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Edit ${profile.name}", onOpen, outlined = false)
    }
}

// ── Profiles screen ─────────────────────────────────────────────────
@Composable
private fun NdProfilesScreen(
    profiles: List<NipoProfile>,
    subscriptions: List<NipoSubscription>,
    activeId: String?,
    selectedId: String?,
    connected: Boolean,
    elapsed: String,
    pingMs: Long?,
    onSelect: (String) -> Unit,
    onConnect: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
) {
    val c = NdTheme.colors
    val heroColors = c // hero follows the ambient theme (no inverted panel)
    val displayProfile = profiles.firstOrNull { it.id == selectedId }
    // Only show the connected state in the hero when the selected profile IS
    // the one currently running (you may select another while connected).
    val heroConnected = connected && activeId == selectedId
    Column(Modifier.fillMaxSize()) {
        NdTopBar(
            title = "NipoVPN",
            leading = {
                Box(Modifier.size(30.dp).border(1.dp, c.display), contentAlignment = Alignment.Center) {
                    Text("N", style = TextStyle(fontFamily = NothingFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.display))
                }
            },
            trailing = {
                NdIconButton(Icons.Filled.FileDownload, "Import profile", onImport)
                NdIconButton(Icons.Filled.Add, "Add profile", onAdd)
            },
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            NdConnectionHero(
                hc = heroColors,
                profile = displayProfile,
                connected = heroConnected,
                elapsed = elapsed,
                pingMs = pingMs,
                onToggle = onConnect,
            )
            Spacer(Modifier.height(28.dp))
            NdLabel("Profiles · ${profiles.size}")
            Spacer(Modifier.height(4.dp))
            NdDivider(c.borderVisible)
            profiles.forEachIndexed { i, p ->
                NdProfileRow(
                    profile = p,
                    selected = p.id == selectedId,
                    running = connected && p.id == activeId,
                    subscriptionName = subscriptions.firstOrNull { it.id == p.subscriptionId }?.name,
                    onSelect = { onSelect(p.id) },
                    onOpen = { onOpen(p.id) },
                )
                if (i < profiles.size - 1) NdDivider(c.border)
            }
            NdDivider(c.border)
        }
    }
}

// ── Subscriptions screen ─────────────────────────────────────────────
@Composable
private fun NdSubsScreen(
    subscriptions: List<NipoSubscription>,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onEdit: (NipoSubscription) -> Unit,
    onRefresh: (NipoSubscription) -> Unit,
    onDelete: (NipoSubscription) -> Unit,
) {
    val c = NdTheme.colors
    Column(Modifier.fillMaxSize()) {
        NdTopBar(
            title = "Subscriptions",
            trailing = {
                NdIconButton(Icons.Filled.FileDownload, "Import subscription", onImport)
                NdIconButton(Icons.Filled.Add, "Add subscription", onAdd)
            },
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
            subscriptions.forEachIndexed { i, s ->
                NdSubRow(s, onEdit = { onEdit(s) }, onRefresh = { onRefresh(s) }, onDelete = { onDelete(s) })
                if (i < subscriptions.size - 1) NdDivider(c.border)
            }
            NdDivider(c.border)
        }
    }
}

@Composable
private fun NdSubRow(sub: NipoSubscription, onEdit: () -> Unit, onRefresh: () -> Unit, onDelete: () -> Unit) {
    val c = NdTheme.colors
    val status = when {
        sub.lastError != null -> "Error: ${sub.lastError}"
        sub.lastFetchAtMillis != null -> "Updated ${android.text.format.DateUtils.getRelativeTimeSpanString(sub.lastFetchAtMillis, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)}"
        else -> "Never fetched"
    }
    val statusColor = if (sub.lastError != null) c.accent else c.secondary
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sub.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = NothingFonts.Body, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = c.primary))
                Spacer(Modifier.height(3.dp))
                Text(sub.link, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 12.sp, color = c.secondary))
                Spacer(Modifier.height(3.dp))
                Text(status, style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 11.sp, color = statusColor))
            }
            NdIconButton(Icons.Filled.Refresh, "Refresh ${sub.name}", onRefresh, outlined = false)
            NdIconButton(Icons.Filled.Edit, "Edit ${sub.name}", onEdit, outlined = false)
            NdIconButton(Icons.Filled.Delete, "Delete ${sub.name}", onDelete, outlined = false)
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
                NdIconButton(Icons.Filled.CleaningServices, "Clear logs", onClear)
                NdIconButton(Icons.Filled.ContentCopy, "Copy logs", onCopy)
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
    isNameTaken: (String) -> Boolean,
    onSave: (NipoProfile) -> Unit,
    onDelete: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val c = NdTheme.colors
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var cfg by remember(profile.id) { mutableStateOf(profile.config.normalized()) }
    var nameError by remember(profile.id) { mutableStateOf<String?>(null) }
    fun save() {
        if (isNameTaken(name.trim())) { nameError = "A profile with this name already exists"; return }
        onSave(profile.copy(name = name.trim(), config = cfg.normalized()))
    }

    val selectedMethods = cfg.methods.lines().map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
    fun toggleMethod(m: String) {
        val set = if (selectedMethods.contains(m)) selectedMethods - m else selectedMethods + m
        cfg = cfg.copy(methods = listOf("GET", "POST", "PUT", "DELETE").filter { set.contains(it) }.joinToString("\n"))
    }

    Column(Modifier.fillMaxSize()) {
        NdTopBar(
            title = "Edit Profile",
            leading = { NdIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) },
            trailing = { NdIconButton(Icons.Filled.Link, "Copy share link", onCopyLink) },
        )
        Column(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {

            NdSection("Profile") {
                NdBoxInput("Profile name", name, { name = it; nameError = null }, mono = false)
                if (nameError != null) {
                    Text(nameError!!, style = TextStyle(fontFamily = NothingFonts.Body, fontSize = 13.sp, color = c.accent))
                }
                NdBoxInput("Token", cfg.token, { cfg = cfg.copy(token = it) }, placeholder = "your-server-token", trailing = {
                    NdIconButton(Icons.Filled.ContentCopy, "Copy token", { copyToClipboard(context, "Token", cfg.token) }, outlined = false, size = 28.dp)
                })
            }

            NdSection("Connection") {
                NdSegmented(
                    options = listOf(NdSegOption("http", "HTTP"), NdSegOption("socks5", "SOCKS5")),
                    value = cfg.protocol,
                    onChange = { cfg = cfg.copy(protocol = it).normalized() },
                    full = true,
                )
                NdGrid(
                    3,
                    { m -> NdBoxInput("Buffer · B", cfg.bufferSize, { cfg = cfg.copy(bufferSize = it) }, numeric = true, placeholder = "65536", modifier = m) },
                    { m -> NdBoxInput("Timeout · s", cfg.timeout, { cfg = cfg.copy(timeout = it) }, numeric = true, modifier = m) },
                    { m -> NdBoxInput("Pull · s", cfg.pullTimeout, { cfg = cfg.copy(pullTimeout = it) }, numeric = true, modifier = m) },
                )
                NdGrid(
                    4,
                    { m ->
                        NdToggleBox("Tunnel", cfg.tunnelEnable, {
                            cfg = cfg.copy(tunnelEnable = it, connectionReuse = if (it) false else cfg.connectionReuse).normalized()
                        }, m)
                    },
                    { m ->
                        NdToggleBox("Reuse", cfg.connectionReuse, {
                            cfg = cfg.copy(connectionReuse = it, tunnelEnable = if (it) false else cfg.tunnelEnable).normalized()
                        }, m)
                    },
                    { m -> NdToggleBox("TLS", cfg.tlsEnable, { cfg = cfg.copy(tlsEnable = it).normalized() }, m) },
                    { m -> NdToggleBox("AES", cfg.aesEnable, { cfg = cfg.copy(aesEnable = it).normalized() }, m) },
                )
            }

            NdSection("Endpoints") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NdBoxInput("Listen IP", cfg.listenIp, { cfg = cfg.copy(listenIp = it) }, modifier = Modifier.weight(2f))
                    NdBoxInput("Port", cfg.listenPort, { cfg = cfg.copy(listenPort = it) }, numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NdBoxInput("Server IP", cfg.serverIp, { cfg = cfg.copy(serverIp = it) }, placeholder = "1.2.3.4", modifier = Modifier.weight(2f))
                    NdBoxInput("Port", cfg.serverPort, { cfg = cfg.copy(serverPort = it) }, numeric = true, modifier = Modifier.weight(1f))
                }
            }

            NdSection("Advanced") {
                NdBoxInput("User agent", cfg.userAgent, { cfg = cfg.copy(userAgent = it) }, mono = false, multiline = true, rows = 2)
                NdGrid(
                    2,
                    { m -> NdBoxInput("Fake URLs", cfg.fakeUrls, { cfg = cfg.copy(fakeUrls = it) }, multiline = true, rows = 3, placeholder = "www.google.com", modifier = m) },
                    { m -> NdBoxInput("Endpoints", cfg.endPoints, { cfg = cfg.copy(endPoints = it) }, multiline = true, rows = 3, modifier = m) },
                )
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
                // Same size, different style (HIG buttons › Style): Save is the prominent one.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NdButton("Save", { save() }, variant = NdButtonVariant.PRIMARY, modifier = Modifier.weight(1f))
                    NdButton("Delete", onDelete, variant = NdButtonVariant.DESTRUCTIVE, icon = Icons.Filled.Delete, modifier = Modifier.weight(1f))
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
private fun NdDialogHeader(title: String, onDismiss: () -> Unit) {
    val c = NdTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        NdLabel(title, color = c.display, size = 13.sp)
        NdIconButton(Icons.Filled.Close, "Close", onDismiss)
    }
}

@Composable
private fun NdImportDialog(
    title: String = "Import Profile",
    label: String = "Share link",
    placeholder: String = "nipovpn://…",
    multiline: Boolean = false,
    value: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    NdDialogScrim(onDismiss) {
        Column {
            NdDialogHeader(title, onDismiss)
            Spacer(Modifier.height(20.dp))
            NdInput(label, value, onChange, multiline = multiline, placeholder = placeholder)
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NdButton("Cancel", onDismiss, variant = NdButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                NdButton("Import", onConfirm, variant = NdButtonVariant.PRIMARY, modifier = Modifier.weight(1f))
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NdButton("Cancel", onDismiss, variant = NdButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                NdButton("Delete", onConfirm, variant = NdButtonVariant.DESTRUCTIVE, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NdSubFormDialog(
    editing: Boolean,
    name: String,
    link: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onLinkChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = NdTheme.colors
    NdDialogScrim(onDismiss) {
        Column {
            NdDialogHeader(if (editing) "Edit Subscription" else "Add Subscription", onDismiss)
            Spacer(Modifier.height(20.dp))
            NdInput("Name", name, onNameChange, mono = false)
            Spacer(Modifier.height(16.dp))
            NdInput("Link", link, onLinkChange, placeholder = "https://…")
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, style = TextStyle(fontFamily = NothingFonts.Body, fontSize = 13.sp, color = c.accent))
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NdButton("Cancel", onDismiss, variant = NdButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                NdButton("Save", onConfirm, variant = NdButtonVariant.PRIMARY, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NdDeleteSubDialog(name: String, onDismiss: () -> Unit, onKeepProfiles: () -> Unit, onDeleteProfiles: () -> Unit) {
    val c = NdTheme.colors
    NdDialogScrim(onDismiss) {
        Column {
            NdStatus("Delete Subscription", color = c.accent, dot = NdDot.NONE)
            Spacer(Modifier.height(12.dp))
            Text("Subscription “$name” will be removed. What should happen to its profiles?", style = TextStyle(fontFamily = NothingFonts.Body, fontSize = 15.sp, lineHeight = 22.sp, color = c.primary))
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NdButton("Keep profiles (mark Local)", onKeepProfiles, variant = NdButtonVariant.SECONDARY, full = true)
                NdButton("Delete profiles too", onDeleteProfiles, variant = NdButtonVariant.DESTRUCTIVE, full = true)
                NdButton("Cancel", onDismiss, variant = NdButtonVariant.GHOST, full = true)
            }
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────
@Composable
private fun NdEmptyState(title: String, message: String, onAdd: () -> Unit, onImport: () -> Unit) {
    val c = NdTheme.colors
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(120.dp).border(1.dp, c.borderVisible)) { NdDotGrid(Modifier.matchParentSize()) }
        Spacer(Modifier.height(32.dp))
        NdLabel(title, color = c.secondary, size = 13.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, style = TextStyle(fontFamily = NothingFonts.Body, fontSize = 14.sp, lineHeight = 21.sp, color = c.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NdButton("Import", onImport, variant = NdButtonVariant.SECONDARY, icon = Icons.Filled.FileDownload, modifier = Modifier.weight(1f))
            NdButton("New", onAdd, variant = NdButtonVariant.PRIMARY, icon = Icons.Filled.Add, modifier = Modifier.weight(1f))
        }
    }
}

