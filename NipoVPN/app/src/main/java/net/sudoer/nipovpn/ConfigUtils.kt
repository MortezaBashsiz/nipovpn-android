package net.sudoer.nipovpn

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class NipoConfig(
    val token: String = "af445adb-2434-4975-9445-2c1b2231",
    val protocol: String = "socks5",
    val fakeUrls: String = "nipo.ciron.net\nwww.google.com\nsudoer.net\nsudoer.ir",
    val methods: String = "GET\nPOST\nPUT\nDELETE",
    val endPoints: String = "api\nlogin\nuser\nupdate",
    val timeout: String = "10",
    val pullTimeout: String = "50",
    val tunnelEnable: Boolean = true,
    val connectionReuse: Boolean = false,
    val tlsEnable: Boolean = true,
    val tlsVerifyPeer: Boolean = false,
    val tlsCertFile: String = "/etc/nipovpn/server.crt",
    val tlsKeyFile: String = "/etc/nipovpn/server.key",
    val tlsCaFile: String = "",
    val logLevel: String = "DEBUG",
    val listenIp: String = "0.0.0.0",
    val listenPort: String = "8080",
    val serverIp: String = "46.225.50.122",
    val serverPort: String = "443",
    val httpVersion: String = "1.1",
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0",
    val bufferSize: String = "65536"
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

    val normalizedProtocol = when (protocol.trim().lowercase()) {
        "http" -> "http"
        "socks5" -> "socks5"
        else -> "socks5"
    }

    val normalizedTunnelEnable = if (connectionReuse) false else tunnelEnable
    val normalizedConnectionReuse = if (normalizedTunnelEnable) false else connectionReuse

    val normalizedBufferSize = bufferSize.trim().toIntOrNull()
        ?.coerceIn(1, 65536)?.toString() ?: "65536"

    return copy(
        protocol = normalizedProtocol,
        methods = normalizedMethods,
        logLevel = normalizedLogLevel,
        tunnelEnable = normalizedTunnelEnable,
        connectionReuse = normalizedConnectionReuse,
        bufferSize = normalizedBufferSize
    )
}

fun phoneCpuCoreCount(): Int {
    return Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
}

fun yamlList(text: String): String {
    return text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n") { " - $it" }
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
 protocol: ${finalCfg.protocol}
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
 tlsVerifyPeer: ${finalCfg.tlsVerifyPeer}
 tlsCertFile: "${finalCfg.tlsCertFile}"
 tlsKeyFile: "${finalCfg.tlsKeyFile}"
 tlsCaFile: "${finalCfg.tlsCaFile}"
 bufferSize: ${finalCfg.bufferSize}
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
 httpVersion: "${finalCfg.httpVersion}"
 userAgent: "${finalCfg.userAgent}"
""".trimIndent()

    configFile.writeText(yaml)
    LogManager.append("Generated config: protocol=${finalCfg.protocol.uppercase()} logLevel=${finalCfg.logLevel}")
    return configFile
}

fun exportNipoProfileToLink(profile: NipoProfile): String {
    val cfg = profile.config.normalized()
    val cfgObj = JSONObject()
        .put("token", cfg.token)
        .put("protocol", cfg.protocol)
        .put("fakeUrls", cfg.fakeUrls)
        .put("methods", cfg.methods)
        .put("endPoints", cfg.endPoints)
        .put("timeout", cfg.timeout)
        .put("pullTimeout", cfg.pullTimeout)
        .put("tunnelEnable", cfg.tunnelEnable)
        .put("connectionReuse", cfg.connectionReuse)
        .put("tlsEnable", cfg.tlsEnable)
        .put("tlsVerifyPeer", cfg.tlsVerifyPeer)
        .put("tlsCertFile", cfg.tlsCertFile)
        .put("tlsKeyFile", cfg.tlsKeyFile)
        .put("tlsCaFile", cfg.tlsCaFile)
        .put("logLevel", cfg.logLevel)
        .put("listenIp", cfg.listenIp)
        .put("listenPort", cfg.listenPort)
        .put("serverIp", cfg.serverIp)
        .put("serverPort", cfg.serverPort)
        .put("httpVersion", cfg.httpVersion)
        .put("userAgent", cfg.userAgent)
        .put("bufferSize", cfg.bufferSize)

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
    require(raw.startsWith("nipovpn://")) { "Config must start with nipovpn://" }

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
        protocol = cfgObj.optString("protocol", defaultCfg.protocol),
        fakeUrls = cfgObj.optString("fakeUrls", defaultCfg.fakeUrls),
        methods = cfgObj.optString("methods", defaultCfg.methods),
        endPoints = cfgObj.optString("endPoints", defaultCfg.endPoints),
        timeout = cfgObj.optString("timeout", defaultCfg.timeout),
        pullTimeout = cfgObj.optString("pullTimeout", defaultCfg.pullTimeout),
        tunnelEnable = cfgObj.optBoolean("tunnelEnable", defaultCfg.tunnelEnable),
        connectionReuse = cfgObj.optBoolean("connectionReuse", defaultCfg.connectionReuse),
        tlsEnable = cfgObj.optBoolean("tlsEnable", defaultCfg.tlsEnable),
        tlsVerifyPeer = cfgObj.optBoolean("tlsVerifyPeer", defaultCfg.tlsVerifyPeer),
        tlsCertFile = cfgObj.optString("tlsCertFile", defaultCfg.tlsCertFile),
        tlsKeyFile = cfgObj.optString("tlsKeyFile", defaultCfg.tlsKeyFile),
        tlsCaFile = cfgObj.optString("tlsCaFile", defaultCfg.tlsCaFile),
        logLevel = cfgObj.optString("logLevel", defaultCfg.logLevel),
        listenIp = cfgObj.optString("listenIp", defaultCfg.listenIp),
        listenPort = cfgObj.optString("listenPort", defaultCfg.listenPort),
        serverIp = cfgObj.optString("serverIp", defaultCfg.serverIp),
        serverPort = cfgObj.optString("serverPort", defaultCfg.serverPort),
        httpVersion = cfgObj.optString("httpVersion", defaultCfg.httpVersion),
        userAgent = cfgObj.optString("userAgent", defaultCfg.userAgent),
        bufferSize = cfgObj.optString("bufferSize", defaultCfg.bufferSize)
    ).normalized()

    return NipoProfile(
        id = UUID.randomUUID().toString(),
        name = obj.optString("name", "Imported Profile"),
        enabled = false,
        config = cfg
    )
}
