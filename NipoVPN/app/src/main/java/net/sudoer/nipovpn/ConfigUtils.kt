package net.sudoer.nipovpn

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class NipoConfig(
    val token: String = "af445adb-2434-4975-9445-2c1b2231",
    val fakeUrls: String = "nipo.ciron.net\nwww.google.com\nsudoer.net\nsudoer.ir",
    val methods: String = "GET\nPOST\nPUT\nDELETE",
    val endPoints: String = "api\nlogin\nuser\nupdate",
    val timeout: String = "10",
    val pullTimeout: String = "50",
    val tunnelEnable: Boolean = true,
    val connectionReuse: Boolean = false,
    val tlsEnable: Boolean = true,
    val logLevel: String = "DEBUG",
    val listenIp: String = "0.0.0.0",
    val listenPort: String = "8080",
    val serverIp: String = "46.225.50.122",
    val serverPort: String = "443",
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"
)

fun NipoConfig.normalized(): NipoConfig {
    val allowedMethods = listOf("GET", "POST", "PUT", "DELETE")
    val methodsSet = methods.lines()
        .map { it.trim().uppercase() }
        .filter { allowedMethods.contains(it) }
        .toSet()

    val normalizedMethods = allowedMethods
        .filter { methodsSet.contains(it) }
        .ifEmpty { allowedMethods }
        .joinToString("\n")

    val normalizedLogLevel = when (logLevel.trim().uppercase()) {
        "TRACE" -> "TRACE"
        "DEBUG" -> "DEBUG"
        else -> "INFO"
    }

    return copy(
        methods = normalizedMethods,
        logLevel = normalizedLogLevel
    )
}

fun phoneCpuCoreCount(): Int {
    return Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
}

fun yamlList(text: String): String {
    return text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n") { "    - $it" }
}

fun generateConfigFile(context: Context, cfg: NipoConfig): File {
    val finalCfg = cfg.normalized()
    val cpuThreads = phoneCpuCoreCount()

    val logDir = File(context.filesDir, "logs")
    logDir.mkdirs()

    val logFile = File(logDir, "nipovpn.log")
    val configFile = File(context.filesDir, "config.yaml")

    val yaml = """
---
general:
  token: "${finalCfg.token}"
  fakeUrls:
${yamlList(finalCfg.fakeUrls)}
  methods:
${yamlList(finalCfg.methods)}
  endPoints:
${yamlList(finalCfg.endPoints)}
  timeout: ${finalCfg.timeout}
  pullTimeout: ${finalCfg.pullTimeout}
  tunnelEnable: ${finalCfg.tunnelEnable}
  connectionReuse: ${finalCfg.connectionReuse}
  tlsEnable: ${finalCfg.tlsEnable}
  tlsVerifyPeer: false
  tlsCertFile: ""
  tlsKeyFile: ""
  tlsCaFile: ""

log:
  logLevel: "${finalCfg.logLevel}"
  logFile: "${logFile.absolutePath}"

server:
  threads: ${cpuThreads}
  listenIp: "0.0.0.0"
  listenPort: 80

agent:
  threads: ${cpuThreads}
  listenIp: "${finalCfg.listenIp}"
  listenPort: ${finalCfg.listenPort}
  serverIp: "${finalCfg.serverIp}"
  serverPort: ${finalCfg.serverPort}
  httpVersion: "1.1"
  userAgent: "${finalCfg.userAgent}"
""".trimIndent()

    configFile.writeText(yaml)
    return configFile
}

fun exportNipoProfileToLink(profile: NipoProfile): String {
    val cfg = profile.config.normalized()

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
        .put("logLevel", cfg.logLevel)
        .put("listenIp", cfg.listenIp)
        .put("listenPort", cfg.listenPort)
        .put("serverIp", cfg.serverIp)
        .put("serverPort", cfg.serverPort)
        .put("userAgent", cfg.userAgent)

    val obj = JSONObject()
        .put("name", profile.name)
        .put("config", cfgObj)

    val encoded = Base64.encodeToString(
        obj.toString().toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE
    )

    return "nipovpn://$encoded"
}

fun importNipoProfileFromLink(text: String): NipoProfile {
    val raw = text.trim()

    require(raw.startsWith("nipovpn://")) {
        "Config must start with nipovpn://"
    }

    val encoded = raw.removePrefix("nipovpn://").trim()
    val decoded = String(
        Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE),
        Charsets.UTF_8
    )

    val obj = JSONObject(decoded)
    val cfgObj = obj.getJSONObject("config")
    val defaultCfg = NipoConfig()

    val cfg = NipoConfig(
        token = cfgObj.optString("token", defaultCfg.token),
        fakeUrls = cfgObj.optString("fakeUrls", defaultCfg.fakeUrls),
        methods = cfgObj.optString("methods", defaultCfg.methods),
        endPoints = cfgObj.optString("endPoints", defaultCfg.endPoints),
        timeout = cfgObj.optString("timeout", defaultCfg.timeout),
        pullTimeout = cfgObj.optString("pullTimeout", defaultCfg.pullTimeout),
        tunnelEnable = cfgObj.optBoolean("tunnelEnable", defaultCfg.tunnelEnable),
        connectionReuse = cfgObj.optBoolean("connectionReuse", defaultCfg.connectionReuse),
        tlsEnable = cfgObj.optBoolean("tlsEnable", defaultCfg.tlsEnable),
        logLevel = cfgObj.optString("logLevel", defaultCfg.logLevel),
        listenIp = cfgObj.optString("listenIp", defaultCfg.listenIp),
        listenPort = cfgObj.optString("listenPort", defaultCfg.listenPort),
        serverIp = cfgObj.optString("serverIp", defaultCfg.serverIp),
        serverPort = cfgObj.optString("serverPort", defaultCfg.serverPort),
        userAgent = cfgObj.optString("userAgent", defaultCfg.userAgent)
    ).normalized()

    return NipoProfile(
        id = UUID.randomUUID().toString(),
        name = obj.optString("name", "Imported Profile"),
        enabled = false,
        config = cfg
    )
}
