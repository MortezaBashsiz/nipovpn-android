package net.sudoer.nipo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VpnModeTest {
    @get:Rule
    val folder = TemporaryFolder()

    // Keys from hev-socks5-tunnel 2.20.0 conf/main.yml; mapped range from its Android app, sockstun.
    @Test
    fun tunnelConfigPointsAtTheLocalCoreAndAnswersDnsItself() {
        val expected = """
            tunnel:
              mtu: 8500
            socks5:
              port: 1080
              address: 127.0.0.1
              udp: 'udp'
            mapdns:
              address: 198.18.0.2
              port: 53
              network: 240.0.0.0
              netmask: 240.0.0.0
              cache-size: 10000
        """.trimIndent() + "\n"

        assertEquals(expected, tunnelYaml(socksPort = 1080))
    }

    // Proxy is what the app did before the setting existed.
    @Test
    fun aNewInstallStartsInProxyMode() {
        assertEquals(ConnectionMode.PROXY, loadConnectionMode(folder.root, sdk = 34))
    }

    @Test
    fun aSavedVpnModeIsReadBackAsVpn() {
        saveConnectionMode(folder.root, ConnectionMode.VPN)

        assertEquals(ConnectionMode.VPN, loadConnectionMode(folder.root, sdk = 34))
    }

    // A backup can be restored onto a phone older than the tunnel library supports.
    @Test
    fun aPhoneBelowTheMinimumSdkStaysInProxyModeEvenIfVpnWasSaved() {
        saveConnectionMode(folder.root, ConnectionMode.VPN)

        assertEquals(ConnectionMode.PROXY, loadConnectionMode(folder.root, sdk = 28))
    }

    @Test
    fun aFileWithUnknownTextMeansProxyMode() {
        File(folder.root, "connection_mode").writeText("tun")

        assertEquals(ConnectionMode.PROXY, loadConnectionMode(folder.root, sdk = 34))
    }

    // The core has no TUN code; the tunnel library only speaks SOCKS5 to it.
    @Test
    fun vpnModeRunsAnHttpProfileAsSocks5() {
        val yaml = configYaml(NipoConfig(protocol = "http").forMode(ConnectionMode.VPN), "/l", 4)

        assertTrue(yaml.contains("\n  protocol: socks5\n"))
    }

    @Test
    fun proxyModeKeepsTheProfilesProtocol() {
        val yaml = configYaml(NipoConfig(protocol = "http").forMode(ConnectionMode.PROXY), "/l", 4)

        assertTrue(yaml.contains("\n  protocol: http\n"))
    }
}
