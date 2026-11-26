package net.sudoer.nipo

import android.os.Build
import java.io.File


// PROXY: the core listens on 127.0.0.1 and only apps pointed at it use it.
// VPN: every app's traffic is read from a TUN device and sent to that same listener.
enum class ConnectionMode { PROXY, VPN }

// The prebuilt hev-socks5-tunnel library is built for API 29.
const val VPN_MIN_SDK = 29

const val TUN_MTU = 8500
const val TUN_IPV4 = "198.18.0.1"
const val TUN_IPV6 = "fc00::1"
const val MAPDNS_ADDRESS = "198.18.0.2"

// Config for hev-socks5-tunnel. mapdns answers DNS on the device with a placeholder address and
// sends the real name to the core, so no DNS packet has to cross the tunnel: the core has no UDP.
// It refuses both relay modes at the first reply; "udp" is the standard UDP ASSOCIATE request.
fun tunnelYaml(socksPort: String): String = """
    tunnel:
      mtu: $TUN_MTU
    socks5:
      port: $socksPort
      address: 127.0.0.1
      udp: 'udp'
    mapdns:
      address: $MAPDNS_ADDRESS
      port: 53
      network: 240.0.0.0
      netmask: 240.0.0.0
      cache-size: 10000
""".trimIndent() + "\n"

fun saveConnectionMode(filesDir: File, mode: ConnectionMode) {
    File(filesDir, "connection_mode").writeText(mode.name)
}

fun loadConnectionMode(filesDir: File, sdk: Int = Build.VERSION.SDK_INT): ConnectionMode {
    val file = File(filesDir, "connection_mode")
    if (sdk < VPN_MIN_SDK || !file.exists()) return ConnectionMode.PROXY
    return ConnectionMode.entries.firstOrNull { it.name == file.readText() } ?: ConnectionMode.PROXY
}

fun NipoConfig.forMode(mode: ConnectionMode): NipoConfig =
    if (mode == ConnectionMode.VPN) copy(protocol = "socks5") else this
