package net.sudoer.nipo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigYamlTest {

    @Test
    fun agentAndServerKeysLiveInTheirOwnBlocks() {
        val cfg = NipoConfig(
            token = "0123456789abcdef0123456789abcdef",
            protocol = "socks5",
            fakeUrls = "a.example\nb.example",
            methods = "GET\nPOST",
            endPoints = "api",
            timeout = "10",
            pullTimeout = "50",
            tunnelEnable = true,
            connectionReuse = false,
            tlsEnable = true,
            aesEnable = false,
            tlsVerifyPeer = false,
            tlsCertFile = "/etc/nipovpn/server.crt",
            tlsKeyFile = "/etc/nipovpn/server.key",
            tlsCaFile = "",
            logLevel = "INFO",
            listenIp = "127.0.0.1",
            listenPort = "1080",
            serverIp = "203.0.113.7",
            serverPort = "443",
            httpVersion = "1.1",
            userAgent = "TestAgent",
            bufferSize = "65536",
        )

        // Layout from nipovpn android-v2.1.0 etc/nipovpn/config.yaml.
        val expected = """
            ---
            general:
              aesEnable: false
              tunnelEnable: true
              tlsEnable: true
              timeout: 10
            log:
              logLevel: "INFO"
              logFile: "/data/logs/nipovpn.log"
            server:
              threads: 4
              listenIp: "0.0.0.0"
              listenPort: 80
              bufferSize: 65536
              tlsCertFile: "/etc/nipovpn/server.crt"
              tlsKeyFile: "/etc/nipovpn/server.key"
            agent:
              threads: 4
              listenIp: "127.0.0.1"
              listenPort: 1080
              serverIp: "203.0.113.7"
              serverPort: 443
              httpVersion: "1.1"
              userAgent: "TestAgent"
              token: "0123456789abcdef0123456789abcdef"
              protocol: socks5
              fakeUrls:
                - a.example
                - b.example
              methods:
                - GET
                - POST
              endPoints:
                - api
              pullTimeout: 50
              connectionReuse: false
              tlsVerifyPeer: false
              tlsCaFile: ""
        """.trimIndent()

        assertEquals(expected, configYaml(cfg, logFilePath = "/data/logs/nipovpn.log", threads = 4))
    }

    // A bare "fakeUrls:" is a YAML null, which the 2.1.0 core rejects as "Error in agent.fakeUrls".
    @Test
    fun emptyListsAreWrittenAsEmptySequences() {
        val yaml = configYaml(
            NipoConfig(fakeUrls = "", endPoints = "  \n"),
            logFilePath = "/data/logs/nipovpn.log",
            threads = 4,
        )

        assertTrue(yaml, yaml.contains("\n  fakeUrls: []\n"))
        assertTrue(yaml, yaml.contains("\n  endPoints: []\n"))
    }
}
