package net.sudoer.nipo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import kotlin.system.measureTimeMillis

private const val PING_TARGET_HOST = "google.com"
private const val PING_TARGET_PORT = 443
private const val PROXY_HOST = "127.0.0.1"
private const val PING_TIMEOUT_MS = 5000

suspend fun pingGoogleDelayMs(profile: NipoProfile): Long? = withContext(Dispatchers.IO) {
    val cfg = profile.config.normalized()
    val port = cfg.listenPort.trim().toIntOrNull() ?: return@withContext null

    try {
        when (cfg.protocol.lowercase()) {
            "http" -> pingThroughHttpProxy(port)
            "socks5" -> pingThroughSocks5Proxy(port)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

suspend fun pingGoogleMs(profile: NipoProfile): String {
    return pingGoogleDelayMs(profile)?.let { "${it} ms" } ?: "timeout"
}

private fun pingThroughHttpProxy(port: Int): Long? {
    var success = false

    val elapsed = measureTimeMillis {
        Socket().use { socket ->
            socket.soTimeout = PING_TIMEOUT_MS
            socket.connect(InetSocketAddress(PROXY_HOST, port), PING_TIMEOUT_MS)

            val request = buildString {
                append("CONNECT $PING_TARGET_HOST:$PING_TARGET_PORT HTTP/1.1\r\n")
                append("Host: $PING_TARGET_HOST:$PING_TARGET_PORT\r\n")
                append("Proxy-Connection: close\r\n")
                append("User-Agent: NipoVPN-Android\r\n")
                append("\r\n")
            }

            socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val statusLine = reader.readLine().orEmpty()
            success = statusLine.contains(" 200 ") || statusLine.startsWith("HTTP/1.1 200") || statusLine.startsWith("HTTP/1.0 200")
        }
    }

    return if (success) elapsed else null
}

private fun pingThroughSocks5Proxy(port: Int): Long? {
    val proxy = Proxy(
        Proxy.Type.SOCKS,
        InetSocketAddress(PROXY_HOST, port)
    )

    var success = false

    val elapsed = measureTimeMillis {
        Socket(proxy).use { socket ->
            socket.soTimeout = PING_TIMEOUT_MS
            socket.connect(InetSocketAddress(PING_TARGET_HOST, PING_TARGET_PORT), PING_TIMEOUT_MS)
            success = socket.isConnected
        }
    }

    return if (success) elapsed else null
}
