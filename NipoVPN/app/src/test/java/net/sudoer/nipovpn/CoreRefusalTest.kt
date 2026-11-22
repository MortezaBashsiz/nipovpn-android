package net.sudoer.nipo

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreRefusalTest {

    // Shape of a core log line: "<timestamp> [<mode>] [<LEVEL>] <message>".
    private fun agentLine(message: String) =
        "2026-11-22_10:00:00 [agent] [ERROR] [5f0c2e1a-9b7d-4c3e-8a21-6d4f0b9e7c11] [AgentHandler handle] $message"

    @Test
    fun forbiddenRefusalMeansQuotaUsedUpOrAccountDisabled() {
        assertEquals(Refusal.QUOTA_OR_DISABLED, coreRefusal(agentLine("[Server Refused] [403 Forbidden]")))
    }

    @Test
    fun badRequestRefusalMeansAccountOrProfileRejected() {
        assertEquals(Refusal.REJECTED, coreRefusal(agentLine("[Server Refused] [400 Bad Request]")))
    }

    @Test
    fun tunnelRefusalsAreDetectedToo() {
        val line = "2026-11-22_10:00:00 [agent] [ERROR] [x] [TCPConnection] [Server Refused] [403 Forbidden]"
        assertEquals(Refusal.QUOTA_OR_DISABLED, coreRefusal(line))
    }

    @Test
    fun messagesShownToTheUser() {
        assertEquals(
            "Your account's data allowance has been exhausted or your account has been disabled.",
            Refusal.QUOTA_OR_DISABLED.message,
        )
        assertEquals(
            "The server rejected this connection. Your account may have been disabled, or this profile's token or settings don't match the server.",
            Refusal.REJECTED.message,
        )
    }

    @Test
    fun ordinaryCoreLinesAreNotRefusals() {
        listOf(
            "2026-11-22_10:00:00 [agent] [INFO] [x] [CONNECT] [SRC 127.0.0.1:50122] [DST 142.250.1.1:443]",
            agentLine("[Decryption Failed] : [ bad tag ] "),
            "2026-11-22_10:00:00 [agent] [ERROR] [x] [TCPConnection] send refused or failed",
            "2026-11-22_10:00:00 [agent] [TRACE] [x] [FORWARD] GET /errors/403 HTTP/1.1",
            "===== Starting NipoVPN: Nipo1 Direct =====",
            "",
        ).forEach { line -> assertEquals(line, null, coreRefusal(line)) }
    }
}
